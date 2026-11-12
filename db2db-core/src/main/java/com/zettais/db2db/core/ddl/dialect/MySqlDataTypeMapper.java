package com.zettais.db2db.core.ddl.dialect;

import com.zettais.db2db.core.config.Db2DbConfig;
import com.zettais.db2db.core.model.Column;
import com.zettais.db2db.core.model.DatabaseDialect;

import java.util.Locale;

/**
 * Dedicated MySQL / MariaDB data type mapping strategy for DB2DB by ZettaIS.
 */
public final class MySqlDataTypeMapper implements DialectDataTypeMapper {

    @Override
    public DatabaseDialect getDialect() {
        return DatabaseDialect.MYSQL;
    }

    @Override
    public String mapToPostgreSql(Column column, Db2DbConfig config) {
        if (column == null || column.getOriginalType() == null) {
            return "text";
        }

        var rawType = column.getOriginalType().trim().toUpperCase(Locale.ROOT);
        var precision = column.getPrecision();
        var scale = column.getScale();
        var length = column.getLength();

        // 1. Unsigned integer escalations
        if (rawType.contains("UNSIGNED")) {
            if (rawType.startsWith("TINYINT")) return "smallint";
            if (rawType.startsWith("SMALLINT") || rawType.startsWith("MEDIUMINT")) return "integer";
            if (rawType.startsWith("INT")) return "bigint";
            if (rawType.startsWith("BIGINT")) return "numeric(20)";
        }

        // 2. Standard Integers & Booleans
        if (rawType.equals("TINYINT(1)") || rawType.equals("BOOL") || rawType.equals("BOOLEAN")) {
            return "boolean";
        }
        if (rawType.startsWith("TINYINT")) return "smallint";
        if (rawType.startsWith("SMALLINT")) return "smallint";
        if (rawType.startsWith("MEDIUMINT")) return "integer";
        if (rawType.startsWith("INT") || rawType.startsWith("INTEGER")) return "integer";
        if (rawType.startsWith("BIGINT")) return "bigint";

        // 3. Exact & Approximate Numerics
        if (rawType.startsWith("DECIMAL") || rawType.startsWith("NUMERIC") || rawType.startsWith("DEC")) {
            return (precision > 0 && scale >= 0) ? "numeric(" + precision + "," + scale + ")" : "numeric";
        }
        if (rawType.startsWith("FLOAT")) return "real";
        if (rawType.startsWith("DOUBLE") || rawType.startsWith("REAL")) return "double precision";

        // 4. Character Data & Text
        if (rawType.startsWith("VARCHAR")) {
            return length > 0 ? "varchar(" + length + ")" : "varchar";
        }
        if (rawType.startsWith("CHAR")) {
            return length > 0 ? "char(" + length + ")" : "char(1)";
        }
        if (rawType.contains("TEXT")) {
            return "text";
        }

        // 5. Binary & BLOBs
        if (rawType.contains("BLOB") || rawType.contains("BINARY")) {
            return "bytea";
        }

        // 6. Temporal Types
        if (rawType.equals("DATE")) return "date";
        if (rawType.equals("DATETIME")) return "timestamp without time zone";
        if (rawType.equals("TIMESTAMP")) return "timestamptz";
        if (rawType.equals("TIME")) return "time without time zone";
        if (rawType.equals("YEAR")) return "smallint";

        // 7. Modern MySQL JSON and Special Types
        if (rawType.equals("JSON")) return "jsonb";
        if (rawType.startsWith("ENUM")) return "varchar(255)";
        if (rawType.startsWith("SET")) return "text[]";

        return rawType.toLowerCase(Locale.ROOT);
    }
}
