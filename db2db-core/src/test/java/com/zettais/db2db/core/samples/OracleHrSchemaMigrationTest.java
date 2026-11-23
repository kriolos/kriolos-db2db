package com.zettais.db2db.core.samples;

import com.zettais.db2db.core.assessment.AssessmentEngine;
import com.zettais.db2db.core.config.Db2DbConfig;
import com.zettais.db2db.core.ddl.MariaDbDdlGenerator;
import com.zettais.db2db.core.ddl.PostgreSqlDdlGenerator;
import com.zettais.db2db.core.model.DatabaseCatalog;
import com.zettais.db2db.core.model.DatabaseDialect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Oracle Data Modeler HR Sample Schema Migration Tests")
class OracleHrSchemaMigrationTest {

    private DatabaseCatalog hrCatalog;
    private Db2DbConfig config;

    @BeforeEach
    void setUp() {
        this.hrCatalog = OracleSampleCatalogs.createOracleHrCatalog();
        this.config = new Db2DbConfig();
        this.config.setTargetSchema("hr");
        this.config.setDropExistingTables(true);
    }

    @Test
    @DisplayName("Should successfully migrate Oracle HR catalog to PostgreSQL")
    void testOracleHrToPostgreSqlDdl() {
        var pgGenerator = new PostgreSqlDdlGenerator(config);
        assertThat(pgGenerator.getTargetDialect()).isEqualTo(DatabaseDialect.POSTGRESQL);

        var fullDdl = new StringBuilder();

        // Sequences
        for (var seq : hrCatalog.getSequences()) {
            fullDdl.append(pgGenerator.generateSequenceDdl(seq));
        }

        // Tables
        for (var table : hrCatalog.getTables()) {
            fullDdl.append(pgGenerator.generateTableDdl(table));
        }

        // Foreign Keys
        for (var table : hrCatalog.getTables()) {
            fullDdl.append(pgGenerator.generateDeferredForeignKeys(table));
        }

        String ddl = fullDdl.toString();

        // Verifications
        assertThat(ddl)
                .contains("CREATE SEQUENCE hr.employees_seq")
                .contains("CREATE TABLE hr.employees (")
                .contains("salary numeric(8,2)")
                .contains("hire_date timestamp")
                .contains("CONSTRAINT emp_emp_id_pk PRIMARY KEY (employee_id)")
                .contains("CONSTRAINT emp_salary_min CHECK (salary > 0)")
                .contains("ALTER TABLE hr.employees")
                .contains("ADD CONSTRAINT emp_mgr_fk")
                .contains("REFERENCES hr.employees (employee_id)");
    }

    @Test
    @DisplayName("Should successfully migrate Oracle HR catalog to MariaDB")
    void testOracleHrToMariaDbDdl() {
        var mariaGenerator = new MariaDbDdlGenerator(config);
        assertThat(mariaGenerator.getTargetDialect()).isEqualTo(DatabaseDialect.MARIADB);

        var fullDdl = new StringBuilder();

        // Sequences (MariaDB 10.3+)
        for (var seq : hrCatalog.getSequences()) {
            fullDdl.append(mariaGenerator.generateSequenceDdl(seq));
        }

        // Tables
        for (var table : hrCatalog.getTables()) {
            fullDdl.append(mariaGenerator.generateTableDdl(table));
        }

        // Foreign Keys
        for (var table : hrCatalog.getTables()) {
            fullDdl.append(mariaGenerator.generateDeferredForeignKeys(table));
        }

        String ddl = fullDdl.toString();

        // Verifications
        assertThat(ddl)
                .contains("CREATE SEQUENCE IF NOT EXISTS `employees_seq`")
                .contains("START WITH 207")
                .contains("CREATE TABLE `employees` (")
                .contains("`salary` DECIMAL(8, 2)")
                .contains("`hire_date` DATETIME(6) NOT NULL")
                .contains("CONSTRAINT `emp_emp_id_pk` PRIMARY KEY (`employee_id`)")
                .contains("CONSTRAINT `emp_email_uk` UNIQUE (`email`)")
                .contains("CONSTRAINT `emp_salary_min` CHECK (salary > 0)")
                .contains("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;")
                .contains("ALTER TABLE `employees`")
                .contains("ADD CONSTRAINT `emp_mgr_fk`")
                .contains("FOREIGN KEY (`manager_id`)")
                .contains("REFERENCES `employees` (`employee_id`)");
    }

    @Test
    @DisplayName("Should evaluate Oracle HR complexity and units in AssessmentEngine")
    void testOracleHrAssessment() {
        var engine = new AssessmentEngine();
        var report = engine.assess(hrCatalog);

        assertThat(report.getSchemaName()).isEqualTo("HR");
        assertThat(report.getTotalTables()).isEqualTo(7);
        assertThat(report.getTotalColumns()).isEqualTo(35);
        assertThat(report.getTotalConstraints()).isEqualTo(20);
        assertThat(report.getTotalSequences()).isEqualTo(3);
        assertThat(report.getTotalMigrationCostUnits()).isGreaterThan(0.0);
        assertThat(report.getEstimatedHumanDays()).isGreaterThan(0.0);
    }
}
