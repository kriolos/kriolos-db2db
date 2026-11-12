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
        assertThat(html).contains("<h1>db2db - Migration Assessment Report</h1>");
        assertThat(html).contains("badge-LOW");
    }
}
