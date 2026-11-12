package org.db2db.core.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class Db2DbConfigTest {

    @Test
    void testConfigParsingAndFiltering(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("ora2pg.conf");
        String content = """
            # ora2pg compatibility configuration
            ORACLE_DSN  dbi:Oracle:host=oradb;sid=PROD;port=1521
            ORACLE_USER SCOTT
            ORACLE_PWD  TIGER
            SCHEMA      HR
            TYPE        TABLE
            OUTPUT      /tmp/output
            BATCH_SIZE  50000
            PARALLEL_WORKERS 8
            ALLOW       EMPLOYEES, DEPARTMENTS, ^LOG_.*
            EXCLUDE     TEMP_.*, BACKUP_.*
            PG_NUMERIC_TYPE 1
            FKEY_ADD_DEFERRED 1
            """;
        Files.writeString(configFile, content);

        Db2DbConfig config = Db2DbConfig.loadFromFile(configFile);

        assertThat(config.getSourceDsn()).isEqualTo("dbi:Oracle:host=oradb;sid=PROD;port=1521");
        assertThat(config.getSourceUser()).isEqualTo("SCOTT");
        assertThat(config.getSourcePassword()).isEqualTo("TIGER");
        assertThat(config.getSourceSchema()).isEqualTo("HR");
        assertThat(config.getExportType()).isEqualTo(Db2DbConfig.ExportType.TABLE);
        assertThat(config.getOutputPath()).isEqualTo("/tmp/output");
        assertThat(config.getBatchSize()).isEqualTo(50000);
        assertThat(config.getParallelWorkers()).isEqualTo(8);
        assertThat(config.isPgNumericType()).isTrue();
        assertThat(config.isFkeyAddDeferred()).isTrue();

        // Test filtering
        assertThat(config.isTableAllowed("EMPLOYEES")).isTrue();
        assertThat(config.isTableAllowed("DEPARTMENTS")).isTrue();
        assertThat(config.isTableAllowed("LOG_AUDIT")).isTrue();
        assertThat(config.isTableAllowed("CUSTOMERS")).isFalse(); // Not in allow list
        assertThat(config.isTableAllowed("TEMP_DATA")).isFalse();  // Excluded
    }
}
