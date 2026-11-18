package com.zettais.db2db.core.ddl;

import com.zettais.db2db.core.config.Db2DbConfig;
import com.zettais.db2db.core.model.*;

import java.util.List;

/**
 * Generates standards-compliant PostgreSQL DDL scripts from DatabaseCatalog models.
 */
public class PostgreSqlDdlGenerator {

    private final Db2DbConfig config;
    private final DataTypeMapper typeMapper;

    public PostgreSqlDdlGenerator(Db2DbConfig config) {
        this.config = config != null ? config : new Db2DbConfig();
        this.typeMapper = new DataTypeMapper(this.config);
    }

    public String generateTableDdl(Table table) {
        StringBuilder sb = new StringBuilder();
        String targetSchema = config.getTargetSchema() != null && !config.getTargetSchema().isEmpty()
                ? config.getTargetSchema() : "public";
        String qualifiedName = targetSchema + "." + table.getName().toLowerCase();

        if (config.isDropExistingTables()) {
            sb.append("DROP TABLE IF EXISTS ").append(qualifiedName).append(" CASCADE;\n");
        }

        sb.append("CREATE TABLE ").append(qualifiedName).append(" (\n");

        List<Column> columns = table.getColumns();
        for (int i = 0; i < columns.size(); i++) {
            Column col = columns.get(i);
            String pgType = typeMapper.mapToPostgreSql(col);

            sb.append("    ").append(col.getName().toLowerCase()).append(" ").append(pgType);

            if (col.getDefaultValue() != null && !col.getDefaultValue().isBlank()) {
                sb.append(" DEFAULT ").append(typeMapper.transformDefaultValue(col.getDefaultValue()));
            }

            if (!col.isNullable()) {
                sb.append(" NOT NULL");
            }

            if (i < columns.size() - 1 || hasTableConstraints(table)) {
                sb.append(",");
            }
            sb.append("\n");
        }

        // Inline Primary Key and Unique/Check constraints
        List<Constraint> inlineConstraints = table.getConstraints().stream()
                .filter(c -> c.getType() != Constraint.Type.FOREIGN_KEY || !config.isFkeyAddDeferred())
                .toList();

        for (int i = 0; i < inlineConstraints.size(); i++) {
            Constraint c = inlineConstraints.get(i);
            sb.append("    CONSTRAINT ").append(c.getName().toLowerCase()).append(" ");
            switch (c.getType()) {
                case PRIMARY_KEY -> sb.append("PRIMARY KEY (").append(String.join(", ", c.getColumns().stream().map(String::toLowerCase).toList())).append(")");
                case UNIQUE -> sb.append("UNIQUE (").append(String.join(", ", c.getColumns().stream().map(String::toLowerCase).toList())).append(")");
                case CHECK -> sb.append("CHECK (").append(c.getCheckCondition()).append(")");
                case FOREIGN_KEY -> sb.append("FOREIGN KEY (").append(String.join(", ", c.getColumns().stream().map(String::toLowerCase).toList()))
                        .append(") REFERENCES ").append(targetSchema).append(".").append(c.getReferencedTable().toLowerCase())
                        .append(" (").append(String.join(", ", c.getReferencedColumns().stream().map(String::toLowerCase).toList())).append(")");
            }
            if (i < inlineConstraints.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }

        sb.append(")");

        // Partitioning clause
        if (table.getPartitionInfo() != null && !table.getPartitionInfo().getPartitionColumns().isEmpty()) {
            sb.append(" PARTITION BY ").append(table.getPartitionInfo().getType().name())
              .append(" (").append(String.join(", ", table.getPartitionInfo().getPartitionColumns().stream().map(String::toLowerCase).toList())).append(")");
        }

        sb.append(";\n");

        // Comments
        if (table.getComment() != null && !table.getComment().isBlank()) {
            sb.append("COMMENT ON TABLE ").append(qualifiedName).append(" IS '")
              .append(table.getComment().replace("'", "''")).append("';\n");
        }
        for (Column col : columns) {
            if (col.getComment() != null && !col.getComment().isBlank()) {
                sb.append("COMMENT ON COLUMN ").append(qualifiedName).append(".").append(col.getName().toLowerCase())
                  .append(" IS '").append(col.getComment().replace("'", "''")).append("';\n");
            }
        }

        return sb.toString();
    }

    public String generateDeferredForeignKeys(Table table) {
        StringBuilder sb = new StringBuilder();
        String targetSchema = config.getTargetSchema() != null && !config.getTargetSchema().isEmpty()
                ? config.getTargetSchema() : "public";
        String qualifiedName = targetSchema + "." + table.getName().toLowerCase();

        for (Constraint c : table.getConstraints()) {
            if (c.getType() == Constraint.Type.FOREIGN_KEY && config.isFkeyAddDeferred()) {
                sb.append("ALTER TABLE ").append(qualifiedName)
                  .append(" ADD CONSTRAINT ").append(c.getName().toLowerCase())
                  .append(" FOREIGN KEY (").append(String.join(", ", c.getColumns().stream().map(String::toLowerCase).toList())).append(")")
                  .append(" REFERENCES ").append(targetSchema).append(".").append(c.getReferencedTable().toLowerCase())
                  .append(" (").append(String.join(", ", c.getReferencedColumns().stream().map(String::toLowerCase).toList())).append(")");
                if (c.isDeferrable()) {
                    sb.append(" DEFERRABLE");
                    if (c.isDeferred()) {
                        sb.append(" INITIALLY DEFERRED");
                    }
                }
                sb.append(";\n");
            }
        }
        return sb.toString();
    }

    public String generateIndexesDdl(Table table) {
        StringBuilder sb = new StringBuilder();
        String targetSchema = config.getTargetSchema() != null && !config.getTargetSchema().isEmpty()
                ? config.getTargetSchema() : "public";

        for (Index idx : table.getIndexes()) {
            String idxName = idx.getName().toLowerCase();
            String tableName = targetSchema + "." + table.getName().toLowerCase();

            sb.append("CREATE ");
            if (idx.isUnique()) {
                sb.append("UNIQUE ");
            }
            sb.append("INDEX ").append(idxName).append(" ON ").append(tableName);

            if (idx.getType() == Index.IndexType.FUNCTION_BASED && idx.getExpression() != null) {
                sb.append(" (").append(idx.getExpression().toLowerCase()).append(")");
            } else if (!idx.getColumns().isEmpty()) {
                sb.append(" (").append(String.join(", ", idx.getColumns().stream().map(String::toLowerCase).toList())).append(")");
            }
            sb.append(";\n");
        }
        return sb.toString();
    }

    public String generateSequenceDdl(Sequence seq) {
        var targetSchema = config.getTargetSchema() != null && !config.getTargetSchema().isEmpty()
                ? config.getTargetSchema() : "public";
        var name = targetSchema + "." + seq.getName().toLowerCase();

        return """
               CREATE SEQUENCE %s
                   INCREMENT BY %s
                   MINVALUE %s
                   MAXVALUE %s
                   START WITH %s%s%s;
               """.formatted(
                   name,
                   seq.getIncrementBy(),
                   seq.getMinValue(),
                   seq.getMaxValue(),
                   seq.getLastNumber(),
                   seq.isCycle() ? " CYCLE" : " NO CYCLE",
                   seq.getCacheSize() > 1 ? " CACHE " + seq.getCacheSize() : ""
               );
    }

    public String generateViewDdl(View view) {
        String targetSchema = config.getTargetSchema() != null && !config.getTargetSchema().isEmpty()
                ? config.getTargetSchema() : "public";
        String name = targetSchema + "." + view.getName().toLowerCase();

        StringBuilder sb = new StringBuilder();
        if (view.isMaterialized()) {
            sb.append("CREATE MATERIALIZED VIEW ").append(name).append(" AS\n")
              .append(view.getDefinition()).append(";\n");
        } else {
            sb.append("CREATE OR REPLACE VIEW ").append(name).append(" AS\n")
              .append(view.getDefinition()).append(";\n");
        }
        return sb.toString();
    }

    private boolean hasTableConstraints(Table table) {
        if (!config.isFkeyAddDeferred()) {
            return !table.getConstraints().isEmpty();
        }
        return table.getConstraints().stream().anyMatch(c -> c.getType() != Constraint.Type.FOREIGN_KEY);
    }
}
