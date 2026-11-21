package com.zettais.db2db.core.config;

import com.zettais.db2db.core.model.DatabaseDialect;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Configuration holder for DB2DB by ZettaIS migration platform.
 * Supports standard properties files as well as legacy configuration syntax.
 */
public class Db2DbConfig {

    public enum ExportType {
        TABLE,
        PACKAGE,
        VIEW,
        TRIGGER,
        FUNCTION,
        PROCEDURE,
        SEQUENCE,
        TYPE,
        SYNONYM,
        MVIEW,
        DBLINK,
        DIRECTORY,
        GRANT,
        PARTITION,
        COPY,
        INSERT,
        TEST,
        SHOW_REPORT,
        SHOW_SCHEMA,
        ALL
    }

    private String sourceDsn = "jdbc:oracle:thin:@localhost:1521/XE";
    private String sourceUser = "SYSTEM";
    private String sourcePassword = "";
    private String sourceSchema = "";

    private String targetDsn = "jdbc:postgresql://localhost:5432/postgres";
    private String targetUser = "postgres";
    private String targetPassword = "";
    private String targetSchema = "public";
    private DatabaseDialect targetDialect = DatabaseDialect.POSTGRESQL;

    private ExportType exportType = ExportType.SHOW_REPORT;
    private String outputPath = "./output";
    private int batchSize = 10000;
    private int parallelWorkers = Math.max(2, Runtime.getRuntime().availableProcessors());
    private long dataLimit = 0;
    private boolean pgNumericType = true;
    private boolean defaultNumericAsBigint = false;
    private boolean dropExistingTables = false;
    private boolean fkeyAddDeferred = true;
    private boolean disableTriggers = true;
    private boolean compress = false;

    private List<Pattern> allowTablePatterns = new ArrayList<>();
    private List<Pattern> excludeTablePatterns = new ArrayList<>();
    private Map<String, String> customTypeMap = new HashMap<>();

    public Db2DbConfig() {
    }

    public static Db2DbConfig loadFromFile(Path path) throws IOException {
        Db2DbConfig config = new Db2DbConfig();
        File file = path.toFile();
        if (!file.exists()) {
            throw new IllegalArgumentException("Config file does not exist: " + path);
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                String[] parts = line.split("[\\s=]+", 2);
                if (parts.length < 2) {
                    continue;
                }
                String key = parts[0].trim().toUpperCase(Locale.ROOT);
                String value = parts[1].trim();

                switch (key) {
                    case "ORACLE_DSN":
                    case "SOURCE_DSN":
                        config.setSourceDsn(value);
                        break;
                    case "ORACLE_USER":
                    case "SOURCE_USER":
                        config.setSourceUser(value);
                        break;
                    case "ORACLE_PWD":
                    case "SOURCE_PWD":
                        config.setSourcePassword(value);
                        break;
                    case "SCHEMA":
                    case "SOURCE_SCHEMA":
                        config.setSourceSchema(value);
                        break;
                    case "PG_DSN":
                    case "TARGET_DSN":
                        config.setTargetDsn(value);
                        break;
                    case "PG_USER":
                    case "TARGET_USER":
                        config.setTargetUser(value);
                        break;
                    case "PG_PWD":
                    case "TARGET_PWD":
                        config.setTargetPassword(value);
                        break;
                    case "PG_SCHEMA":
                    case "TARGET_SCHEMA":
                        config.setTargetSchema(value);
                        break;
                    case "TARGET_DIALECT":
                    case "TARGET":
                        try {
                            config.setTargetDialect(DatabaseDialect.valueOf(value.toUpperCase(Locale.ROOT)));
                        } catch (IllegalArgumentException ignored) {
                        }
                        break;
                    case "TYPE":
                        config.setExportType(ExportType.valueOf(value.toUpperCase(Locale.ROOT)));
                        break;
                    case "OUTPUT":
                    case "OUTPUT_DIR":
                        config.setOutputPath(value);
                        break;
                    case "BATCH_SIZE":
                    case "DATA_LIMIT":
                        config.setBatchSize(Integer.parseInt(value));
                        break;
                    case "PARALLEL_WORKERS":
                    case "JOBS":
                        config.setParallelWorkers(Integer.parseInt(value));
                        break;
                    case "ALLOW":
                        for (String item : value.split("[,\\s]+")) {
                            if (!item.isEmpty()) {
                                config.allowTablePatterns.add(Pattern.compile(item, Pattern.CASE_INSENSITIVE));
                            }
                        }
                        break;
                    case "EXCLUDE":
                        for (String item : value.split("[,\\s]+")) {
                            if (!item.isEmpty()) {
                                config.excludeTablePatterns.add(Pattern.compile(item, Pattern.CASE_INSENSITIVE));
                            }
                        }
                        break;
                    case "PG_NUMERIC_TYPE":
                        config.setPgNumericType(Boolean.parseBoolean(value) || "1".equals(value));
                        break;
                    case "DROP_FKEY":
                    case "FKEY_ADD_DEFERRED":
                        config.setFkeyAddDeferred(Boolean.parseBoolean(value) || "1".equals(value));
                        break;
                    case "DISABLE_TRIGGERS":
                        config.setDisableTriggers(Boolean.parseBoolean(value) || "1".equals(value));
                        break;
                    default:
                        break;
                }
            }
        }
        return config;
    }

    public boolean isTableAllowed(String tableName) {
        if (tableName == null) return false;
        if (!allowTablePatterns.isEmpty()) {
            boolean matched = false;
            for (Pattern p : allowTablePatterns) {
                if (p.matcher(tableName).matches()) {
                    matched = true;
                    break;
                }
            }
            if (!matched) return false;
        }

        for (Pattern p : excludeTablePatterns) {
            if (p.matcher(tableName).matches()) {
                return false;
            }
        }
        return true;
    }

    // Getters and Setters
    public String getSourceDsn() { return sourceDsn; }
    public void setSourceDsn(String sourceDsn) { this.sourceDsn = sourceDsn; }
    public String getSourceUser() { return sourceUser; }
    public void setSourceUser(String sourceUser) { this.sourceUser = sourceUser; }
    public String getSourcePassword() { return sourcePassword; }
    public void setSourcePassword(String sourcePassword) { this.sourcePassword = sourcePassword; }
    public String getSourceSchema() { return sourceSchema; }
    public void setSourceSchema(String sourceSchema) { this.sourceSchema = sourceSchema; }

    public String getTargetDsn() { return targetDsn; }
    public void setTargetDsn(String targetDsn) { this.targetDsn = targetDsn; }
    public String getTargetUser() { return targetUser; }
    public void setTargetUser(String targetUser) { this.targetUser = targetUser; }
    public String getTargetPassword() { return targetPassword; }
    public void setTargetPassword(String targetPassword) { this.targetPassword = targetPassword; }
    public String getTargetSchema() { return targetSchema; }
    public void setTargetSchema(String targetSchema) { this.targetSchema = targetSchema; }
    public DatabaseDialect getTargetDialect() {
        if (targetDialect == null) {
            if (targetDsn != null && (targetDsn.toLowerCase(Locale.ROOT).contains("mariadb") || targetDsn.toLowerCase(Locale.ROOT).contains("mysql"))) {
                return DatabaseDialect.MARIADB;
            }
            return DatabaseDialect.POSTGRESQL;
        }
        return targetDialect;
    }
    public void setTargetDialect(DatabaseDialect targetDialect) { this.targetDialect = targetDialect; }

    public ExportType getExportType() { return exportType; }
    public void setExportType(ExportType exportType) { this.exportType = exportType; }
    public String getOutputPath() { return outputPath; }
    public void setOutputPath(String outputPath) { this.outputPath = outputPath; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public int getParallelWorkers() { return parallelWorkers; }
    public void setParallelWorkers(int parallelWorkers) { this.parallelWorkers = parallelWorkers; }
    public long getDataLimit() { return dataLimit; }
    public void setDataLimit(long dataLimit) { this.dataLimit = dataLimit; }
    public boolean isPgNumericType() { return pgNumericType; }
    public void setPgNumericType(boolean pgNumericType) { this.pgNumericType = pgNumericType; }
    public boolean isDefaultNumericAsBigint() { return defaultNumericAsBigint; }
    public void setDefaultNumericAsBigint(boolean defaultNumericAsBigint) { this.defaultNumericAsBigint = defaultNumericAsBigint; }
    public boolean isDropExistingTables() { return dropExistingTables; }
    public void setDropExistingTables(boolean dropExistingTables) { this.dropExistingTables = dropExistingTables; }
    public boolean isFkeyAddDeferred() { return fkeyAddDeferred; }
    public void setFkeyAddDeferred(boolean fkeyAddDeferred) { this.fkeyAddDeferred = fkeyAddDeferred; }
    public boolean isDisableTriggers() { return disableTriggers; }
    public void setDisableTriggers(boolean disableTriggers) { this.disableTriggers = disableTriggers; }
    public boolean isCompress() { return compress; }
    public void setCompress(boolean compress) { this.compress = compress; }

    public List<Pattern> getAllowTablePatterns() { return allowTablePatterns; }
    public List<Pattern> getExcludeTablePatterns() { return excludeTablePatterns; }
    public Map<String, String> getCustomTypeMap() { return customTypeMap; }
}
