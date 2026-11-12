package org.db2db.cli;

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
        assertThat(sw.toString()).contains("Enterprise Oracle & MySQL to PostgreSQL migration engine");
        assertThat(sw.toString()).contains("test-connection");
        assertThat(sw.toString()).contains("analyze");
        assertThat(sw.toString()).contains("export-schema");
        assertThat(sw.toString()).contains("export-data");
        assertThat(sw.toString()).contains("convert-sql");
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
}
