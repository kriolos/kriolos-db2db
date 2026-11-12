package org.db2db.core.model;

import java.util.Locale;

/**
 * Supported source and target database dialects in db2db.
 */
public enum DatabaseDialect {
    ORACLE,
    MYSQL,
    MSSQL,
    ANSI;

    /**
     * Resolves the database dialect from a JDBC DSN string using pattern matching.
     */
    public static DatabaseDialect fromDsn(String dsn) {
        if (dsn == null || dsn.isBlank()) {
            return ORACLE; // Default dialect for DB2DB
        }
        var lower = dsn.toLowerCase(Locale.ROOT);
        if (lower.contains("oracle")) return ORACLE;
        if (lower.contains("mysql") || lower.contains("mariadb")) return MYSQL;
        if (lower.contains("sqlserver") || lower.contains("jtds") || lower.contains("mssql")) return MSSQL;
        return ANSI;
    }
}
