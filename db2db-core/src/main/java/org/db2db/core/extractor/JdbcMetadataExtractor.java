package org.db2db.core.extractor;

import org.db2db.core.config.Db2DbConfig;
import org.db2db.core.model.*;

import java.sql.*;
import java.util.*;

/**
 * Metadata extractor using standard JDBC DatabaseMetaData.
 * Acts as a resilient fallback and universal introspector.
 */
public class JdbcMetadataExtractor implements MetadataExtractor {

    private final Db2DbConfig config;

    public JdbcMetadataExtractor(Db2DbConfig config) {
        this.config = config != null ? config : new Db2DbConfig();
    }

    @Override
    public DatabaseCatalog extractCatalog(Connection connection, String schema) throws SQLException {
        DatabaseCatalog catalog = new DatabaseCatalog(schema);
        DatabaseMetaData meta = connection.getMetaData();

        String schemaPattern = (schema != null && !schema.isBlank()) ? schema : null;

        // 1. Tables & Columns
        try (ResultSet rsTables = meta.getTables(null, schemaPattern, "%", new String[]{"TABLE"})) {
            while (rsTables.next()) {
                String tableName = rsTables.getString("TABLE_NAME");
                if (!config.isTableAllowed(tableName)) {
                    continue;
                }

                Table table = new Table(schema, tableName);
                table.setComment(rsTables.getString("REMARKS"));

                // Columns
                try (ResultSet rsCols = meta.getColumns(null, schemaPattern, tableName, "%")) {
                    while (rsCols.next()) {
                        Column col = new Column();
                        col.setName(rsCols.getString("COLUMN_NAME"));
                        col.setOriginalType(rsCols.getString("TYPE_NAME"));
                        col.setLength(rsCols.getInt("COLUMN_SIZE"));
                        col.setPrecision(rsCols.getInt("COLUMN_SIZE"));
                        col.setScale(rsCols.getInt("DECIMAL_DIGITS"));
                        col.setNullable("YES".equalsIgnoreCase(rsCols.getString("IS_NULLABLE")));
                        col.setDefaultValue(rsCols.getString("COLUMN_DEF"));
                        col.setComment(rsCols.getString("REMARKS"));
                        table.addColumn(col);
                    }
                }

                // Primary Keys
                try (ResultSet rsPk = meta.getPrimaryKeys(null, schemaPattern, tableName)) {
                    List<String> pkCols = new ArrayList<>();
                    String pkName = "pk_" + tableName;
                    while (rsPk.next()) {
                        pkCols.add(rsPk.getString("COLUMN_NAME"));
                        String name = rsPk.getString("PK_NAME");
                        if (name != null) pkName = name;
                    }
                    if (!pkCols.isEmpty()) {
                        Constraint pk = new Constraint(pkName, Constraint.Type.PRIMARY_KEY);
                        pk.setColumns(pkCols);
                        table.addConstraint(pk);
                    }
                }

                // Foreign Keys
                try (ResultSet rsFk = meta.getImportedKeys(null, schemaPattern, tableName)) {
                    Map<String, Constraint> fkMap = new LinkedHashMap<>();
                    while (rsFk.next()) {
                        String fkName = rsFk.getString("FK_NAME");
                        if (fkName == null) fkName = "fk_" + tableName;
                        Constraint fk = fkMap.computeIfAbsent(fkName, k -> {
                            Constraint c = new Constraint(k, Constraint.Type.FOREIGN_KEY);
                            try {
                                c.setReferencedTable(rsFk.getString("PKTABLE_NAME"));
                            } catch (SQLException ignored) {}
                            return c;
                        });
                        fk.getColumns().add(rsFk.getString("FKCOLUMN_NAME"));
                        fk.getReferencedColumns().add(rsFk.getString("PKCOLUMN_NAME"));
                    }
                    table.getConstraints().addAll(fkMap.values());
                }

                // Indexes
                try (ResultSet rsIdx = meta.getIndexInfo(null, schemaPattern, tableName, false, false)) {
                    Map<String, Index> idxMap = new LinkedHashMap<>();
                    while (rsIdx.next()) {
                        String idxName = rsIdx.getString("INDEX_NAME");
                        if (idxName == null) continue;
                        boolean nonUnique = rsIdx.getBoolean("NON_UNIQUE");
                        String colName = rsIdx.getString("COLUMN_NAME");

                        Index idx = idxMap.computeIfAbsent(idxName, k -> new Index(k, tableName, !nonUnique));
                        if (colName != null) {
                            idx.getColumns().add(colName);
                        }
                    }
                    table.getIndexes().addAll(idxMap.values());
                }

                catalog.getTables().add(table);
            }
        }

        // 2. Views
        try (ResultSet rsViews = meta.getTables(null, schemaPattern, "%", new String[]{"VIEW"})) {
            while (rsViews.next()) {
                String viewName = rsViews.getString("TABLE_NAME");
                if (config.isTableAllowed(viewName)) {
                    catalog.getViews().add(new View(schema, viewName, "-- View definition to be extracted via catalog", false));
                }
            }
        }

        return catalog;
    }
}
