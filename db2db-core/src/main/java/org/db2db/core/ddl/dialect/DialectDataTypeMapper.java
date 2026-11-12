package org.db2db.core.ddl.dialect;

import org.db2db.core.config.Db2DbConfig;
import org.db2db.core.model.Column;
import org.db2db.core.model.DatabaseDialect;

/**
 * Sealed Strategy interface for DBMS-specific data type mapping.
 * Each dialect encapsulates its native rules with zero cross-DBMS coupling.
 */
public sealed interface DialectDataTypeMapper
        permits OracleDataTypeMapper, MySqlDataTypeMapper, MssqlDataTypeMapper, AnsiDataTypeMapper {

    /**
     * The database dialect handled by this strategy.
     */
    DatabaseDialect getDialect();

    /**
     * Translates a column definition from the source dialect to its PostgreSQL equivalent.
     *
     * @param column the source column metadata
     * @param config the migration configuration
     * @return the PostgreSQL data type string
     */
    String mapToPostgreSql(Column column, Db2DbConfig config);
}
