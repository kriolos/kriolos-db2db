package com.zettais.db2db.core.ddl;

import com.zettais.db2db.core.config.Db2DbConfig;
import com.zettais.db2db.core.model.*;

import java.util.List;
import java.util.Locale;

/**
 * Generates standards-compliant MariaDB (10.3+) DDL scripts from DatabaseCatalog models.
 * Implements TargetDdlGenerator with backtick quoting, native sequences, InnoDB engine,
 * and OGC spatial data types.
 */
public final class MariaDbDdlGenerator implements TargetDdlGenerator {

    private final Db2DbConfig config;

    public MariaDbDdlGenerator(Db2DbConfig config) {
        this.config = config != null ? config : new Db2DbConfig();
    }

    @Override
    public DatabaseDialect getTargetDialect() {
        return DatabaseDialect.MARIADB;
    }

    @Override
    public String generateTableDdl(Table table) {
        var sb = new StringBuilder();
        String tableName = table.getName().toLowerCase(Locale.ROOT);
        String qualifiedName = "`" + tableName + "`";

        if (config.isDropExistingTables()) {
            sb.append("DROP TABLE IF EXISTS ").append(qualifiedName).append(";\n");
        }

        sb.append("CREATE TABLE ").append(qualifiedName).append(" (\n");

        List<Column> columns = table.getColumns();
        for (int i = 0; i < columns.size(); i++) {
            Column col = columns.get(i);
            String mariaType = mapToMariaDbType(col);

            sb.append("    `").append(col.getName().toLowerCase(Locale.ROOT)).append("` ").append(mariaType);

            if (col.getDefaultValue() != null && !col.getDefaultValue().isBlank()) {
                sb.append(" DEFAULT ").append(transformDefaultValue(col.getDefaultValue()));
            }

            if (!col.isNullable()) {
                sb.append(" NOT NULL");
            }

            if (col.isIdentity()) {
                sb.append(" AUTO_INCREMENT");
            }

            if (i < columns.size() - 1 || hasInlineConstraints(table)) {
                sb.append(",\n");
            } else {
                sb.append("\n");
            }
        }

        // Inline Primary Key, Unique, and Check constraints
        List<Constraint> inlineConstraints = table.getConstraints().stream()
                .filter(c -> c.getType() != Constraint.Type.FOREIGN_KEY)
                .toList();

        for (int i = 0; i < inlineConstraints.size(); i++) {
            Constraint c = inlineConstraints.get(i);
            sb.append("    CONSTRAINT `").append(c.getName().toLowerCase(Locale.ROOT)).append("` ");
            switch (c.getType()) {
                case PRIMARY_KEY -> sb.append("PRIMARY KEY (").append(formatColumnList(c.getColumns())).append(")");
                case UNIQUE -> sb.append("UNIQUE (").append(formatColumnList(c.getColumns())).append(")");
                case CHECK -> sb.append("CHECK (").append(transformCheckExpression(c.getCheckCondition())).append(")");
                default -> {}
            }

            if (i < inlineConstraints.size() - 1) {
                sb.append(",\n");
            } else {
                sb.append("\n");
            }
        }

        sb.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;\n\n");
        return sb.toString();
    }

    @Override
    public String generateDeferredForeignKeys(Table table) {
        var sb = new StringBuilder();
        String tableName = table.getName().toLowerCase(Locale.ROOT);

        for (Constraint c : table.getConstraints()) {
            if (c.getType() == Constraint.Type.FOREIGN_KEY) {
                String fkName = c.getName().toLowerCase(Locale.ROOT);
                String refTable = c.getReferencedTable() != null ? c.getReferencedTable().toLowerCase(Locale.ROOT) : "";

                sb.append("ALTER TABLE `").append(tableName).append("`\n")
                  .append("    ADD CONSTRAINT `").append(fkName).append("`\n")
                  .append("    FOREIGN KEY (").append(formatColumnList(c.getColumns())).append(")\n")
                  .append("    REFERENCES `").append(refTable).append("` (")
                  .append(formatColumnList(c.getReferencedColumns())).append(");\n\n");
            }
        }
        return sb.toString();
    }

    @Override
    public String generateIndexesDdl(Table table) {
        var sb = new StringBuilder();
        String tableName = table.getName().toLowerCase(Locale.ROOT);

        for (Index idx : table.getIndexes()) {
            String idxName = idx.getName().toLowerCase(Locale.ROOT);

            if (idx.isUnique()) {
                sb.append("CREATE UNIQUE INDEX `").append(idxName).append("` ON `").append(tableName).append("` (");
            } else {
                sb.append("CREATE INDEX `").append(idxName).append("` ON `").append(tableName).append("` (");
            }

            sb.append(formatColumnList(idx.getColumns())).append(");\n");
        }

        if (!table.getIndexes().isEmpty()) {
            sb.append("\n");
        }
        return sb.toString();
    }

    @Override
    public String generateSequenceDdl(Sequence seq) {
        var sb = new StringBuilder();
        String seqName = seq.getName().toLowerCase(Locale.ROOT);

        // MariaDB 10.3+ Native Sequence Syntax
        sb.append("CREATE SEQUENCE IF NOT EXISTS `").append(seqName).append("`\n")
          .append("    START WITH ").append(seq.getLastNumber() != null ? seq.getLastNumber() : "1").append("\n")
          .append("    INCREMENT BY ").append(seq.getIncrementBy() != null ? seq.getIncrementBy() : "1");

        if (seq.getMaxValue() != null) {
            sb.append("\n    MAXVALUE ").append(seq.getMaxValue());
        }

        if (seq.getMinValue() != null) {
            sb.append("\n    MINVALUE ").append(seq.getMinValue());
        }

        if (seq.isCycle()) {
            sb.append("\n    CYCLE");
        } else {
            sb.append("\n    NOCYCLE");
        }

        if (seq.getCacheSize() > 1) {
            sb.append("\n    CACHE ").append(seq.getCacheSize());
        } else {
            sb.append("\n    NOCACHE");
        }

        sb.append(";\n\n");
        return sb.toString();
    }

    @Override
    public String generateViewDdl(View view) {
        String viewName = view.getName().toLowerCase(Locale.ROOT);
        return "CREATE OR REPLACE VIEW `" + viewName + "` AS\n" + (view.getDefinition() != null ? view.getDefinition() : "SELECT 1") + ";\n\n";
    }

    /**
     * Maps an enterprise column definition to standards-compliant MariaDB datatypes.
     */
    public String mapToMariaDbType(Column col) {
        String typeName = col.getOriginalType() != null ? col.getOriginalType().toUpperCase(Locale.ROOT) : "VARCHAR";

        if (typeName.contains("VARCHAR2") || typeName.contains("VARCHAR")) {
            int len = col.getLength() > 0 ? col.getLength() : 255;
            if (len > 16383) {
                return "MEDIUMTEXT";
            }
            return "VARCHAR(" + len + ")";
        }

        if (typeName.contains("NVARCHAR")) {
            int len = col.getLength() > 0 ? col.getLength() : 255;
            return "VARCHAR(" + len + ") CHARACTER SET utf8mb4";
        }

        if (typeName.contains("CHAR")) {
            int len = col.getLength() > 0 ? col.getLength() : 1;
            return "CHAR(" + len + ")";
        }

        if (typeName.contains("NUMBER") || typeName.contains("NUMERIC") || typeName.contains("DECIMAL")) {
            int p = col.getPrecision();
            int s = col.getScale();
            if (s > 0) {
                int precision = p > 0 ? p : 38;
                return "DECIMAL(" + precision + ", " + s + ")";
            }
            if (p > 0) {
                if (p <= 4) return "SMALLINT";
                if (p <= 9) return "INT";
                if (p <= 18) return "BIGINT";
                return "DECIMAL(" + p + ", 0)";
            }
            return "DOUBLE";
        }

        if (typeName.contains("FLOAT") || typeName.contains("BINARY_FLOAT")) {
            return "FLOAT";
        }

        if (typeName.contains("DOUBLE") || typeName.contains("BINARY_DOUBLE")) {
            return "DOUBLE";
        }

        if (typeName.equals("DATE") || typeName.contains("TIMESTAMP")) {
            return "DATETIME(6)";
        }

        if (typeName.contains("CLOB") || typeName.contains("TEXT")) {
            return "LONGTEXT";
        }

        if (typeName.contains("BLOB") || typeName.contains("IMAGE")) {
            return "LONGBLOB";
        }

        if (typeName.contains("RAW") || typeName.contains("BINARY")) {
            if (col.getLength() == 16) {
                return "UUID"; // Native UUID type in MariaDB 10.7+
            }
            int len = col.getLength() > 0 ? col.getLength() : 255;
            return "VARBINARY(" + len + ")";
        }

        if (typeName.contains("SDO_GEOMETRY") || typeName.contains("GEOMETRY")) {
            return "GEOMETRY";
        }

        return "VARCHAR(255)";
    }

    private String transformDefaultValue(String rawDefault) {
        String trimmed = rawDefault.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (upper.equals("SYSDATE") || upper.equals("CURRENT_TIMESTAMP") || upper.startsWith("LOCALTIMESTAMP")) {
            return "CURRENT_TIMESTAMP(6)";
        }
        return trimmed;
    }

    private String transformCheckExpression(String expr) {
        if (expr == null || expr.isBlank()) return "1=1";
        return expr.trim();
    }

    private String formatColumnList(List<String> cols) {
        var sb = new StringBuilder();
        for (int i = 0; i < cols.size(); i++) {
            sb.append("`").append(cols.get(i).toLowerCase(Locale.ROOT)).append("`");
            if (i < cols.size() - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    private boolean hasInlineConstraints(Table table) {
        return table.getConstraints().stream().anyMatch(c -> c.getType() != Constraint.Type.FOREIGN_KEY);
    }
}
