package com.zettais.db2db.core.extractor;

import com.zettais.db2db.core.config.Db2DbConfig;
import com.zettais.db2db.core.model.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dedicated MySQL / MariaDB metadata extractor querying MySQL information_schema views:
 * TABLES, COLUMNS, TABLE_CONSTRAINTS, KEY_COLUMN_USAGE, STATISTICS, VIEWS, ROUTINES, and TRIGGERS
 * for DB2DB by ZettaIS.
 */
public class MySqlMetadataExtractor implements MetadataExtractor {

    private final Db2DbConfig config;

    public MySqlMetadataExtractor(Db2DbConfig config) {
        this.config = config != null ? config : new Db2DbConfig();
    }

    @Override
    public DatabaseCatalog extractCatalog(Connection connection, String schema) throws SQLException {
        var dbName = (schema != null && !schema.isBlank()) ? schema : connection.getCatalog();
        var catalog = new DatabaseCatalog(dbName);

        // 1. Tables
        Map<String, Table> tableMap = new LinkedHashMap<>();
        var sqlTables = """
                SELECT TABLE_NAME, TABLE_ROWS, TABLE_COMMENT
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE'
                ORDER BY TABLE_NAME
                """;

        try (var ps = connection.prepareStatement(sqlTables)) {
            ps.setString(1, dbName);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    var tableName = rs.getString("TABLE_NAME");
                    if (config.isTableAllowed(tableName)) {
                        var table = new Table(dbName, tableName);
                        table.setEstimatedRowCount(rs.getLong("TABLE_ROWS"));
                        table.setComment(rs.getString("TABLE_COMMENT"));
                        tableMap.put(tableName, table);
                    }
                }
            }
        }

        // 2. Columns
        var sqlCols = """
                SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, COLUMN_TYPE,
                       CHARACTER_MAXIMUM_LENGTH, NUMERIC_PRECISION, NUMERIC_SCALE,
                       IS_NULLABLE, COLUMN_DEFAULT, EXTRA, COLUMN_COMMENT
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = ?
                ORDER BY TABLE_NAME, ORDINAL_POSITION
                """;

        try (var ps = connection.prepareStatement(sqlCols)) {
            ps.setString(1, dbName);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    var tableName = rs.getString("TABLE_NAME");
                    var table = tableMap.get(tableName);
                    if (table != null) {
                        var col = new Column();
                        col.setName(rs.getString("COLUMN_NAME"));
                        var columnType = rs.getString("COLUMN_TYPE");
                        col.setOriginalType(columnType != null ? columnType : rs.getString("DATA_TYPE"));
                        col.setLength(rs.getInt("CHARACTER_MAXIMUM_LENGTH"));
                        col.setPrecision(rs.getInt("NUMERIC_PRECISION"));
                        col.setScale(rs.getInt("NUMERIC_SCALE"));
                        col.setNullable("YES".equalsIgnoreCase(rs.getString("IS_NULLABLE")));
                        col.setDefaultValue(rs.getString("COLUMN_DEFAULT"));
                        col.setComment(rs.getString("COLUMN_COMMENT"));

                        var extra = rs.getString("EXTRA");
                        if (extra != null && extra.toLowerCase().contains("auto_increment")) {
                            col.setIdentity(true);
                        }

                        table.addColumn(col);
                    }
                }
            }
        }

        // 3. Constraints (Primary Key, Foreign Key, Unique)
        var sqlCons = """
                SELECT tc.TABLE_NAME, tc.CONSTRAINT_NAME, tc.CONSTRAINT_TYPE,
                       kcu.COLUMN_NAME, kcu.REFERENCED_TABLE_NAME, kcu.REFERENCED_COLUMN_NAME
                FROM information_schema.TABLE_CONSTRAINTS tc
                JOIN information_schema.KEY_COLUMN_USAGE kcu
                  ON tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME
                 AND tc.TABLE_SCHEMA = kcu.TABLE_SCHEMA
                 AND tc.TABLE_NAME = kcu.TABLE_NAME
                WHERE tc.TABLE_SCHEMA = ?
                ORDER BY tc.TABLE_NAME, tc.CONSTRAINT_NAME, kcu.ORDINAL_POSITION
                """;

        try (var ps = connection.prepareStatement(sqlCons)) {
            ps.setString(1, dbName);
            try (var rs = ps.executeQuery()) {
                Map<String, Constraint> consMap = new HashMap<>();
                while (rs.next()) {
                    var tableName = rs.getString("TABLE_NAME");
                    var table = tableMap.get(tableName);
                    if (table == null) continue;

                    var consName = rs.getString("CONSTRAINT_NAME");
                    var consType = rs.getString("CONSTRAINT_TYPE");
                    var colName = rs.getString("COLUMN_NAME");
                    var refTable = rs.getString("REFERENCED_TABLE_NAME");
                    var refCol = rs.getString("REFERENCED_COLUMN_NAME");

                    var constraint = consMap.computeIfAbsent(tableName + ":" + consName, k -> {
                        var c = new Constraint();
                        c.setName(consName);
                        switch (consType) {
                            case "PRIMARY KEY" -> c.setType(Constraint.Type.PRIMARY_KEY);
                            case "FOREIGN KEY" -> {
                                c.setType(Constraint.Type.FOREIGN_KEY);
                                c.setReferencedTable(refTable);
                            }
                            case "UNIQUE" -> c.setType(Constraint.Type.UNIQUE);
                            default -> c.setType(Constraint.Type.CHECK);
                        }
                        table.addConstraint(c);
                        return c;
                    });
                    constraint.getColumns().add(colName);
                    if (refCol != null) {
                        constraint.getReferencedColumns().add(refCol);
                    }
                }
            }
        }

        // 4. Indexes
        var sqlIdx = """
                SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE, COLUMN_NAME, SUB_PART
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = ?
                ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX
                """;

        try (var ps = connection.prepareStatement(sqlIdx)) {
            ps.setString(1, dbName);
            try (var rs = ps.executeQuery()) {
                Map<String, Index> idxMap = new HashMap<>();
                while (rs.next()) {
                    var tableName = rs.getString("TABLE_NAME");
                    var table = tableMap.get(tableName);
                    if (table == null) continue;

                    var idxName = rs.getString("INDEX_NAME");
                    if ("PRIMARY".equalsIgnoreCase(idxName)) continue; // Handled by PK constraint

                    var nonUnique = rs.getInt("NON_UNIQUE") == 1;
                    var colName = rs.getString("COLUMN_NAME");

                    var index = idxMap.computeIfAbsent(tableName + ":" + idxName, k -> {
                        var idx = new Index(idxName, tableName, !nonUnique);
                        table.addIndex(idx);
                        return idx;
                    });
                    if (colName != null) {
                        index.getColumns().add(colName);
                    }
                }
            }
        }

        catalog.getTables().addAll(tableMap.values());

        // 5. Views
        var sqlViews = """
                SELECT TABLE_NAME, VIEW_DEFINITION
                FROM information_schema.VIEWS
                WHERE TABLE_SCHEMA = ?
                """;

        try (var ps = connection.prepareStatement(sqlViews)) {
            ps.setString(1, dbName);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    var viewName = rs.getString("TABLE_NAME");
                    var def = rs.getString("VIEW_DEFINITION");
                    catalog.getViews().add(new View(dbName, viewName, def, false));
                }
            }
        }

        // 6. Routines (Functions & Procedures)
        var sqlRoutines = """
                SELECT ROUTINE_NAME, ROUTINE_TYPE, ROUTINE_DEFINITION
                FROM information_schema.ROUTINES
                WHERE ROUTINE_SCHEMA = ?
                """;

        try (var ps = connection.prepareStatement(sqlRoutines)) {
            ps.setString(1, dbName);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    var name = rs.getString("ROUTINE_NAME");
                    var type = rs.getString("ROUTINE_TYPE");
                    var body = rs.getString("ROUTINE_DEFINITION");
                    var unitType = "FUNCTION".equalsIgnoreCase(type)
                            ? PlSqlUnit.UnitType.FUNCTION
                            : PlSqlUnit.UnitType.PROCEDURE;
                    catalog.getPlSqlUnits().add(new PlSqlUnit(dbName, name, unitType, body));
                }
            }
        }

        return catalog;
    }
}
