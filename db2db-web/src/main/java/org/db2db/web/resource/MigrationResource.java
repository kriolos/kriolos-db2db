package org.db2db.web.resource;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.db2db.core.assessment.AssessmentEngine;
import org.db2db.core.assessment.AssessmentReport;
import org.db2db.core.config.Db2DbConfig;
import org.db2db.core.ddl.PostgreSqlDdlGenerator;
import org.db2db.core.extractor.JdbcMetadataExtractor;
import org.db2db.core.extractor.MetadataExtractor;
import org.db2db.core.extractor.OracleMetadataExtractor;
import org.db2db.core.model.DatabaseCatalog;
import org.db2db.core.model.Table;
import org.db2db.core.transpiler.PlSqlTranspiler;
import org.db2db.web.dto.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Map;

@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MigrationResource {

    private final PlSqlTranspiler transpiler = new PlSqlTranspiler();
    private final AssessmentEngine assessmentEngine = new AssessmentEngine();

    @GET
    @Path("/health")
    public Response health() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "UP");
        status.put("engine", "db2db Enterprise Migration Platform");
        status.put("version", "1.0.0");
        status.put("jdk", System.getProperty("java.version"));
        return Response.ok(status).build();
    }

    @POST
    @Path("/transpile")
    public Response transpile(TranspileRequest request) {
        if (request == null || request.getSql() == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing SQL content").build();
        }

        long start = System.currentTimeMillis();
        String result = transpiler.transpile(request.getSql());
        long elapsed = System.currentTimeMillis() - start;

        return Response.ok(new TranspileResponse(request.getSql(), result, elapsed)).build();
    }

    @POST
    @Path("/connection/test")
    public Response testConnection(ConnectionTestRequest request) {
        ConnectionTestResponse resp = new ConnectionTestResponse();

        // Source test
        if (request.getSourceDsn() != null && !request.getSourceDsn().isBlank()) {
            try (Connection conn = DriverManager.getConnection(request.getSourceDsn(), request.getSourceUser(), request.getSourcePassword())) {
                resp.setSourceOk(true);
                resp.setSourceMessage("Connected: " + conn.getMetaData().getDatabaseProductName() + " " + conn.getMetaData().getDatabaseProductVersion());
            } catch (Exception e) {
                resp.setSourceOk(false);
                resp.setSourceMessage("Failed: " + e.getMessage());
            }
        }

        // Target test
        if (request.getTargetDsn() != null && !request.getTargetDsn().isBlank()) {
            try (Connection conn = DriverManager.getConnection(request.getTargetDsn(), request.getTargetUser(), request.getTargetPassword())) {
                resp.setTargetOk(true);
                resp.setTargetMessage("Connected: " + conn.getMetaData().getDatabaseProductName() + " " + conn.getMetaData().getDatabaseProductVersion());
            } catch (Exception e) {
                resp.setTargetOk(false);
                resp.setTargetMessage("Failed: " + e.getMessage());
            }
        }

        return Response.ok(resp).build();
    }

    @POST
    @Path("/assessment")
    public Response runAssessment(AssessmentRequest request) {
        if (request == null || request.getSourceDsn() == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing source connection details").build();
        }

        Db2DbConfig config = new Db2DbConfig();
        config.setSourceDsn(request.getSourceDsn());
        config.setSourceUser(request.getSourceUser());
        config.setSourcePassword(request.getSourcePassword());
        config.setSourceSchema(request.getSchema());

        try (Connection conn = DriverManager.getConnection(config.getSourceDsn(), config.getSourceUser(), config.getSourcePassword())) {
            MetadataExtractor extractor = config.getSourceDsn().toLowerCase().contains("oracle")
                    ? new OracleMetadataExtractor(config)
                    : new JdbcMetadataExtractor(config);

            DatabaseCatalog catalog = extractor.extractCatalog(conn, config.getSourceSchema());
            AssessmentReport report = assessmentEngine.assess(catalog);
            return Response.ok(report).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/export/schema")
    @Produces(MediaType.TEXT_PLAIN)
    public Response exportSchema(AssessmentRequest request) {
        Db2DbConfig config = new Db2DbConfig();
        config.setSourceDsn(request.getSourceDsn());
        config.setSourceUser(request.getSourceUser());
        config.setSourcePassword(request.getSourcePassword());
        config.setSourceSchema(request.getSchema());

        try (Connection conn = DriverManager.getConnection(config.getSourceDsn(), config.getSourceUser(), config.getSourcePassword())) {
            MetadataExtractor extractor = config.getSourceDsn().toLowerCase().contains("oracle")
                    ? new OracleMetadataExtractor(config)
                    : new JdbcMetadataExtractor(config);

            DatabaseCatalog catalog = extractor.extractCatalog(conn, config.getSourceSchema());
            PostgreSqlDdlGenerator generator = new PostgreSqlDdlGenerator(config);

            StringBuilder ddl = new StringBuilder("-- Generated by db2db Engine for Schema: ")
                    .append(config.getSourceSchema()).append("\n\n");

            for (Table t : catalog.getTables()) {
                ddl.append(generator.generateTableDdl(t)).append("\n");
                ddl.append(generator.generateIndexesDdl(t)).append("\n");
                ddl.append(generator.generateDeferredForeignKeys(t)).append("\n");
            }
            for (var s : catalog.getSequences()) {
                ddl.append(generator.generateSequenceDdl(s)).append("\n");
            }
            for (var v : catalog.getViews()) {
                ddl.append(generator.generateViewDdl(v)).append("\n");
            }

            return Response.ok(ddl.toString()).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error generating DDL: " + e.getMessage())
                    .build();
        }
    }
}
