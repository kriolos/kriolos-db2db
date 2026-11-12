package org.db2db.core.extractor;

import org.db2db.core.config.Db2DbConfig;
import org.db2db.core.model.*;

import java.sql.*;
import java.util.*;

/**
 * Dedicated Oracle metadata extractor querying Oracle data dictionary views:
 * ALL_TABLES, ALL_TAB_COLS, ALL_CONSTRAINTS, ALL_INDEXES, ALL_SEQUENCES,
 * ALL_VIEWS, ALL_TRIGGERS, ALL_SOURCE, ALL_TAB_PARTITIONS, and ALL_OBJECTS.
 */
public class OracleMetadataExtractor implements MetadataExtractor {

    private final Db2DbConfig config;

    public OracleMetadataExtractor(Db2DbConfig config) {
        this.config = config != null ? config : new Db2DbConfig();
    }

    @Override
    public DatabaseCatalog extractCatalog(Connection connection, String schema) throws SQLException {
        String owner = (schema != null && !schema.isBlank()) ? schema.toUpperCase(Locale.ROOT) : config.getSourceUser().toUpperCase(Locale.ROOT);
        DatabaseCatalog catalog = new DatabaseCatalog(owner);

        // 1. Tables
        Map<String, Table> tableMap = new LinkedHashMap<>();
        String sqlTables = "SELECT table_name, num_rows FROM all_tables WHERE owner = ? ORDER BY table_name";
        try (PreparedStatement ps = connection.prepareStatement(sqlTables)) {
            ps.setString(1, owner);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    if (config.isTableAllowed(tableName)) {
                        Table table = new Table(owner, tableName);
                        table.setEstimatedRowCount(rs.getLong("NUM_ROWS"));
                        tableMap.put(tableName, table);
                    }
                }
            }
        }

        // 2. Columns
        String sqlCols = "SELECT table_name, column_name, data_type, data_length, data_precision, data_scale, nullable, data_default " +
                         "FROM all_tab_cols WHERE owner = ? AND hidden_column = 'NO' ORDER BY table_name, column_id";
        try (PreparedStatement ps = connection.prepareStatement(sqlCols)) {
            ps.setString(1, owner);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    Table table = tableMap.get(tableName);
                    if (table != null) {
                        Column col = new Column();
                        col.setName(rs.getString("COLUMN_NAME"));
                        col.setOriginalType(rs.getString("DATA_TYPE"));
                        col.setLength(rs.getInt("DATA_LENGTH"));
                        col.setPrecision(rs.getInt("DATA_PRECISION"));
                        col.setScale(rs.getInt("DATA_SCALE"));
                        col.setNullable("Y".equalsIgnoreCase(rs.getString("NULLABLE")));
                        col.setDefaultValue(rs.getString("DATA_DEFAULT"));
                        table.addColumn(col);
                    }
                }
            }
        }

        // 3. Constraints (PK, FK, Unique, Check)
        String sqlCons = "SELECT c.table_name, c.constraint_name, c.constraint_type, c.search_condition, " +
                         "r.table_name AS r_table_name, c.deferrable, c.deferred, cc.column_name " +
                         "FROM all_constraints c " +
                         "LEFT JOIN all_constraints r ON c.r_constraint_name = r.constraint_name AND c.r_owner = r.owner " +
                         "JOIN all_cons_columns cc ON c.constraint_name = cc.constraint_name AND c.owner = cc.owner " +
                         "WHERE c.owner = ? AND c.constraint_type IN ('P', 'R', 'U', 'C') " +
                         "ORDER BY c.table_name, c.constraint_name, cc.position";
        try (PreparedStatement ps = connection.prepareStatement(sqlCons)) {
            ps.setString(1, owner);
            try (ResultSet rs = ps.executeQuery()) {
                Map<String, Constraint> consMap = new HashMap<>();
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    Table table = tableMap.get(tableName);
                    if (table == null) continue;

                    String consName = rs.getString("CONSTRAINT_NAME");
                    String consType = rs.getString("CONSTRAINT_TYPE");
                    String colName = rs.getString("COLUMN_NAME");

                    Constraint constraint = consMap.computeIfAbsent(tableName + ":" + consName, k -> {
                        Constraint c = new Constraint();
                        c.setName(consName);
                        switch (consType) {
                            case "P" -> c.setType(Constraint.Type.PRIMARY_KEY);
                            case "R" -> {
                                c.setType(Constraint.Type.FOREIGN_KEY);
                                try {
                                    c.setReferencedTable(rs.getString("R_TABLE_NAME"));
                                } catch (SQLException ignored) {}
                            }
                            case "U" -> c.setType(Constraint.Type.UNIQUE);
                            case "C" -> {
                                c.setType(Constraint.Type.CHECK);
                                try {
                                    c.setCheckCondition(rs.getString("SEARCH_CONDITION"));
                                } catch (SQLException ignored) {}
                            }
                        }
                        try {
                            c.setDeferrable("DEFERRABLE".equalsIgnoreCase(rs.getString("DEFERRABLE")));
                            c.setDeferred("DEFERRED".equalsIgnoreCase(rs.getString("DEFERRED")));
                        } catch (SQLException ignored) {}
                        table.addConstraint(c);
                        return c;
                    });
                    constraint.getColumns().add(colName);
                }
            }
        }

        // 4. Indexes
        String sqlIdx = "SELECT i.table_name, i.index_name, i.uniqueness, i.index_type, ic.column_name " +
                        "FROM all_indexes i " +
                        "JOIN all_ind_columns ic ON i.index_name = ic.index_name AND i.owner = ic.index_owner " +
                        "WHERE i.owner = ? ORDER BY i.table_name, i.index_name, ic.column_position";
        try (PreparedStatement ps = connection.prepareStatement(sqlIdx)) {
            ps.setString(1, owner);
            try (ResultSet rs = ps.executeQuery()) {
                Map<String, Index> idxMap = new HashMap<>();
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    Table table = tableMap.get(tableName);
                    if (table == null) continue;

                    String idxName = rs.getString("INDEX_NAME");
                    String colName = rs.getString("COLUMN_NAME");
                    boolean unique = "UNIQUE".equalsIgnoreCase(rs.getString("UNIQUENESS"));
                    String type = rs.getString("INDEX_TYPE");

                    Index index = idxMap.computeIfAbsent(tableName + ":" + idxName, k -> {
                        Index idx = new Index(idxName, tableName, unique);
                        if ("BITMAP".equalsIgnoreCase(type)) {
                            idx.setType(Index.IndexType.BITMAP);
                        }
                        table.addIndex(idx);
                        return idx;
                    });
                    index.getColumns().add(colName);
                }
            }
        }

        catalog.getTables().addAll(tableMap.values());

        // 5. Sequences
        String sqlSeq = "SELECT sequence_name, min_value, max_value, increment_by, last_number, cycle_flag, cache_size " +
                        "FROM all_sequences WHERE sequence_owner = ?";
        try (PreparedStatement ps = connection.prepareStatement(sqlSeq)) {
            ps.setString(1, owner);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Sequence seq = new Sequence(owner, rs.getString("SEQUENCE_NAME"));
                    seq.setMinValue(rs.getBigDecimal("MIN_VALUE"));
                    seq.setMaxValue(rs.getBigDecimal("MAX_VALUE"));
                    seq.setIncrementBy(rs.getBigDecimal("INCREMENT_BY"));
                    seq.setLastNumber(rs.getBigDecimal("LAST_NUMBER"));
                    seq.setCycle("Y".equalsIgnoreCase(rs.getString("CYCLE_FLAG")));
                    seq.setCacheSize(rs.getInt("CACHE_SIZE"));
                    catalog.getSequences().add(seq);
                }
            }
        }

        // 6. Views
        String sqlViews = "SELECT view_name, text FROM all_views WHERE owner = ?";
        try (PreparedStatement ps = connection.prepareStatement(sqlViews)) {
            ps.setString(1, owner);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    catalog.getViews().add(new View(owner, rs.getString("VIEW_NAME"), rs.getString("TEXT"), false));
                }
            }
        }

        // 7. PL/SQL Source (Packages, Procedures, Functions)
        String sqlSource = "SELECT name, type, line, text FROM all_source WHERE owner = ? ORDER BY name, type, line";
        try (PreparedStatement ps = connection.prepareStatement(sqlSource)) {
            ps.setString(1, owner);
            try (ResultSet rs = ps.executeQuery()) {
                Map<String, StringBuilder> sourceMap = new LinkedHashMap<>();
                Map<String, PlSqlUnit.UnitType> typeMap = new LinkedHashMap<>();

                while (rs.next()) {
                    String name = rs.getString("NAME");
                    String rawType = rs.getString("TYPE");
                    String text = rs.getString("TEXT");
                    String key = name + ":" + rawType;

                    sourceMap.computeIfAbsent(key, k -> new StringBuilder()).append(text);
                    typeMap.computeIfAbsent(key, k -> {
                        return switch (rawType) {
                            case "PACKAGE" -> PlSqlUnit.UnitType.PACKAGE_SPEC;
                            case "PACKAGE BODY" -> PlSqlUnit.UnitType.PACKAGE_BODY;
                            case "PROCEDURE" -> PlSqlUnit.UnitType.PROCEDURE;
                            case "FUNCTION" -> PlSqlUnit.UnitType.FUNCTION;
                            case "TYPE" -> PlSqlUnit.UnitType.TYPE_SPEC;
                            case "TYPE BODY" -> PlSqlUnit.UnitType.TYPE_BODY;
                            default -> PlSqlUnit.UnitType.PROCEDURE;
                        };
                    });
                }

                for (Map.Entry<String, StringBuilder> entry : sourceMap.entrySet()) {
                    String[] parts = entry.getKey().split(":");
                    PlSqlUnit unit = new PlSqlUnit(owner, parts[0], typeMap.get(entry.getKey()), entry.getValue().toString());
                    catalog.getPlSqlUnits().add(unit);
                }
            }
        }

        // 8. Invalid Objects
        String sqlInvalid = "SELECT object_type, object_name FROM all_objects WHERE owner = ? AND status = 'INVALID'";
        try (PreparedStatement ps = connection.prepareStatement(sqlInvalid)) {
            ps.setString(1, owner);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    catalog.getInvalidObjects().add(rs.getString("OBJECT_TYPE") + " " + rs.getString("OBJECT_NAME"));
                }
            }
        }

        return catalog;
    }
}
