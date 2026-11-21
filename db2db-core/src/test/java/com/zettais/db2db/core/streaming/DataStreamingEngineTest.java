package com.zettais.db2db.core.streaming;

import com.zettais.db2db.core.config.Db2DbConfig;
import com.zettais.db2db.core.model.Column;
import com.zettais.db2db.core.model.DatabaseDialect;
import com.zettais.db2db.core.model.Table;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DataStreamingEngine Multi-Target SQL Generation & Dispatch Tests")
class DataStreamingEngineTest {

    private Table sampleTable;
    private Db2DbConfig pgConfig;
    private Db2DbConfig mariaConfig;

    @BeforeEach
    void setUp() {
        sampleTable = new Table("HR", "EMPLOYEES");
        sampleTable.addColumn(createCol("EMPLOYEE_ID", "NUMBER"));
        sampleTable.addColumn(createCol("FIRST_NAME", "VARCHAR2"));
        sampleTable.addColumn(createCol("LAST_NAME", "VARCHAR2"));
        sampleTable.addColumn(createCol("SALARY", "NUMBER"));

        pgConfig = new Db2DbConfig();
        pgConfig.setTargetDialect(DatabaseDialect.POSTGRESQL);
        pgConfig.setTargetSchema("hr_pg");

        mariaConfig = new Db2DbConfig();
        mariaConfig.setTargetDialect(DatabaseDialect.MARIADB);
        mariaConfig.setTargetSchema("hr_maria");
    }

    private Column createCol(String name, String type) {
        return new Column(name, type, true);
    }

    @Test
    @DisplayName("Should generate valid PostgreSQL COPY FROM STDIN command")
    void testBuildPostgreSqlCopySql() {
        var engine = new DataStreamingEngine(pgConfig);
        String copySql = engine.buildPostgreSqlCopySql(sampleTable);

        assertThat(copySql)
                .startsWith("COPY hr_pg.employees (employee_id, first_name, last_name, salary) FROM STDIN")
                .contains("FORMAT CSV")
                .contains("DELIMITER '\t'")
                .contains("NULL '\\N'");
    }

    @Test
    @DisplayName("Should generate valid MariaDB parameterized multi-column INSERT statement")
    void testBuildMariaDbInsertSql() {
        var engine = new DataStreamingEngine(mariaConfig);
        String insertSql = engine.buildMariaDbInsertSql(sampleTable);

        assertThat(insertSql)
                .isEqualTo("INSERT INTO `hr_maria`.`employees` (`employee_id`, `first_name`, `last_name`, `salary`) VALUES (?, ?, ?, ?)");
    }

    @Test
    @DisplayName("Should support empty/default schema for MariaDB INSERT")
    void testBuildMariaDbInsertSqlNoSchema() {
        mariaConfig.setTargetSchema("");
        var engine = new DataStreamingEngine(mariaConfig);
        String insertSql = engine.buildMariaDbInsertSql(sampleTable);

        assertThat(insertSql)
                .isEqualTo("INSERT INTO `employees` (`employee_id`, `first_name`, `last_name`, `salary`) VALUES (?, ?, ?, ?)");
    }
}
