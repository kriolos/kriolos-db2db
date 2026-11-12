package org.db2db.core.ddl;

import org.db2db.core.model.Column;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MySqlDataTypeMapperTest {

    private MySqlDataTypeMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new MySqlDataTypeMapper();
    }

    @Test
    void testUnsignedIntegerEscalations() {
        Column tinyUnsigned = new Column("id", "TINYINT UNSIGNED", false);
        assertThat(mapper.mapToPostgreSql(tinyUnsigned)).isEqualTo("smallint");

        Column smallUnsigned = new Column("id", "SMALLINT UNSIGNED", false);
        assertThat(mapper.mapToPostgreSql(smallUnsigned)).isEqualTo("integer");

        Column intUnsigned = new Column("id", "INT UNSIGNED", false);
        assertThat(mapper.mapToPostgreSql(intUnsigned)).isEqualTo("bigint");

        Column bigUnsigned = new Column("id", "BIGINT UNSIGNED", false);
        assertThat(mapper.mapToPostgreSql(bigUnsigned)).isEqualTo("numeric(20)");
    }

    @Test
    void testBooleansAndText() {
        Column bool1 = new Column("flag", "TINYINT(1)", false);
        assertThat(mapper.mapToPostgreSql(bool1)).isEqualTo("boolean");

        Column bool2 = new Column("is_active", "BOOLEAN", false);
        assertThat(mapper.mapToPostgreSql(bool2)).isEqualTo("boolean");

        Column mediumText = new Column("description", "MEDIUMTEXT", true);
        assertThat(mapper.mapToPostgreSql(mediumText)).isEqualTo("text");

        Column longText = new Column("body", "LONGTEXT", true);
        assertThat(mapper.mapToPostgreSql(longText)).isEqualTo("text");
    }

    @Test
    void testBinaryAndBlob() {
        Column longBlob = new Column("avatar", "LONGBLOB", true);
        assertThat(mapper.mapToPostgreSql(longBlob)).isEqualTo("bytea");

        Column varBinary = new Column("hash", "VARBINARY", true);
        assertThat(mapper.mapToPostgreSql(varBinary)).isEqualTo("bytea");
    }

    @Test
    void testModernJsonAndTemporal() {
        Column json = new Column("metadata", "JSON", true);
        assertThat(mapper.mapToPostgreSql(json)).isEqualTo("jsonb");

        Column datetime = new Column("created_at", "DATETIME", false);
        assertThat(mapper.mapToPostgreSql(datetime)).isEqualTo("timestamp without time zone");

        Column timestamp = new Column("updated_at", "TIMESTAMP", false);
        assertThat(mapper.mapToPostgreSql(timestamp)).isEqualTo("timestamptz");

        Column enumCol = new Column("status", "ENUM('A','B','C')", false);
        assertThat(mapper.mapToPostgreSql(enumCol)).isEqualTo("varchar(255)");
    }
}
