package org.db2db.core.ddl;

import org.db2db.core.model.Column;

import java.util.Locale;

/**
 * Dedicated MySQL to PostgreSQL data type mapper, replicating the %SQL_TYPE matrix
 * from Ora2Pg's MySQL.pm.
 */
public class MySqlDataTypeMapper {

    public String mapToPostgreSql(Column column) {
        if (column == null || column.getOriginalType() == null) {
            return "text";
        }

        var rawType = column.getOriginalType().trim().toUpperCase(Locale.ROOT);
        var precision = column.getPrecision();
        var scale = column.getScale();
        var length = column.getLength();

        // 1. Unsigned and Integer Escalations
        if (rawType.contains("UNSIGNED")) {
            if (rawType.startsWith("TINYINT")) return "smallint";
            if (rawType.startsWith("SMALLINT") || rawType.startsWith("MEDIUMINT")) return "integer";
            if (rawType.startsWith("INT")) return "bigint";
            if (rawType.startsWith("BIGINT")) return "numeric(20)";
        }

        // 2. Standard Integers and Booleans
        if (rawType.equals("TINYINT(1)") || rawType.equals("BOOL") || rawType.equals("BOOLEAN")) {
            return "boolean";
        }
        if (rawType.startsWith("TINYINT")) return "smallint";
        if (rawType.startsWith("SMALLINT")) return "smallint";
        if (rawType.startsWith("MEDIUMINT")) return "integer";
        if (rawType.startsWith("INT") || rawType.startsWith("INTEGER")) return "integer";
        if (rawType.startsWith("BIGINT")) return "bigint";

        // 3. Floating Point and Decimal
        if (rawType.startsWith("DECIMAL") || rawType.startsWith("NUMERIC") || rawType.startsWith("DEC")) {
            if (precision > 0 && scale >= 0) {
                return "numeric(" + precision + "," + scale + ")";
            }
            return "numeric";
        }
        if (rawType.startsWith("FLOAT")) return "real";
        if (rawType.startsWith("DOUBLE") || rawType.startsWith("REAL")) return "double precision";

        // 4. Strings & Text
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

        // 6. Date, Time, and Temporal
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
