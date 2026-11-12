package com.zettais.db2db.web.resource;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import com.zettais.db2db.core.assessment.AssessmentEngine;
import com.zettais.db2db.core.assessment.AssessmentReport;
import com.zettais.db2db.core.config.Db2DbConfig;
import com.zettais.db2db.core.ddl.PostgreSqlDdlGenerator;
import com.zettais.db2db.core.extractor.JdbcMetadataExtractor;
import com.zettais.db2db.core.extractor.MetadataExtractor;
import com.zettais.db2db.core.extractor.OracleMetadataExtractor;
import com.zettais.db2db.core.model.DatabaseCatalog;
import com.zettais.db2db.core.model.Table;
import com.zettais.db2db.core.transpiler.PlSqlTranspiler;
import com.zettais.db2db.web.dto.*;

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

    @GET
    @Path("/assessment/sample")
    public Response getSampleAssessment() {
        var report = assessmentEngine.createSampleReport();
        return Response.ok(report).build();
    }

    @GET
    @Path("/assessment/sample/html")
    @Produces(MediaType.TEXT_HTML)
    public Response getSampleAssessmentHtml() {
        var report = assessmentEngine.createSampleReport();
        var generator = new com.zettais.db2db.core.assessment.AssessmentReportGenerator();
        return Response.ok(generator.toHtml(report)).build();
    }

    @GET
    @Path("/assessment/live/html")
    @Produces(MediaType.TEXT_HTML)
    public Response getLiveAssessmentHtml(
            @QueryParam("dsn") String dsn,
            @QueryParam("user") String user,
            @QueryParam("password") String password,
            @QueryParam("schema") String schema
    ) {
        String effectiveDsn = dsn != null && !dsn.isBlank() ? dsn : "jdbc:oracle:thin:@localhost:1521/XEPDB1";
        String effectiveUser = user != null && !user.isBlank() ? user : "OSADMIN";
        String effectivePassword = password != null ? password : "";
        String effectiveSchema = schema != null && !schema.isBlank() ? schema : effectiveUser.toUpperCase(java.util.Locale.ROOT);

        var config = new Db2DbConfig();
        config.setSourceDsn(effectiveDsn);
        config.setSourceUser(effectiveUser);
        config.setSourcePassword(effectivePassword);
        config.setSourceSchema(effectiveSchema);

        try (var conn = DriverManager.getConnection(config.getSourceDsn(), config.getSourceUser(), config.getSourcePassword())) {
            MetadataExtractor extractor = resolveExtractor(config);
            var catalog = extractor.extractCatalog(conn, config.getSourceSchema());
            var report = assessmentEngine.assess(catalog);
            var generator = new com.zettais.db2db.core.assessment.AssessmentReportGenerator();
            return Response.ok(generator.toHtml(report)).build();
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.toString();
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("""
                            <!DOCTYPE html>
                            <html>
                            <head><title>DB2DB - Connection Error</title></head>
                            <body style="font-family: -apple-system, BlinkMacSystemFont, sans-serif; margin: 3rem; background: #0f172a; color: #f8fafc;">
                                <div style="max-width: 800px; margin: 0 auto; background: #1e293b; border: 1px solid #dc2626; border-radius: 8px; padding: 2rem;">
                                    <h2 style="color: #ef4444; margin-top: 0;">Database Assessment Connection Failed</h2>
                                    <p style="margin-bottom: 0.5rem;"><strong>Attempted Connection:</strong> %s</p>
                                    <p style="margin-bottom: 1rem;"><strong>User:</strong> %s &bull; <strong>Schema:</strong> %s</p>
                                    <div style="background: #450a0a; border: 1px solid #7f1d1d; padding: 1rem; border-radius: 6px; color: #fecaca; font-family: monospace; font-size: 0.9rem;">
                                        %s
                                    </div>
                                    <p style="margin-top: 1.5rem; font-size: 0.85rem; color: #94a3b8;">
                                        Please verify your database host, port, credentials, and network listener.
                                    </p>
                                </div>
                            </body>
                            </html>
                            """.formatted(effectiveDsn, effectiveUser, effectiveSchema, errorMsg))
                    .build();
        }
    }

    @POST
    @Path("/assessment/live/html")
    @Produces(MediaType.TEXT_HTML)
    public Response postLiveAssessmentHtml(AssessmentRequest request) {
        if (request == null || request.sourceDsn() == null || request.sourceDsn().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("<h3>Error: Source JDBC DSN is required.</h3>")
                    .build();
        }
        return getLiveAssessmentHtml(request.sourceDsn(), request.sourceUser(), request.sourcePassword(), request.schema());
    }

    @POST
    @Path("/assessment/html")
    @Produces(MediaType.TEXT_HTML)
    public Response runAssessmentHtml(AssessmentRequest request) {
        return postLiveAssessmentHtml(request);
    }

    @POST
    @Path("/assessment")
    public Response runAssessment(AssessmentRequest request) {
        if (request == null || request.sourceDsn() == null || request.sourceDsn().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Source JDBC DSN is required. Please configure it in the Database Connections tab."))
                    .build();
        }

        var config = new Db2DbConfig();
        config.setSourceDsn(request.sourceDsn());
        config.setSourceUser(request.sourceUser());
        config.setSourcePassword(request.sourcePassword());
        config.setSourceSchema(request.schema() != null && !request.schema().isBlank() ? request.schema() : "HR");

        try (var conn = DriverManager.getConnection(config.getSourceDsn(), config.getSourceUser(), config.getSourcePassword())) {
            MetadataExtractor extractor = resolveExtractor(config);

            var catalog = extractor.extractCatalog(conn, config.getSourceSchema());
            var report = assessmentEngine.assess(catalog);
            return Response.ok(report).build();
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.toString();
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", errorMsg))
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
                    -- Generated by DB2DB by ZettaIS (Enterprise Migration Platform)
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
            return new com.zettais.db2db.core.extractor.MySqlMetadataExtractor(config);
        } else if (dsn.contains("sqlserver") || dsn.contains("jtds") || dsn.contains("mssql")) {
            return new com.zettais.db2db.core.extractor.MssqlMetadataExtractor(config);
        }
        return new JdbcMetadataExtractor(config);
    }
}
