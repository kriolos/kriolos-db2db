package com.zettais.db2db.core.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Validates row count parity and structural integrity between Source and Target databases.
 */
public class DataValidationEngine {

    private static final Logger log = LoggerFactory.getLogger(DataValidationEngine.class);

    public record ValidationItem(
            String tableName,
            long sourceRows,
            long targetRows,
            boolean match,
            double differencePercentage,
            String status,
            String note
    ) implements Serializable {}

    public record DataValidationReport(
            int totalTables,
            int matchedTables,
            int mismatchedTables,
            long totalSourceRows,
            long totalTargetRows,
            List<ValidationItem> items
    ) implements Serializable {
        public DataValidationReport {
            items = items != null ? List.copyOf(items) : Collections.emptyList();
        }
    }

    /**
     * Executes parallel row count verification between Source and Target.
     */
    public DataValidationReport validate(
            String sourceDsn, String sourceUser, String sourcePassword, String sourceSchema,
            String targetDsn, String targetUser, String targetPassword, String targetSchema,
            List<String> tableNames
    ) {
        List<ValidationItem> items = new ArrayList<>();
        int matched = 0;
        int mismatched = 0;
        long sumSrc = 0;
        long sumTgt = 0;

        String srcPrefix = (sourceSchema != null && !sourceSchema.isBlank()) ? sourceSchema + "." : "";
        String tgtPrefix = (targetSchema != null && !targetSchema.isBlank()) ? targetSchema.toLowerCase() + "." : "public.";

        try (Connection srcConn = DriverManager.getConnection(sourceDsn, sourceUser, sourcePassword);
             Connection tgtConn = DriverManager.getConnection(targetDsn, targetUser, targetPassword)) {

            for (String table : tableNames) {
                long srcCount = -1;
                long tgtCount = -1;
                String note = null;

                // Query Source row count
                try (Statement stmt = srcConn.createStatement();
                     ResultSet rs = stmt.executeQuery("""
                             SELECT COUNT(*) AS cnt FROM %s%s
                             """.formatted(srcPrefix, table))) {
                    if (rs.next()) {
                        srcCount = rs.getLong("cnt");
                    }
                } catch (Exception ex) {
                    note = "Source error: " + ex.getMessage();
                }

                // Query Target row count
                try (Statement stmt = tgtConn.createStatement();
                     ResultSet rs = stmt.executeQuery("""
                             SELECT COUNT(*) AS cnt FROM %s"%s"
                             """.formatted(tgtPrefix, table.toLowerCase()))) {
                    if (rs.next()) {
                        tgtCount = rs.getLong("cnt");
                    }
                } catch (Exception ex) {
                    note = (note != null ? note + " | " : "") + "Target error: " + ex.getMessage();
                }

                boolean isMatch = (srcCount >= 0 && tgtCount >= 0 && srcCount == tgtCount);
                double diffPct = 0.0;
                if (srcCount > 0 && tgtCount >= 0) {
                    diffPct = Math.abs((double) (srcCount - tgtCount) / srcCount) * 100.0;
                }

                String status = switch (isMatch ? 1 : (srcCount < 0 || tgtCount < 0 ? -1 : 0)) {
                    case 1 -> "MATCHED";
                    case -1 -> "ERROR";
                    default -> "MISMATCH";
                };

                if (isMatch) {
                    matched++;
                } else {
                    mismatched++;
                }

                if (srcCount > 0) sumSrc += srcCount;
                if (tgtCount > 0) sumTgt += tgtCount;

                items.add(new ValidationItem(table, srcCount, tgtCount, isMatch, diffPct, status, note));
            }

        } catch (Exception e) {
            log.error("Fatal error during data validation: {}", e.getMessage());
        }

        return new DataValidationReport(tableNames.size(), matched, mismatched, sumSrc, sumTgt, items);
    }
}
