package org.db2db.core.extractor;

import org.db2db.core.config.Db2DbConfig;
import org.db2db.core.model.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dedicated Microsoft SQL Server metadata extractor querying INFORMATION_SCHEMA
 * and sys catalog views.
 * Replicates the metadata introspection logic from Ora2Pg's MSSQL.pm.
 */
public class MssqlMetadataExtractor implements MetadataExtractor {

    private final Db2DbConfig config;

    public MssqlMetadataExtractor(Db2DbConfig config) {
        this.config = config != null ? config : new Db2DbConfig();
    }

    @Override
    public DatabaseCatalog extractCatalog(Connection connection, String schema) throws SQLException {
        var targetSchema = (schema != null && !schema.isBlank()) ? schema : "dbo";
        var catalog = new DatabaseCatalog(targetSchema);

        // 1. Tables
        Map<String, Table> tableMap = new LinkedHashMap<>();
        var sqlTables = """
                SELECT TABLE_NAME
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE'
                ORDER BY TABLE_NAME
                """;

        try (var ps = connection.prepareStatement(sqlTables)) {
            ps.setString(1, targetSchema);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    var tableName = rs.getString("TABLE_NAME");
                    if (config.isTableAllowed(tableName)) {
                        var table = new Table(targetSchema, tableName);
                        tableMap.put(tableName, table);
                    }
                }
            }
        }

        // 2. Columns (with Identity detection)
        var sqlCols = """
                SELECT c.TABLE_NAME, c.COLUMN_NAME, c.DATA_TYPE,
                       c.CHARACTER_MAXIMUM_LENGTH, c.NUMERIC_PRECISION, c.NUMERIC_SCALE,
                       c.IS_NULLABLE, c.COLUMN_DEFAULT,
                       COLUMNPROPERTY(object_id(c.TABLE_SCHEMA + '.' + c.TABLE_NAME), c.COLUMN_NAME, 'IsIdentity') AS is_identity
                FROM INFORMATION_SCHEMA.COLUMNS c
                WHERE c.TABLE_SCHEMA = ?
                ORDER BY c.TABLE_NAME, c.ORDINAL_POSITION
                """;

        try (var ps = connection.prepareStatement(sqlCols)) {
            ps.setString(1, targetSchema);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    var tableName = rs.getString("TABLE_NAME");
                    var table = tableMap.get(tableName);
                    if (table != null) {
                        var col = new Column();
                        col.setName(rs.getString("COLUMN_NAME"));
                        col.setOriginalType(rs.getString("DATA_TYPE"));
                        col.setLength(rs.getInt("CHARACTER_MAXIMUM_LENGTH"));
                        col.setPrecision(rs.getInt("NUMERIC_PRECISION"));
                        col.setScale(rs.getInt("NUMERIC_SCALE"));
                        col.setNullable("YES".equalsIgnoreCase(rs.getString("IS_NULLABLE")));
                        col.setDefaultValue(rs.getString("COLUMN_DEFAULT"));
                        col.setIdentity(rs.getInt("is_identity") == 1);
                        table.addColumn(col);
                    }
                }
            }
        }

        // 3. Constraints (Primary Key, Foreign Key, Unique)
        var sqlCons = """
                SELECT tc.TABLE_NAME, tc.CONSTRAINT_NAME, tc.CONSTRAINT_TYPE,
                       kcu.COLUMN_NAME,
                       ccu.TABLE_NAME AS REFERENCED_TABLE_NAME,
                       ccu.COLUMN_NAME AS REFERENCED_COLUMN_NAME
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
                JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu
                  ON tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME
                 AND tc.TABLE_SCHEMA = kcu.TABLE_SCHEMA
                 AND tc.TABLE_NAME = kcu.TABLE_NAME
                LEFT JOIN INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS rc
                  ON tc.CONSTRAINT_NAME = rc.CONSTRAINT_NAME
                 AND tc.TABLE_SCHEMA = rc.CONSTRAINT_SCHEMA
                LEFT JOIN INFORMATION_SCHEMA.CONSTRAINT_COLUMN_USAGE ccu
                  ON rc.UNIQUE_CONSTRAINT_NAME = ccu.CONSTRAINT_NAME
                 AND rc.UNIQUE_CONSTRAINT_SCHEMA = ccu.CONSTRAINT_SCHEMA
                WHERE tc.TABLE_SCHEMA = ?
                ORDER BY tc.TABLE_NAME, tc.CONSTRAINT_NAME, kcu.ORDINAL_POSITION
                """;

        try (var ps = connection.prepareStatement(sqlCons)) {
            ps.setString(1, targetSchema);
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
                SELECT t.name AS table_name, ind.name AS index_name, ind.is_unique, col.name AS column_name
                FROM sys.indexes ind
                JOIN sys.index_columns ic ON ind.object_id = ic.object_id AND ind.index_id = ic.index_id
                JOIN sys.columns col ON ic.object_id = col.object_id AND ic.column_id = col.column_id
                JOIN sys.tables t ON ind.object_id = t.object_id
                JOIN sys.schemas s ON t.schema_id = s.schema_id
                WHERE s.name = ? AND ind.is_primary_key = 0 AND ind.type_desc != 'HEAP'
                ORDER BY t.name, ind.name, ic.key_ordinal
                """;

        try (var ps = connection.prepareStatement(sqlIdx)) {
            ps.setString(1, targetSchema);
            try (var rs = ps.executeQuery()) {
                Map<String, Index> idxMap = new HashMap<>();
                while (rs.next()) {
                    var tableName = rs.getString("table_name");
                    var table = tableMap.get(tableName);
                    if (table == null) continue;

                    var idxName = rs.getString("index_name");
                    var isUnique = rs.getBoolean("is_unique");
                    var colName = rs.getString("column_name");

                    var index = idxMap.computeIfAbsent(tableName + ":" + idxName, k -> {
                        var idx = new Index(idxName, tableName, isUnique);
                        table.addIndex(idx);
                        return idx;
                    });
                    index.getColumns().add(colName);
                }
            }
        }

        catalog.getTables().addAll(tableMap.values());

        // 5. Views
        var sqlViews = """
                SELECT TABLE_NAME, VIEW_DEFINITION
                FROM INFORMATION_SCHEMA.VIEWS
                WHERE TABLE_SCHEMA = ?
                """;

        try (var ps = connection.prepareStatement(sqlViews)) {
            ps.setString(1, targetSchema);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    var viewName = rs.getString("TABLE_NAME");
                    var def = rs.getString("VIEW_DEFINITION");
                    catalog.getViews().add(new View(targetSchema, viewName, def, false));
                }
            }
        }

        // 6. Routines (Stored Procedures & Functions)
        var sqlRoutines = """
                SELECT ROUTINE_NAME, ROUTINE_TYPE, ROUTINE_DEFINITION
                FROM INFORMATION_SCHEMA.ROUTINES
                WHERE ROUTINE_SCHEMA = ?
                """;

        try (var ps = connection.prepareStatement(sqlRoutines)) {
            ps.setString(1, targetSchema);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    var name = rs.getString("ROUTINE_NAME");
                    var type = rs.getString("ROUTINE_TYPE");
                    var body = rs.getString("ROUTINE_DEFINITION");
                    var unitType = "FUNCTION".equalsIgnoreCase(type)
                            ? PlSqlUnit.UnitType.FUNCTION
                            : PlSqlUnit.UnitType.PROCEDURE;
                    catalog.getPlSqlUnits().add(new PlSqlUnit(targetSchema, name, unitType, body));
                }
            }
        }

        return catalog;
    }
}
