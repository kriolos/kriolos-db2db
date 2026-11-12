package org.db2db.core.ddl.dialect;

import org.db2db.core.config.Db2DbConfig;
import org.db2db.core.model.Column;
import org.db2db.core.model.DatabaseDialect;

import java.util.Locale;

/**
 * Dedicated Oracle data type mapping strategy implementing Ora2Pg's Oracle.pm matrix.
 */
public final class OracleDataTypeMapper implements DialectDataTypeMapper {

    @Override
    public DatabaseDialect getDialect() {
        return DatabaseDialect.ORACLE;
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

        // Numeric types
        if (rawType.startsWith("NUMBER")) {
            if (scale > 0) {
                return precision > 0 ? "numeric(" + precision + "," + scale + ")" : "numeric";
            } else if (precision > 0 && scale == 0) {
                if (precision <= 4) return "smallint";
                if (precision <= 9) return "integer";
                if (precision <= 18) return "bigint";
                return "numeric(" + precision + ")";
            } else if (scale < 0) {
                return "numeric(" + precision + "," + scale + ")";
            } else {
                if (config != null && config.isDefaultNumericAsBigint()) {
                    return "bigint";
                }
                return (config != null && config.isPgNumericType()) ? "numeric" : "double precision";
            }
        }

        if (rawType.equals("BINARY_FLOAT") || rawType.equals("FLOAT4")) return "real";
        if (rawType.equals("BINARY_DOUBLE") || rawType.equals("FLOAT") || rawType.equals("FLOAT8") || rawType.equals("DOUBLE PRECISION")) {
            return "double precision";
        }

        // Strings
        if (rawType.startsWith("VARCHAR2") || rawType.startsWith("NVARCHAR2") || rawType.startsWith("VARCHAR")) {
            return (length > 0 && length <= 10485760) ? "varchar(" + length + ")" : "varchar";
        }
        if (rawType.startsWith("CHAR") || rawType.startsWith("NCHAR")) {
            return length > 0 ? "char(" + length + ")" : "char(1)";
        }

        // LOBs
        if (rawType.equals("CLOB") || rawType.equals("NCLOB") || rawType.equals("LONG")) return "text";
        if (rawType.equals("BLOB") || rawType.equals("RAW") || rawType.equals("LONG RAW")) return "bytea";

        // Temporal
        if (rawType.equals("DATE")) return "timestamp(0) without time zone";
        if (rawType.startsWith("TIMESTAMP WITH LOCAL TIME ZONE") || rawType.startsWith("TIMESTAMP WITH TIME ZONE")) {
            return "timestamptz";
        }
        if (rawType.startsWith("TIMESTAMP")) return "timestamp";
        if (rawType.startsWith("INTERVAL YEAR") || rawType.startsWith("INTERVAL DAY")) return "interval";

        // Special types
        if (rawType.equals("XMLTYPE")) return "xml";
        if (rawType.equals("SDO_GEOMETRY")) return "geometry";
        if (rawType.equals("ROWID") || rawType.equals("UROWID")) return "text";
        if (rawType.equals("BOOLEAN") || rawType.equals("BOOL")) return "boolean";

        return rawType.toLowerCase(Locale.ROOT);
    }
}
