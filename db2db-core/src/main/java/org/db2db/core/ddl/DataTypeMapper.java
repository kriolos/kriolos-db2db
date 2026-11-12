package org.db2db.core.ddl;

import org.db2db.core.config.Db2DbConfig;
import org.db2db.core.model.Column;

import java.util.Locale;

/**
 * Maps Oracle and generic database data types to PostgreSQL equivalents,
 * fully implementing the ora2pg type mapping matrix.
 */
public class DataTypeMapper {

    private final Db2DbConfig config;
    private final MySqlDataTypeMapper mySqlMapper = new MySqlDataTypeMapper();
    private final MssqlDataTypeMapper mssqlMapper = new MssqlDataTypeMapper();

    public DataTypeMapper(Db2DbConfig config) {
        this.config = config != null ? config : new Db2DbConfig();
    }

    public String mapToPostgreSql(Column column) {
        if (column == null || column.getOriginalType() == null) {
            return "text";
        }

        String rawType = column.getOriginalType().trim().toUpperCase(Locale.ROOT);
        int precision = column.getPrecision();
        int scale = column.getScale();
        int length = column.getLength();

        // Custom user overrides
        if (config.getCustomTypeMap().containsKey(rawType)) {
            return config.getCustomTypeMap().get(rawType);
        }

        // Microsoft SQL Server specific signatures
        if (rawType.equals("BIT") || rawType.equals("MONEY") || rawType.equals("SMALLMONEY")
                || rawType.equals("UNIQUEIDENTIFIER") || rawType.equals("DATETIME2")
                || rawType.equals("DATETIMEOFFSET") || rawType.equals("SMALLDATETIME")
                || rawType.equals("IMAGE") || rawType.equals("ROWVERSION") || rawType.contains("MAX")
                || rawType.equals("HIERARCHYID") || rawType.equals("SQL_VARIANT")) {
            return mssqlMapper.mapToPostgreSql(column);
        }

        // MySQL / MariaDB specific signatures
        if (rawType.contains("UNSIGNED") || rawType.contains("TINYINT") || rawType.contains("MEDIUMINT")
                || rawType.contains("TEXT") || rawType.contains("BLOB") || rawType.equals("DATETIME")
                || rawType.equals("YEAR") || rawType.equals("JSON") || rawType.startsWith("ENUM")
                || rawType.startsWith("SET") || rawType.equals("BOOL") || rawType.equals("BOOLEAN")) {
            return mySqlMapper.mapToPostgreSql(column);
        }

        // Numeric types
        if (rawType.startsWith("NUMBER")) {
            if (scale > 0) {
                if (precision > 0) {
                    return "numeric(" + precision + "," + scale + ")";
                }
                return "numeric";
            } else if (precision > 0 && scale == 0) {
                if (precision <= 4) {
                    return "smallint";
                } else if (precision <= 9) {
                    return "integer";
                } else if (precision <= 18) {
                    return "bigint";
                } else {
                    return "numeric(" + precision + ")";
                }
            } else if (scale < 0) {
                return "numeric(" + precision + "," + scale + ")";
            } else {
                // NUMBER with unspecified precision
                if (config.isDefaultNumericAsBigint()) {
                    return "bigint";
                }
                return config.isPgNumericType() ? "numeric" : "double precision";
            }
        }

        if (rawType.equals("BINARY_FLOAT") || rawType.equals("FLOAT4")) {
            return "real";
        }
        if (rawType.equals("BINARY_DOUBLE") || rawType.equals("FLOAT") || rawType.equals("FLOAT8") || rawType.equals("DOUBLE PRECISION")) {
            return "double precision";
        }

        // String types
        if (rawType.startsWith("VARCHAR2") || rawType.startsWith("NVARCHAR2") || rawType.startsWith("VARCHAR")) {
            if (length > 0 && length <= 10485760) {
                return "varchar(" + length + ")";
            }
            return "varchar";
        }

        if (rawType.startsWith("CHAR") || rawType.startsWith("NCHAR")) {
            if (length > 0) {
                return "char(" + length + ")";
            }
            return "char(1)";
        }

        // LOB & Large types
        if (rawType.equals("CLOB") || rawType.equals("NCLOB") || rawType.equals("LONG")) {
            return "text";
        }
        if (rawType.equals("BLOB") || rawType.equals("RAW") || rawType.equals("LONG RAW")) {
            return "bytea";
        }

        // Date and Time types
        if (rawType.equals("DATE")) {
            return "timestamp(0) without time zone";
        }
        if (rawType.startsWith("TIMESTAMP WITH LOCAL TIME ZONE") || rawType.startsWith("TIMESTAMP WITH TIME ZONE")) {
            return "timestamptz";
        }
        if (rawType.startsWith("TIMESTAMP")) {
            return "timestamp";
        }
        if (rawType.startsWith("INTERVAL YEAR") || rawType.startsWith("INTERVAL DAY")) {
            return "interval";
        }

        // Spatial / XML / Identifiers
        if (rawType.equals("XMLTYPE")) {
            return "xml";
        }
        if (rawType.equals("SDO_GEOMETRY")) {
            return "geometry";
        }
        if (rawType.equals("ROWID") || rawType.equals("UROWID")) {
            return "text";
        }
        if (rawType.equals("BOOLEAN") || rawType.equals("BOOL")) {
            return "boolean";
        }

        return rawType.toLowerCase(Locale.ROOT);
    }

    public String transformDefaultValue(String oracleDefault) {
        if (oracleDefault == null) return null;
        String trimmed = oracleDefault.trim();
        if (trimmed.equalsIgnoreCase("SYSDATE")) {
            return "clock_timestamp()";
        }
        if (trimmed.equalsIgnoreCase("CURRENT_DATE")) {
            return "CURRENT_DATE";
        }
        if (trimmed.equalsIgnoreCase("SYSTIMESTAMP")) {
            return "clock_timestamp()";
        }
        if (trimmed.equalsIgnoreCase("USER")) {
            return "CURRENT_USER";
        }
        return trimmed;
    }
}
