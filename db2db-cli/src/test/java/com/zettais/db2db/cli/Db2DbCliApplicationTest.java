package com.zettais.db2db.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

class Db2DbCliApplicationTest {

    @Test
    void testCliHelpExecution() {
        Db2DbCliApplication app = new Db2DbCliApplication();
        CommandLine cmd = new CommandLine(app);
        StringWriter sw = new StringWriter();
        cmd.setOut(new PrintWriter(sw));

        int exitCode = cmd.execute("--help");
        assertThat(exitCode).isEqualTo(0);
        assertThat(sw.toString()).contains("DB2DB by ZettaIS");
        assertThat(sw.toString()).contains("test-connection");
        assertThat(sw.toString()).contains("analyze");
        assertThat(sw.toString()).contains("export-schema");
        assertThat(sw.toString()).contains("export-data");
        assertThat(sw.toString()).contains("convert-sql");
        assertThat(sw.toString()).contains("workflow");
    }

    @Test
    void testConvertSqlSubcommand() {
        Db2DbCliApplication app = new Db2DbCliApplication();
        CommandLine cmd = new CommandLine(app);
        StringWriter sw = new StringWriter();
        cmd.setOut(new PrintWriter(sw));

        int exitCode = cmd.execute("convert-sql", "SELECT NVL(amount, 0) FROM DUAL;");
        assertThat(exitCode).isEqualTo(0);
    }

    @Test
    void testTargetDialectOption() {
        Db2DbCliApplication app = new Db2DbCliApplication();
        CommandLine cmd = new CommandLine(app);
        cmd.parseArgs("--target", "mariadb", "--schema", "HR");
        var cfg = app.resolveConfig();
        assertThat(cfg.getTargetDialect()).isEqualTo(com.zettais.db2db.core.model.DatabaseDialect.MARIADB);
        assertThat(cfg.getSourceSchema()).isEqualTo("HR");
    }
}
