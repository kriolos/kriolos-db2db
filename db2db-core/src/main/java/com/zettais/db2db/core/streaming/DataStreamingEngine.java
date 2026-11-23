package com.zettais.db2db.core.streaming;

import com.zettais.db2db.core.config.Db2DbConfig;
import com.zettais.db2db.core.model.Column;
import com.zettais.db2db.core.model.DatabaseDialect;
import com.zettais.db2db.core.model.Table;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Parallel Data Streaming Engine leveraging JDK 25 Virtual Threads,
 * PostgreSQL CopyManager, and MariaDB batched multi-row streaming for
 * ultra-high throughput database data migration.
 */
public class DataStreamingEngine {

    private static final Logger log = LoggerFactory.getLogger(DataStreamingEngine.class);

    private final Db2DbConfig config;

    public interface StreamProgressCallback {
        void onProgress(String tableName, long rowsTransferred, long bytesTransferred, boolean completed, Throwable error);
    }

    public DataStreamingEngine(Db2DbConfig config) {
        this.config = config != null ? config : new Db2DbConfig();
    }

    /**
     * Streams tables in parallel using JDK 25 Virtual Threads.
     */
    public void streamAllTables(List<Table> tables, StreamProgressCallback progressCallback) throws InterruptedException {
        // JDK 25 Virtual Thread Executor
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (Table t : tables) {
                if (config.isTableAllowed(t.getName())) {
                    futures.add(executor.submit(() -> {
                        try {
                            streamTable(t, progressCallback);
                        } catch (Exception e) {
                            log.error("Failed to stream table {}", t.getName(), e);
                            if (progressCallback != null) {
                                progressCallback.onProgress(t.getName(), 0, 0, true, e);
                            }
                        }
                    }));
                }
            }

            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (ExecutionException e) {
                    log.error("Table streaming task encountered error", e.getCause());
                }
            }
        }
    }

    /**
     * Streams a table to the target database matching the configured target dialect.
     */
    public void streamTable(Table table, StreamProgressCallback progressCallback) throws SQLException, IOException {
        switch (config.getTargetDialect()) {
            case MARIADB -> streamTableToMariaDb(table, progressCallback);
            case POSTGRESQL -> streamTableToPostgreSql(table, progressCallback);
            default -> throw new UnsupportedOperationException("Unsupported target streaming dialect: " + config.getTargetDialect());
        }
    }

    /**
     * Streams a table to PostgreSQL using native CopyManager / COPY FROM STDIN.
     */
    public void streamTableToPostgreSql(Table table, StreamProgressCallback progressCallback) throws SQLException, IOException {
        String copySql = buildPostgreSqlCopySql(table);
        String selectSql = "SELECT * FROM " + (table.getSchema() != null && !table.getSchema().isBlank() ? table.getSchema() + "." : "") + table.getName();

        log.info("Starting PostgreSQL data stream for table {}", table.getName());

        try (Connection srcConn = DriverManager.getConnection(config.getSourceDsn(), config.getSourceUser(), config.getSourcePassword());
             Connection tgtConn = DriverManager.getConnection(config.getTargetDsn(), config.getTargetUser(), config.getTargetPassword())) {

            srcConn.setAutoCommit(false);
            tgtConn.setAutoCommit(true);

            // Unwrap PostgreSQL native connection for CopyManager
            PGConnection pgConn = tgtConn.unwrap(PGConnection.class);
            CopyManager copyManager = pgConn.getCopyAPI();

            PipedOutputStream pos = new PipedOutputStream();
            PipedInputStream pis = new PipedInputStream(pos, 65536);

            AtomicLong rowCount = new AtomicLong();
            AtomicLong byteCount = new AtomicLong();

            // Run copyIn on a dedicated virtual thread
            Thread copyThread = Thread.ofVirtual().start(() -> {
                try {
                    copyManager.copyIn(copySql, pis);
                } catch (Exception e) {
                    throw new RuntimeException("PostgreSQL CopyManager error", e);
                }
            });

            try (PreparedStatement ps = srcConn.prepareStatement(selectSql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(pos, StandardCharsets.UTF_8))) {

                ps.setFetchSize(config.getBatchSize());
                try (ResultSet rs = ps.executeQuery()) {
                    ResultSetMetaData md = rs.getMetaData();
                    int colCount = md.getColumnCount();

                    while (rs.next()) {
                        StringBuilder line = new StringBuilder();
                        for (int i = 1; i <= colCount; i++) {
                            Object val = rs.getObject(i);
                            if (val == null) {
                                line.append("\\N");
                            } else {
                                String str = escapeCsv(val.toString());
                                line.append(str);
                            }
                            if (i < colCount) {
                                line.append("\t");
                            }
                        }
                        line.append("\n");

                        byte[] bytes = line.toString().getBytes(StandardCharsets.UTF_8);
                        writer.write(line.toString());
                        byteCount.addAndGet(bytes.length);

                        long currentRows = rowCount.incrementAndGet();
                        if (currentRows % 10000 == 0 && progressCallback != null) {
                            progressCallback.onProgress(table.getName(), currentRows, byteCount.get(), false, null);
                        }
                    }
                }
                writer.flush();
            } finally {
                pos.close();
            }

            try {
                copyThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SQLException("Data streaming interrupted for table " + table.getName(), e);
            }

            if (progressCallback != null) {
                progressCallback.onProgress(table.getName(), rowCount.get(), byteCount.get(), true, null);
            }

            log.info("Completed PostgreSQL data streaming for table {}: {} rows, {} bytes", table.getName(), rowCount.get(), byteCount.get());
        }
    }

    /**
     * Streams a table to MariaDB using high-throughput batched multi-row prepared statements.
     */
    public void streamTableToMariaDb(Table table, StreamProgressCallback progressCallback) throws SQLException {
        String insertSql = buildMariaDbInsertSql(table);
        String selectSql = "SELECT * FROM " + (table.getSchema() != null && !table.getSchema().isBlank() ? table.getSchema() + "." : "") + table.getName();

        log.info("Starting MariaDB data stream for table {}", table.getName());

        try (Connection srcConn = DriverManager.getConnection(config.getSourceDsn(), config.getSourceUser(), config.getSourcePassword());
             Connection tgtConn = DriverManager.getConnection(config.getTargetDsn(), config.getTargetUser(), config.getTargetPassword())) {

            srcConn.setAutoCommit(false);
            tgtConn.setAutoCommit(false);

            int batchSize = config.getBatchSize() > 0 ? config.getBatchSize() : 5000;
            long rowCount = 0;
            long byteCount = 0;

            try (PreparedStatement psSrc = srcConn.prepareStatement(selectSql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
                 PreparedStatement psTgt = tgtConn.prepareStatement(insertSql)) {

                psSrc.setFetchSize(batchSize);
                try (ResultSet rs = psSrc.executeQuery()) {
                    ResultSetMetaData md = rs.getMetaData();
                    int colCount = md.getColumnCount();

                    int uncommittedCount = 0;
                    while (rs.next()) {
                        for (int i = 1; i <= colCount; i++) {
                            Object val = rs.getObject(i);
                            if (val == null) {
                                psTgt.setNull(i, md.getColumnType(i));
                            } else {
                                psTgt.setObject(i, val);
                                byteCount += val.toString().length();
                            }
                        }
                        psTgt.addBatch();
                        rowCount++;
                        uncommittedCount++;

                        if (uncommittedCount >= batchSize) {
                            psTgt.executeBatch();
                            tgtConn.commit();
                            uncommittedCount = 0;
                            if (progressCallback != null) {
                                progressCallback.onProgress(table.getName(), rowCount, byteCount, false, null);
                            }
                        }
                    }

                    if (uncommittedCount > 0) {
                        psTgt.executeBatch();
                        tgtConn.commit();
                    }
                }
            } catch (SQLException ex) {
                try {
                    tgtConn.rollback();
                } catch (SQLException ignored) {}
                throw ex;
            }

            if (progressCallback != null) {
                progressCallback.onProgress(table.getName(), rowCount, byteCount, true, null);
            }

            log.info("Completed MariaDB data streaming for table {}: {} rows, {} bytes", table.getName(), rowCount, byteCount);
        }
    }

    /**
     * Builds the PostgreSQL COPY FROM STDIN command string for the given table.
     */
    public String buildPostgreSqlCopySql(Table table) {
        String targetSchema = config.getTargetSchema() != null && !config.getTargetSchema().isBlank()
                ? config.getTargetSchema() : "public";
        String targetTable = targetSchema + "." + table.getName().toLowerCase();
        StringBuilder copySql = new StringBuilder("COPY ").append(targetTable).append(" (");
        List<Column> columns = table.getColumns();
        for (int i = 0; i < columns.size(); i++) {
            copySql.append(columns.get(i).getName().toLowerCase());
            if (i < columns.size() - 1) copySql.append(", ");
        }
        copySql.append(") FROM STDIN WITH (FORMAT CSV, DELIMITER '\t', NULL '\\N', ESCAPE '\\')");
        return copySql.toString();
    }

    /**
     * Builds the MariaDB parameterized INSERT statement string for the given table.
     */
    public String buildMariaDbInsertSql(Table table) {
        String targetSchema = config.getTargetSchema() != null && !config.getTargetSchema().isBlank()
                ? config.getTargetSchema() : "";
        String targetTable = targetSchema.isEmpty()
                ? "`" + table.getName().toLowerCase() + "`"
                : "`" + targetSchema + "`.`" + table.getName().toLowerCase() + "`";

        List<Column> columns = table.getColumns();
        StringBuilder insertSql = new StringBuilder("INSERT INTO ").append(targetTable).append(" (");
        for (int i = 0; i < columns.size(); i++) {
            insertSql.append("`").append(columns.get(i).getName().toLowerCase()).append("`");
            if (i < columns.size() - 1) insertSql.append(", ");
        }
        insertSql.append(") VALUES (");
        for (int i = 0; i < columns.size(); i++) {
            insertSql.append("?");
            if (i < columns.size() - 1) insertSql.append(", ");
        }
        insertSql.append(")");
        return insertSql.toString();
    }

    private String escapeCsv(String str) {
        return str.replace("\\", "\\\\")
                  .replace("\t", "\\t")
                  .replace("\r", "\\r")
                  .replace("\n", "\\n");
    }
}
