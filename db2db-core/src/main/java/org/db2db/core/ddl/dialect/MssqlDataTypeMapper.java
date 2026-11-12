package org.db2db.core.ddl.dialect;

import org.db2db.core.config.Db2DbConfig;
import org.db2db.core.model.Column;
import org.db2db.core.model.DatabaseDialect;

import java.util.Locale;

/**
 * Dedicated Microsoft SQL Server data type mapping strategy for DB2DB by ZettaIS.
 */
public final class MssqlDataTypeMapper implements DialectDataTypeMapper {

    @Override
    public DatabaseDialect getDialect() {
        return DatabaseDialect.MSSQL;
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

        // 1. Boolean and Integers
        if (rawType.equals("BIT")) return "boolean";
        if (rawType.equals("TINYINT")) return "smallint"; // 1 byte unsigned (0..255)
        if (rawType.equals("SMALLINT")) return "smallint";
        if (rawType.equals("INT") || rawType.equals("INTEGER")) return "integer";
        if (rawType.equals("BIGINT")) return "bigint";

        // 2. Exact Numerics and Currency
        if (rawType.equals("MONEY")) return "numeric(19,4)";
        if (rawType.equals("SMALLMONEY")) return "numeric(10,4)";
        if (rawType.startsWith("DECIMAL") || rawType.startsWith("NUMERIC") || rawType.startsWith("DEC")) {
            return (precision > 0 && scale >= 0) ? "numeric(" + precision + "," + scale + ")" : "numeric";
        }

        // 3. Approximate Numerics
        if (rawType.startsWith("FLOAT")) return "double precision";
        if (rawType.startsWith("REAL")) return "real";

        // 4. Strings & Character Data
        if (rawType.contains("MAX") || rawType.equals("TEXT") || rawType.equals("NTEXT")) {
            return "text";
        }
        if (rawType.startsWith("NVARCHAR") || rawType.startsWith("VARCHAR")) {
            return length > 0 ? "varchar(" + length + ")" : "varchar";
        }
        if (rawType.startsWith("NCHAR") || rawType.startsWith("CHAR")) {
            return length > 0 ? "char(" + length + ")" : "char(1)";
        }

        // 5. Binary & Large Objects
        if (rawType.equals("IMAGE") || rawType.equals("ROWVERSION") || rawType.equals("TIMESTAMP")) {
            return "bytea";
        }
        if (rawType.startsWith("VARBINARY") || rawType.startsWith("BINARY")) {
            return "bytea";
        }

        // 6. Date & Time
        if (rawType.equals("DATE")) return "date";
        if (rawType.equals("SMALLDATETIME")) return "timestamp(0) without time zone";
        if (rawType.equals("DATETIME")) return "timestamp(3) without time zone";
        if (rawType.equals("DATETIME2")) return "timestamp without time zone";
        if (rawType.equals("DATETIMEOFFSET")) return "timestamptz";
        if (rawType.equals("TIME")) return "time without time zone";

        // 7. Special SQL Server Types
        if (rawType.equals("UNIQUEIDENTIFIER")) return "uuid";
        if (rawType.equals("XML")) return "xml";
        if (rawType.equals("HIERARCHYID")) return "varchar(255)";
        if (rawType.equals("SQL_VARIANT")) return "text";

        return rawType.toLowerCase(Locale.ROOT);
    }
}
