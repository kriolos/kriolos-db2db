package com.zettais.db2db.core.ddl;

import com.zettais.db2db.core.model.Column;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MssqlDataTypeMapperTest {

    private MssqlDataTypeMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new MssqlDataTypeMapper();
    }

    @Test
    void testUniqueIdentifierAndBit() {
        Column uuidCol = new Column("guid", "UNIQUEIDENTIFIER", false);
        assertThat(mapper.mapToPostgreSql(uuidCol)).isEqualTo("uuid");

        Column bitCol = new Column("is_active", "BIT", false);
        assertThat(mapper.mapToPostgreSql(bitCol)).isEqualTo("boolean");
    }

    @Test
    void testMoneyAndExactNumerics() {
        Column money = new Column("salary", "MONEY", false);
        assertThat(mapper.mapToPostgreSql(money)).isEqualTo("numeric(19,4)");

        Column smallMoney = new Column("fee", "SMALLMONEY", false);
        assertThat(mapper.mapToPostgreSql(smallMoney)).isEqualTo("numeric(10,4)");
    }

    @Test
    void testMaxTypesAndBinary() {
        Column varcharMax = new Column("notes", "VARCHAR(MAX)", true);
        assertThat(mapper.mapToPostgreSql(varcharMax)).isEqualTo("text");

        Column nvarcharMax = new Column("comments", "NVARCHAR(MAX)", true);
        assertThat(mapper.mapToPostgreSql(nvarcharMax)).isEqualTo("text");

        Column image = new Column("photo", "IMAGE", true);
        assertThat(mapper.mapToPostgreSql(image)).isEqualTo("bytea");

        Column rowversion = new Column("ver", "ROWVERSION", false);
        assertThat(mapper.mapToPostgreSql(rowversion)).isEqualTo("bytea");
    }

    @Test
    void testDateAndTemporalTypes() {
        Column datetimeoffset = new Column("logged_at", "DATETIMEOFFSET", false);
        assertThat(mapper.mapToPostgreSql(datetimeoffset)).isEqualTo("timestamptz");

        Column datetime2 = new Column("created_at", "DATETIME2", false);
        assertThat(mapper.mapToPostgreSql(datetime2)).isEqualTo("timestamp without time zone");

        Column smalldatetime = new Column("exp_date", "SMALLDATETIME", false);
        assertThat(mapper.mapToPostgreSql(smalldatetime)).isEqualTo("timestamp(0) without time zone");
    }
}
