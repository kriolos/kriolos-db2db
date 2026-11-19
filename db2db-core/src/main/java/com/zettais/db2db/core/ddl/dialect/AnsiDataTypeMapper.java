package com.zettais.db2db.core.ddl.dialect;

import com.zettais.db2db.core.config.Db2DbConfig;
import com.zettais.db2db.core.model.Column;
import com.zettais.db2db.core.model.DatabaseDialect;

import java.util.Locale;

/**
 * Standard ANSI SQL fallback data type mapping strategy.
 */
public final class AnsiDataTypeMapper implements DialectDataTypeMapper {

    @Override
    public DatabaseDialect getDialect() {
        return DatabaseDialect.ANSI;
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

        if (rawType.equals("BOOLEAN") || rawType.equals("BOOL")) return "boolean";
        if (rawType.equals("SMALLINT")) return "smallint";
        if (rawType.equals("INTEGER") || rawType.equals("INT")) return "integer";
        if (rawType.equals("BIGINT")) return "bigint";
        if (rawType.startsWith("NUMERIC") || rawType.startsWith("DECIMAL")) {
            return (precision > 0 && scale >= 0) ? "numeric(" + precision + "," + scale + ")" : "numeric";
        }
        if (rawType.equals("REAL")) return "real";
        if (rawType.equals("DOUBLE PRECISION") || rawType.equals("FLOAT")) return "double precision";
        if (rawType.startsWith("VARCHAR") || rawType.startsWith("CHARACTER VARYING")) {
            return length > 0 ? "varchar(" + length + ")" : "varchar";
        }
        if (rawType.startsWith("CHAR") || rawType.startsWith("CHARACTER")) {
            return length > 0 ? "char(" + length + ")" : "char(1)";
        }
        if (rawType.equals("DATE")) return "date";
        if (rawType.equals("TIME")) return "time without time zone";
        if (rawType.startsWith("TIMESTAMP")) return "timestamp";
        if (rawType.equals("CLOB")) return "text";
        if (rawType.equals("BLOB")) return "bytea";

        return rawType.toLowerCase(Locale.ROOT);
    }
}
