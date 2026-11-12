package org.db2db.core.ddl;

import org.db2db.core.config.Db2DbConfig;
import org.db2db.core.ddl.dialect.*;
import org.db2db.core.model.Column;
import org.db2db.core.model.DatabaseDialect;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Enterprise Facade and Strategy Registry for multi-database data type conversion.
 * Eliminates heuristic keyword sniffing by binding to the active source DatabaseDialect.
 */
public class DataTypeMapper {

    private final Db2DbConfig config;
    private final DatabaseDialect dialect;
    private final DialectDataTypeMapper strategy;

    private static final Map<DatabaseDialect, DialectDataTypeMapper> REGISTRY = new EnumMap<>(DatabaseDialect.class);

    static {
        register(new org.db2db.core.ddl.dialect.OracleDataTypeMapper());
        register(new org.db2db.core.ddl.dialect.MySqlDataTypeMapper());
        register(new org.db2db.core.ddl.dialect.MssqlDataTypeMapper());
        register(new org.db2db.core.ddl.dialect.AnsiDataTypeMapper());
    }

    private static void register(DialectDataTypeMapper mapper) {
        REGISTRY.put(mapper.getDialect(), mapper);
    }

    public DataTypeMapper() {
        this(new Db2DbConfig(), DatabaseDialect.ORACLE);
    }

    public DataTypeMapper(Db2DbConfig config) {
        this(config, config != null ? DatabaseDialect.fromDsn(config.getSourceDsn()) : DatabaseDialect.ORACLE);
    }

    public DataTypeMapper(Db2DbConfig config, DatabaseDialect dialect) {
        this.config = config != null ? config : new Db2DbConfig();
        this.dialect = Objects.requireNonNullElse(dialect, DatabaseDialect.ORACLE);
        this.strategy = REGISTRY.getOrDefault(this.dialect, REGISTRY.get(DatabaseDialect.ANSI));
    }

    /**
     * Maps a source column to its target PostgreSQL type using the selected dialect strategy.
     */
    public String mapToPostgreSql(Column column) {
        if (column == null || column.getOriginalType() == null) {
            return "text";
        }

        var rawType = column.getOriginalType().trim().toUpperCase(Locale.ROOT);

        // 1. User-defined custom overrides
        if (config.getCustomTypeMap().containsKey(rawType)) {
            return config.getCustomTypeMap().get(rawType);
        }

        // 2. Delegate cleanly to the active dialect strategy
        return strategy.mapToPostgreSql(column, config);
    }

    /**
     * Translates dialect-specific default expressions into standard PostgreSQL expressions.
     */
    public String transformDefaultValue(String sourceDefault) {
        if (sourceDefault == null) return null;
        var trimmed = sourceDefault.trim();
        return switch (trimmed.toUpperCase(Locale.ROOT)) {
            case "SYSDATE", "GETDATE()", "CURRENT_TIMESTAMP()" -> "clock_timestamp()";
            case "CURRENT_DATE", "CURDATE()" -> "CURRENT_DATE";
            case "SYSTIMESTAMP", "SYSUTCDATETIME()" -> "clock_timestamp()";
            case "USER", "SYSTEM_USER", "CURRENT_USER()" -> "CURRENT_USER";
            case "NEWID()" -> "gen_random_uuid()";
            default -> trimmed;
        };
    }

    public DatabaseDialect getDialect() {
        return dialect;
    }

    public DialectDataTypeMapper getStrategy() {
        return strategy;
    }
}
