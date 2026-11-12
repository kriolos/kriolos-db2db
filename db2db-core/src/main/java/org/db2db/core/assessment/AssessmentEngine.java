package org.db2db.core.assessment;

import org.db2db.core.model.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Calculates migration assessment metrics, difficulty levels, and human-days cost
 * for DB2DB by ZettaIS.
 */
public class AssessmentEngine {

    // DB2DB Migration Unit constants
    private static final double COST_TABLE = 1.0;
    private static final double COST_COLUMN = 0.1;
    private static final double COST_CONSTRAINT = 0.5;
    private static final double COST_INDEX = 0.5;
    private static final double COST_SEQUENCE = 0.2;
    private static final double COST_VIEW = 2.0;
    private static final double COST_TRIGGER = 4.0;
    private static final double COST_PROCEDURE = 5.0;
    private static final double COST_FUNCTION = 5.0;
    private static final double COST_PACKAGE = 15.0;
    private static final double COST_PER_PLSQL_LINE = 0.05;

    // 1 Human-Day = ~40 units (equivalent to ~8 hours with 5 units/hr)
    private static final double UNITS_PER_HUMAN_DAY = 40.0;

    public AssessmentReport assess(DatabaseCatalog catalog) {
        var report = new AssessmentReport();
        report.setSchemaName(catalog.getSchemaName());
        report.setDatabaseType(catalog.getDialect() != null ? catalog.getDialect().name() : "GENERIC_JDBC");

        int totalTables = catalog.getTables().size();
        int totalCols = 0;
        int totalConstraints = 0;
        int totalIndexes = 0;
        for (Table t : catalog.getTables()) {
            totalCols += t.getColumns().size();
            totalConstraints += t.getConstraints().size();
            totalIndexes += t.getIndexes().size();
        }

        int totalSeqs = catalog.getSequences().size();
        int totalViews = catalog.getViews().size();
        int totalTriggers = catalog.getTriggers().size();

        int totalFuncs = 0;
        int totalProcs = 0;
        int totalPkgs = 0;
        int totalPlSqlLines = 0;

        for (PlSqlUnit unit : catalog.getPlSqlUnits()) {
            totalPlSqlLines += unit.getLineCount();
            switch (unit.getType()) {
                case FUNCTION -> totalFuncs++;
                case PROCEDURE -> totalProcs++;
                case PACKAGE_BODY, PACKAGE_SPEC -> totalPkgs++;
                default -> {}
            }
        }

        report.setTotalTables(totalTables);
        report.setTotalColumns(totalCols);
        report.setTotalConstraints(totalConstraints);
        report.setTotalIndexes(totalIndexes);
        report.setTotalSequences(totalSeqs);
        report.setTotalViews(totalViews);
        report.setTotalTriggers(totalTriggers);
        report.setTotalFunctions(totalFuncs);
        report.setTotalProcedures(totalProcs);
        report.setTotalPackages(totalPkgs);
        report.setTotalPlSqlLines(totalPlSqlLines);
        report.setTotalInvalidObjects(catalog.getInvalidObjects().size());

        // Calculate Cost Units
        double tableCost = (totalTables * COST_TABLE) + (totalCols * COST_COLUMN);
        double constraintCost = totalConstraints * COST_CONSTRAINT;
        double indexCost = totalIndexes * COST_INDEX;
        double seqCost = totalSeqs * COST_SEQUENCE;
        double viewCost = totalViews * COST_VIEW;
        double triggerCost = totalTriggers * COST_TRIGGER;
        double plsqlCost = (totalFuncs * COST_FUNCTION) + (totalProcs * COST_PROCEDURE) +
                           (totalPkgs * COST_PACKAGE) + (totalPlSqlLines * COST_PER_PLSQL_LINE);

        double totalUnits = tableCost + constraintCost + indexCost + seqCost + viewCost + triggerCost + plsqlCost;
        report.setTotalMigrationCostUnits(Math.round(totalUnits * 100.0) / 100.0);

        double humanDays = totalUnits / UNITS_PER_HUMAN_DAY;
        report.setEstimatedHumanDays(Math.round(humanDays * 10.0) / 10.0);

        // Difficulty classification
        if (totalUnits < 50) {
            report.setDifficultyLevel("LOW");
        } else if (totalUnits < 200) {
            report.setDifficultyLevel("MEDIUM");
        } else if (totalUnits < 800) {
            report.setDifficultyLevel("HIGH");
        } else {
            report.setDifficultyLevel("CRITICAL");
        }

        report.getObjectCountByType().put("TABLE", totalTables);
        report.getObjectCountByType().put("COLUMN", totalCols);
        report.getObjectCountByType().put("CONSTRAINT", totalConstraints);
        report.getObjectCountByType().put("INDEX", totalIndexes);
        report.getObjectCountByType().put("SEQUENCE", totalSeqs);
        report.getObjectCountByType().put("VIEW", totalViews);
        report.getObjectCountByType().put("TRIGGER", totalTriggers);
        report.getObjectCountByType().put("PLSQL_UNIT", totalFuncs + totalProcs + totalPkgs);

        report.getCostUnitsByType().put("TABLES", tableCost);
        report.getCostUnitsByType().put("CONSTRAINTS", constraintCost);
        report.getCostUnitsByType().put("INDEXES", indexCost);
        report.getCostUnitsByType().put("VIEWS", viewCost);
        report.getCostUnitsByType().put("TRIGGERS", triggerCost);
        report.getCostUnitsByType().put("PLSQL", plsqlCost);

        return report;
    }

    /**
     * Creates a comprehensive showcase assessment report modeled after an enterprise
     * Oracle ERP/Financial core database to present all reporting features.
     */
    public AssessmentReport createSampleReport() {
        var report = new AssessmentReport();
        report.setSchemaName("FINANCE_CORE");
        report.setDatabaseType("ORACLE");
        report.setDatabaseVersion("Oracle Database 19c Enterprise Edition Release 19.3.0.0.0 - 64bit Production");
        report.setDatabaseSize("184.6 GB");
        report.setTotalRows(4_852_400L);

        report.setTotalTables(84);
        report.setTotalColumns(1142);
        report.setTotalConstraints(312);
        report.setTotalIndexes(198);
        report.setTotalSequences(26);
        report.setTotalViews(42);
        report.setTotalTriggers(18);
        report.setTotalFunctions(34);
        report.setTotalProcedures(28);
        report.setTotalPackages(12);
        report.setTotalPlSqlLines(8450);
        report.setTotalInvalidObjects(2);

        // Object count map
        report.getObjectCountByType().put("TABLE", 84);
        report.getObjectCountByType().put("TABLE PARTITION", 36);
        report.getObjectCountByType().put("COLUMN", 1142);
        report.getObjectCountByType().put("CONSTRAINT", 312);
        report.getObjectCountByType().put("INDEX", 198);
        report.getObjectCountByType().put("SEQUENCE", 26);
        report.getObjectCountByType().put("VIEW", 36);
        report.getObjectCountByType().put("MATERIALIZED VIEW", 6);
        report.getObjectCountByType().put("TRIGGER", 18);
        report.getObjectCountByType().put("PACKAGE", 12);
        report.getObjectCountByType().put("PROCEDURE", 28);
        report.getObjectCountByType().put("FUNCTION", 34);
        report.getObjectCountByType().put("TYPE", 8);
        report.getObjectCountByType().put("SYNONYM", 14);
        report.getObjectCountByType().put("DATABASE LINK", 3);

        // Cost Units Calculation
        double tableCost = 198.2;
        double constraintCost = 156.0;
        double indexCost = 112.5;
        double viewCost = 84.0;
        double triggerCost = 72.0;
        double plsqlCost = 285.5;
        double spatialCost = 18.0;

        double totalUnits = tableCost + constraintCost + indexCost + viewCost + triggerCost + plsqlCost + spatialCost;
        report.setTotalMigrationCostUnits(Math.round(totalUnits * 10.0) / 10.0);
        report.setEstimatedHumanDays(Math.round((totalUnits / UNITS_PER_HUMAN_DAY) * 10.0) / 10.0);
        report.setDifficultyLevel("HIGH");

        report.getCostUnitsByType().put("TABLES & COLUMNS", tableCost);
        report.getCostUnitsByType().put("CONSTRAINTS & KEYS", constraintCost);
        report.getCostUnitsByType().put("INDEXES & BITMAPS", indexCost);
        report.getCostUnitsByType().put("VIEWS & MVIEWS", viewCost);
        report.getCostUnitsByType().put("TRIGGERS", triggerCost);
        report.getCostUnitsByType().put("PL/SQL PACKAGES & CODE", plsqlCost);
        report.getCostUnitsByType().put("SPATIAL & POSTGIS", spatialCost);

        // Detailed Object Breakdown
        report.getObjectDetails().put("TABLE", new AssessmentReport.ObjectAssessmentDetail(
                "TABLE", 84, 0, tableCost,
                "84 standard tables including 36 range/list partition elements and 4 global temporary tables.",
                List.of(
                        "Range partitioning detected on GL_TRANSACTIONS by TRAN_DATE (converted to PostgreSQL native declarative partitioning)",
                        "List partitioning detected on INVOICE_ARCHIVE by REGION_CODE",
                        "4 Global Temporary Tables (PRESERVE ROWS) converted to PostgreSQL TEMPORARY tables or session schemas"
                )
        ));

        report.getObjectDetails().put("INDEX", new AssessmentReport.ObjectAssessmentDetail(
                "INDEX", 198, 0, indexCost,
                "152 standard B-tree indexes, 24 Bitmap indexes, 16 Function-based indexes, and 6 Reverse-key indexes.",
                List.of(
                        "24 Bitmap indexes detected: Oracle bitmap indexes will be transpiled to PostgreSQL btree_gin or bloom indexes",
                        "16 Function-based indexes (e.g. UPPER(EMAIL), TRUNC(CREATED_DATE)): fully supported in PostgreSQL with explicit parens",
                        "6 Reverse-key indexes: mapped to standard B-Tree or pg_trgm expression indexes"
                )
        ));

        report.getObjectDetails().put("VIEW", new AssessmentReport.ObjectAssessmentDetail(
                "VIEW", 42, 1, viewCost,
                "36 standard SQL views, 6 Materialized Views (2 configured with REFRESH ON COMMIT).",
                List.of(
                        "6 Materialized Views exported as PostgreSQL MATERIALIZED VIEW with REFRESH MATERIALIZED VIEW CONCURRENTLY",
                        "2 Materialized Views have REFRESH ON COMMIT: Oracle commit-refresh must be emulated using AFTER INSERT/UPDATE triggers",
                        "1 View has compilation error in source catalog (VIEW_CORP_REV_2020: ORA-00942 table not found)"
                )
        ));

        report.getObjectDetails().put("TRIGGER", new AssessmentReport.ObjectAssessmentDetail(
                "TRIGGER", 18, 0, triggerCost,
                "12 BEFORE INSERT/UPDATE row triggers, 4 INSTEAD OF view triggers, and 2 COMPOUND triggers.",
                List.of(
                        "In PostgreSQL, triggers require a separate trigger function returning TRIGGER before CREATE TRIGGER",
                        "2 Compound triggers: Oracle compound triggers split into BEFORE ROW and AFTER STATEMENT trigger pairs",
                        "All sequences referenced inside :NEW.ID := SEQ.NEXTVAL converted to IDENTITY or DEFAULT nextval('seq')"
                )
        ));

        report.getObjectDetails().put("PLSQL", new AssessmentReport.ObjectAssessmentDetail(
                "PLSQL", 74, 1, plsqlCost,
                "12 Packages (spec + body), 28 Stored Procedures, 34 User-Defined Functions totaling 8,450 lines of code.",
                List.of(
                        "Oracle Packages transpiled into PostgreSQL schemas (e.g., pkg_gl_ledger.func() -> finance_core_pkg_gl_ledger.func())",
                        "3 PRAGMA AUTONOMOUS_TRANSACTION occurrences detected: must be rewritten using dblink or pg_background",
                        "8 Dynamic SQL occurrences (EXECUTE IMMEDIATE) requiring parameter verification",
                        "1 Invalid Package: PKG_LEGACY_IMPORT has unresolved dependencies"
                )
        ));

        report.getObjectDetails().put("SPATIAL", new AssessmentReport.ObjectAssessmentDetail(
                "SPATIAL", 4, 0, spatialCost,
                "4 MDSYS.SDO_GEOMETRY columns detected across BRANCH_OFFICES and LOGISTICS_TERRITORIES.",
                List.of(
                        "Spatial columns converted to PostGIS geometry(Point, 4326) and geometry(Polygon, 4326)",
                        "Spatial indexes (MDSYS.SPATIAL_INDEX) converted to PostgreSQL GiST indexes"
                )
        ));

        // Top tables by row count
        report.setTopTablesByRows(List.of(
                new AssessmentReport.TableMetric("GL_TRANSACTIONS_PART", 2_450_000L, "78.4 GB", 38, 12),
                new AssessmentReport.TableMetric("AUDIT_LOG_EVENTS", 1_120_000L, "32.1 GB", 16, 4),
                new AssessmentReport.TableMetric("INVOICE_LINE_ITEMS", 640_000L, "24.6 GB", 28, 8),
                new AssessmentReport.TableMetric("CUSTOMER_PAYMENTS", 412_000L, "16.8 GB", 22, 6),
                new AssessmentReport.TableMetric("PRODUCT_CATALOG", 125_000L, "4.2 GB", 34, 5)
        ));

        // Top tables by size
        report.setTopTablesBySize(List.of(
                new AssessmentReport.TableMetric("GL_TRANSACTIONS_PART", 2_450_000L, "78.4 GB", 38, 12),
                new AssessmentReport.TableMetric("AUDIT_LOG_EVENTS", 1_120_000L, "32.1 GB", 16, 4),
                new AssessmentReport.TableMetric("ATTACHMENT_BLOBS", 42_000L, "28.5 GB", 8, 2),
                new AssessmentReport.TableMetric("INVOICE_LINE_ITEMS", 640_000L, "24.6 GB", 28, 8),
                new AssessmentReport.TableMetric("CUSTOMER_PAYMENTS", 412_000L, "16.8 GB", 22, 6)
        ));

        // PL/SQL granular detail
        report.setPlSqlDetail(new AssessmentReport.PlSqlAssessmentDetail(
                12, 28, 34, 18, 8450, 3, 8, 14, plsqlCost
        ));

        // Spatial granular detail
        report.setSpatialDetail(new AssessmentReport.SpatialAssessmentDetail(
                4, 2, 2, 0,
                "CREATE EXTENSION IF NOT EXISTS postgis;"
        ));

        // Reserved keywords
        report.setReservedKeywordWarnings(List.of(
                "Table 'USER_ROLES' contains column 'USER' (PostgreSQL reserved keyword - double-quoted)",
                "Table 'PURCHASE_ORDERS' contains column 'ORDER' (PostgreSQL reserved keyword - double-quoted)",
                "Table 'EXPENSE_CATEGORIES' contains column 'OFFSET' (PostgreSQL reserved keyword - double-quoted)",
                "Table 'ORGANIZATION_UNITS' contains column 'GROUP' (PostgreSQL reserved keyword - double-quoted)"
        ));

        // Recommendations
        report.setRecommendations(List.of(
                new AssessmentReport.MigrationRecommendation(
                        "ARCHITECTURE",
                        "Emulate Oracle Packages with PostgreSQL Schemas",
                        "PostgreSQL lacks native PACKAGE encapsulation. db2db automates decomposing package functions into dedicated schema namespaces while preserving call semantics.",
                        "MEDIUM"
                ),
                new AssessmentReport.MigrationRecommendation(
                        "CONCURRENCY",
                        "Rewrite Autonomous Transactions with pg_background",
                        "Found 3 instances of PRAGMA AUTONOMOUS_TRANSACTION in PKG_AUDIT_TRAIL. PostgreSQL transactions are strictly bounded; rewrite via pg_background or dblink independent connections.",
                        "HIGH"
                ),
                new AssessmentReport.MigrationRecommendation(
                        "STORAGE",
                        "Migrate Bitmap Indexes to btree_gin or Bloom Indexes",
                        "Found 24 Bitmap indexes. PostgreSQL does not have read-mostly bitmap indexes; enable btree_gin extension or BRIN indexes for high-cardinality multi-column filtering.",
                        "MEDIUM"
                ),
                new AssessmentReport.MigrationRecommendation(
                        "SPATIAL",
                        "Enable PostGIS Extension Before Schema DDL",
                        "Found 4 SDO_GEOMETRY columns. Run 'CREATE EXTENSION postgis;' on target database before applying tables containing spatial geometries.",
                        "LOW"
                ),
                new AssessmentReport.MigrationRecommendation(
                        "DATA_STREAMING",
                        "Use Parallel Copy Streaming with Virtual Threads",
                        "For large tables (>1M rows, e.g. GL_TRANSACTIONS_PART), utilize db2db high-throughput streaming engine with concurrency level 16+.",
                        "LOW"
                )
        ));

        return report;
    }
}
