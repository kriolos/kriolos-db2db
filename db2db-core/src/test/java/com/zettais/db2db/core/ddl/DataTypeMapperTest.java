package com.zettais.db2db.core.ddl;

import com.zettais.db2db.core.config.Db2DbConfig;
import com.zettais.db2db.core.model.Column;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataTypeMapperTest {

    private DataTypeMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new DataTypeMapper(new Db2DbConfig());
    }

    @Test
    void testNumericTypeMapping() {
        Column colSmall = new Column("id", "NUMBER", false);
        colSmall.setPrecision(4);
        colSmall.setScale(0);
        assertThat(mapper.mapToPostgreSql(colSmall)).isEqualTo("smallint");

        Column colInt = new Column("id", "NUMBER", false);
        colInt.setPrecision(9);
        colInt.setScale(0);
        assertThat(mapper.mapToPostgreSql(colInt)).isEqualTo("integer");

        Column colBig = new Column("id", "NUMBER", false);
        colBig.setPrecision(18);
        colBig.setScale(0);
        assertThat(mapper.mapToPostgreSql(colBig)).isEqualTo("bigint");

        Column colDecimal = new Column("amount", "NUMBER", false);
        colDecimal.setPrecision(12);
        colDecimal.setScale(2);
        assertThat(mapper.mapToPostgreSql(colDecimal)).isEqualTo("numeric(12,2)");

        Column colUnspecified = new Column("val", "NUMBER", true);
        assertThat(mapper.mapToPostgreSql(colUnspecified)).isEqualTo("numeric");
    }

    @Test
    void testStringTypeMapping() {
        Column colVarchar = new Column("name", "VARCHAR2", false);
        colVarchar.setLength(100);
        assertThat(mapper.mapToPostgreSql(colVarchar)).isEqualTo("varchar(100)");

        Column colChar = new Column("code", "CHAR", false);
        colChar.setLength(3);
        assertThat(mapper.mapToPostgreSql(colChar)).isEqualTo("char(3)");

        Column colClob = new Column("body", "CLOB", true);
        assertThat(mapper.mapToPostgreSql(colClob)).isEqualTo("text");
    }

    @Test
    void testDateTimeAndBinaryMapping() {
        Column colDate = new Column("created_at", "DATE", false);
        assertThat(mapper.mapToPostgreSql(colDate)).isEqualTo("timestamp(0) without time zone");

        Column colTimestamp = new Column("updated_at", "TIMESTAMP WITH TIME ZONE", false);
        assertThat(mapper.mapToPostgreSql(colTimestamp)).isEqualTo("timestamptz");

        Column colBlob = new Column("data", "BLOB", true);
        assertThat(mapper.mapToPostgreSql(colBlob)).isEqualTo("bytea");

        Column colRaw = new Column("guid", "RAW", true);
        assertThat(mapper.mapToPostgreSql(colRaw)).isEqualTo("bytea");
    }

    @Test
    void testDefaultValueTransformation() {
        assertThat(mapper.transformDefaultValue("SYSDATE")).isEqualTo("clock_timestamp()");
        assertThat(mapper.transformDefaultValue("USER")).isEqualTo("CURRENT_USER");
        assertThat(mapper.transformDefaultValue("'ACTIVE'")).isEqualTo("'ACTIVE'");
    }
}
