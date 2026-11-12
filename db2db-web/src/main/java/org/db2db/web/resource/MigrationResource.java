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
        var status = Map.of(
                "status", "UP",
                "engine", "db2db Enterprise Migration Platform",
                "version", "1.0.0",
                "jdk", System.getProperty("java.version"),
                "virtualThreads", "supported"
        );
        return Response.ok(status).build();
    }

    @POST
    @Path("/transpile")
    public Response transpile(TranspileRequest request) {
        if (request == null || request.sql() == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing SQL content").build();
        }

        var start = System.currentTimeMillis();
        var result = transpiler.transpile(request.sql());
        var elapsed = System.currentTimeMillis() - start;

        return Response.ok(new TranspileResponse(request.sql(), result, elapsed)).build();
    }

    @POST
    @Path("/connection/test")
    public Response testConnection(ConnectionTestRequest request) {
        boolean sourceOk = false;
        String sourceMsg = "Not tested";
        boolean targetOk = false;
        String targetMsg = "Not tested";

        // Source test
        if (request.sourceDsn() != null && !request.sourceDsn().isBlank()) {
            try (Connection conn = DriverManager.getConnection(request.sourceDsn(), request.sourceUser(), request.sourcePassword())) {
                sourceOk = true;
                sourceMsg = "Connected: " + conn.getMetaData().getDatabaseProductName() + " " + conn.getMetaData().getDatabaseProductVersion();
            } catch (Exception e) {
                sourceOk = false;
                sourceMsg = "Failed: " + e.getMessage();
            }
        }

        // Target test
        if (request.targetDsn() != null && !request.targetDsn().isBlank()) {
            try (Connection conn = DriverManager.getConnection(request.targetDsn(), request.targetUser(), request.targetPassword())) {
                targetOk = true;
                targetMsg = "Connected: " + conn.getMetaData().getDatabaseProductName() + " " + conn.getMetaData().getDatabaseProductVersion();
            } catch (Exception e) {
                targetOk = false;
                targetMsg = "Failed: " + e.getMessage();
            }
        }

        return Response.ok(new ConnectionTestResponse(sourceOk, sourceMsg, targetOk, targetMsg)).build();
    }

    @POST
    @Path("/assessment")
    public Response runAssessment(AssessmentRequest request) {
        if (request == null || request.sourceDsn() == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing source connection details").build();
        }

        var config = new Db2DbConfig();
        config.setSourceDsn(request.sourceDsn());
        config.setSourceUser(request.sourceUser());
        config.setSourcePassword(request.sourcePassword());
        config.setSourceSchema(request.schema());

        try (var conn = DriverManager.getConnection(config.getSourceDsn(), config.getSourceUser(), config.getSourcePassword())) {
            MetadataExtractor extractor = resolveExtractor(config);

            var catalog = extractor.extractCatalog(conn, config.getSourceSchema());
            var report = assessmentEngine.assess(catalog);
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
        var config = new Db2DbConfig();
        config.setSourceDsn(request.sourceDsn());
        config.setSourceUser(request.sourceUser());
        config.setSourcePassword(request.sourcePassword());
        config.setSourceSchema(request.schema());

        try (var conn = DriverManager.getConnection(config.getSourceDsn(), config.getSourceUser(), config.getSourcePassword())) {
            MetadataExtractor extractor = resolveExtractor(config);

            var catalog = extractor.extractCatalog(conn, config.getSourceSchema());
            var generator = new PostgreSqlDdlGenerator(config);

            var ddl = new StringBuilder("""
                    -- ==============================================================
                    -- Generated by db2db Engine (Java modern implementation of ora2pg)
                    -- Target Schema: %s
                    -- ==============================================================
                    
                    """.formatted(config.getSourceSchema()));

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

    private MetadataExtractor resolveExtractor(Db2DbConfig config) {
        String dsn = config.getSourceDsn().toLowerCase();
        if (dsn.contains("oracle")) {
            return new OracleMetadataExtractor(config);
        } else if (dsn.contains("mysql") || dsn.contains("mariadb")) {
            return new org.db2db.core.extractor.MySqlMetadataExtractor(config);
        }
        return new JdbcMetadataExtractor(config);
    }
}
