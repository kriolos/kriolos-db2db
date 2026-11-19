package com.zettais.db2db.core.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Synchronizes PostgreSQL sequence values with the maximum IDs in tables.
 */
public class SequenceSynchronizer {

    private static final Logger log = LoggerFactory.getLogger(SequenceSynchronizer.class);

    public record SequenceSyncResult(
            String sequenceName,
            String tableName,
            String columnName,
            long syncedValue,
            boolean success,
            String error
    ) {}

    /**
     * Inspects target PostgreSQL sequences and updates their values using setval(..., MAX(col) + 1).
     */
    public List<SequenceSyncResult> synchronizeSequences(
            String targetDsn,
            String targetUser,
            String targetPassword,
            String targetSchema
    ) {
        List<SequenceSyncResult> results = new ArrayList<>();
        String schema = (targetSchema != null && !targetSchema.isBlank()) ? targetSchema.toLowerCase() : "public";

        String findSequencesSql = """
                SELECT
                    s.relname AS sequence_name,
                    t.relname AS table_name,
                    a.attname AS column_name
                FROM pg_class s
                JOIN pg_namespace n ON n.oid = s.relnamespace
                LEFT JOIN pg_depend d ON d.objid = s.oid AND d.deptype = 'a'
                LEFT JOIN pg_class t ON t.oid = d.refobjid
                LEFT JOIN pg_attribute a ON a.attrelid = d.refobjid AND a.attnum = d.refobjsubid
                WHERE s.relkind = 'S'
                  AND n.nspname = '%s'
                ORDER BY s.relname
                """.formatted(schema);

        try (Connection conn = DriverManager.getConnection(targetDsn, targetUser, targetPassword);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(findSequencesSql)) {

            List<SequenceReference> refs = new ArrayList<>();
            while (rs.next()) {
                refs.add(new SequenceReference(
                        rs.getString("sequence_name"),
                        rs.getString("table_name"),
                        rs.getString("column_name")
                ));
            }

            for (SequenceReference ref : refs) {
                if (ref.tableName() != null && ref.columnName() != null) {
                    String maxSql = """
                            SELECT COALESCE(MAX("%s"), 0) AS max_id FROM "%s"."%s"
                            """.formatted(ref.columnName(), schema, ref.tableName());
                    try (Statement maxStmt = conn.createStatement();
                         ResultSet maxRs = maxStmt.executeQuery(maxSql)) {
                        long maxId = 0;
                        if (maxRs.next()) {
                            maxId = maxRs.getLong("max_id");
                        }
                        long nextVal = Math.max(maxId, 1);
                        String setvalSql = """
                                SELECT setval('"%s"."%s"', %d, %b)
                                """.formatted(schema, ref.sequenceName(), nextVal, maxId > 0);
                        try (Statement setStmt = conn.createStatement()) {
                            setStmt.execute(setvalSql);
                        }
                        results.add(new SequenceSyncResult(ref.sequenceName(), ref.tableName(), ref.columnName(), nextVal, true, null));
                        log.info("Synchronized sequence {}.{} -> {} (table: {})", schema, ref.sequenceName(), nextVal, ref.tableName());
                    } catch (Exception ex) {
                        log.warn("Could not synchronize sequence {} with table {}: {}", ref.sequenceName(), ref.tableName(), ex.getMessage());
                        results.add(new SequenceSyncResult(ref.sequenceName(), ref.tableName(), ref.columnName(), 0, false, ex.getMessage()));
                    }
                } else {
                    // Standalone sequence without detected table column dependency
                    results.add(new SequenceSyncResult(ref.sequenceName(), "N/A", "N/A", 1, true, "Standalone sequence"));
                }
            }

        } catch (Exception e) {
            log.error("Failed querying sequences for schema {}: {}", schema, e.getMessage());
        }

        return results;
    }

    private record SequenceReference(String sequenceName, String tableName, String columnName) {}
}
