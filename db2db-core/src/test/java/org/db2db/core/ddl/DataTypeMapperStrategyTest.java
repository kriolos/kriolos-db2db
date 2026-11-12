package org.db2db.core.ddl;

import org.db2db.core.config.Db2DbConfig;
import org.db2db.core.model.Column;
import org.db2db.core.model.DatabaseDialect;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataTypeMapperStrategyTest {

    @Test
    void testDialectAutoResolutionFromDsn() {
        Db2DbConfig oracleCfg = new Db2DbConfig();
        oracleCfg.setSourceDsn("jdbc:oracle:thin:@oradb:1521/XEPDB1");
        DataTypeMapper oracleMapper = new DataTypeMapper(oracleCfg);
        assertThat(oracleMapper.getDialect()).isEqualTo(DatabaseDialect.ORACLE);

        Db2DbConfig mysqlCfg = new Db2DbConfig();
        mysqlCfg.setSourceDsn("jdbc:mysql://localhost:3306/ecommerce");
        DataTypeMapper mysqlMapper = new DataTypeMapper(mysqlCfg);
        assertThat(mysqlMapper.getDialect()).isEqualTo(DatabaseDialect.MYSQL);

        Db2DbConfig mssqlCfg = new Db2DbConfig();
        mssqlCfg.setSourceDsn("jdbc:sqlserver://localhost:1433;databaseName=sales");
        DataTypeMapper mssqlMapper = new DataTypeMapper(mssqlCfg);
        assertThat(mssqlMapper.getDialect()).isEqualTo(DatabaseDialect.MSSQL);
    }

    @Test
    void testStrategyDelegationWithoutCrossBleed() {
        // In MySQL, TINYINT UNSIGNED -> smallint
        Db2DbConfig mysqlCfg = new Db2DbConfig();
        mysqlCfg.setSourceDsn("jdbc:mysql://localhost:3306/db");
        DataTypeMapper mysqlMapper = new DataTypeMapper(mysqlCfg);
        Column col1 = new Column("status", "TINYINT UNSIGNED", false);
        assertThat(mysqlMapper.mapToPostgreSql(col1)).isEqualTo("smallint");

        // In MSSQL, UNIQUEIDENTIFIER -> uuid
        Db2DbConfig mssqlCfg = new Db2DbConfig();
        mssqlCfg.setSourceDsn("jdbc:sqlserver://localhost:1433;databaseName=db");
        DataTypeMapper mssqlMapper = new DataTypeMapper(mssqlCfg);
        Column col2 = new Column("id", "UNIQUEIDENTIFIER", false);
        assertThat(mssqlMapper.mapToPostgreSql(col2)).isEqualTo("uuid");

        // In Oracle, NUMBER(9,0) -> integer
        Db2DbConfig oracleCfg = new Db2DbConfig();
        oracleCfg.setSourceDsn("jdbc:oracle:thin:@db:1521/XE");
        DataTypeMapper oracleMapper = new DataTypeMapper(oracleCfg);
        Column col3 = new Column("emp_id", "NUMBER", false);
        col3.setPrecision(9);
        col3.setScale(0);
        assertThat(oracleMapper.mapToPostgreSql(col3)).isEqualTo("integer");
    }

    @Test
    void testMultiDbmsDefaultValueTransformations() {
        DataTypeMapper mapper = new DataTypeMapper();
        assertThat(mapper.transformDefaultValue("SYSDATE")).isEqualTo("clock_timestamp()");
        assertThat(mapper.transformDefaultValue("GETDATE()")).isEqualTo("clock_timestamp()");
        assertThat(mapper.transformDefaultValue("NEWID()")).isEqualTo("gen_random_uuid()");
        assertThat(mapper.transformDefaultValue("CURRENT_DATE")).isEqualTo("CURRENT_DATE");
    }
}
