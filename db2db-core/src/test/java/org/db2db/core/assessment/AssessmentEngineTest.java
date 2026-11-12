package org.db2db.core.assessment;

import org.db2db.core.model.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AssessmentEngineTest {

    @Test
    void testAssessmentCalculation() throws IOException {
        DatabaseCatalog catalog = new DatabaseCatalog("HR");

        Table t1 = new Table("HR", "EMPLOYEES");
        t1.setColumns(List.of(new Column("ID", "NUMBER", false), new Column("NAME", "VARCHAR2", false)));
        t1.setConstraints(List.of(new Constraint("PK_EMP", Constraint.Type.PRIMARY_KEY)));
        t1.setIndexes(List.of(new Index("IDX_NAME", "EMPLOYEES", false)));
        catalog.getTables().add(t1);

        catalog.getSequences().add(new Sequence("HR", "EMP_SEQ"));
        catalog.getViews().add(new View("HR", "EMP_VIEW", "SELECT * FROM employees", false));

        PlSqlUnit proc = new PlSqlUnit("HR", "PROCESS_PAYROLL", PlSqlUnit.UnitType.PROCEDURE, "BEGIN\nNULL;\nEND;");
        catalog.getPlSqlUnits().add(proc);

        AssessmentEngine engine = new AssessmentEngine();
        AssessmentReport report = engine.assess(catalog);

        assertThat(report.getSchemaName()).isEqualTo("HR");
        assertThat(report.getTotalTables()).isEqualTo(1);
        assertThat(report.getTotalColumns()).isEqualTo(2);
        assertThat(report.getTotalConstraints()).isEqualTo(1);
        assertThat(report.getTotalSequences()).isEqualTo(1);
        assertThat(report.getTotalViews()).isEqualTo(1);
        assertThat(report.getTotalProcedures()).isEqualTo(1);
        assertThat(report.getTotalMigrationCostUnits()).isGreaterThan(0.0);
        assertThat(report.getDifficultyLevel()).isEqualTo("LOW");

        AssessmentReportGenerator generator = new AssessmentReportGenerator();
        String json = generator.toJson(report);
        assertThat(json).contains("\"schemaName\" : \"HR\"");

        String html = generator.toHtml(report);
        assertThat(html).contains("db2db Migration Assessment");
        assertThat(html).contains("badge-LOW");
    }

    @Test
    void testShowcaseReportGeneration() throws IOException {
        AssessmentEngine engine = new AssessmentEngine();
        AssessmentReport report = engine.createSampleReport();

        assertThat(report.getSchemaName()).isEqualTo("FINANCE_CORE");
        assertThat(report.getDatabaseType()).isEqualTo("ORACLE");
        assertThat(report.getTotalTables()).isEqualTo(84);
        assertThat(report.getTotalRows()).isEqualTo(4_852_400L);
        assertThat(report.getTotalPlSqlLines()).isEqualTo(8450);
        assertThat(report.getDifficultyLevel()).isEqualTo("HIGH");
        assertThat(report.getTotalMigrationCostUnits()).isGreaterThan(500.0);
        assertThat(report.getEstimatedHumanDays()).isGreaterThan(10.0);

        // Check records
        assertThat(report.getTopTablesByRows()).isNotEmpty();
        assertThat(report.getTopTablesByRows().getFirst().tableName()).isEqualTo("GL_TRANSACTIONS_PART");
        assertThat(report.getTopTablesBySize()).isNotEmpty();
        assertThat(report.getPlSqlDetail()).isNotNull();
        assertThat(report.getPlSqlDetail().autonomousTransactions()).isEqualTo(3);
        assertThat(report.getSpatialDetail()).isNotNull();
        assertThat(report.getSpatialDetail().sdoGeometryColumns()).isEqualTo(4);
        assertThat(report.getRecommendations()).hasSize(5);
        assertThat(report.getReservedKeywordWarnings()).isNotEmpty();

        AssessmentReportGenerator generator = new AssessmentReportGenerator();
        String json = generator.toJson(report);
        assertThat(json)
                .contains("\"schemaName\" : \"FINANCE_CORE\"")
                .contains("\"GL_TRANSACTIONS_PART\"")
                .contains("\"autonomousTransactions\" : 3")
                .contains("\"postGisExtensionRecommendation\"");

        String html = generator.toHtml(report);
        assertThat(html)
                .contains("db2db Migration Assessment")
                .contains("FINANCE_CORE")
                .contains("badge-HIGH")
                .contains("GL_TRANSACTIONS_PART")
                .contains("Autonomous Tx")
                .contains("PostGIS Geographic Conversion")
                .contains("PostgreSQL Reserved Keyword Collisions")
                .contains("Strategic Migration Recommendations");
    }
}
