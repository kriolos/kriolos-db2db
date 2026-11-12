package org.db2db.web.dto;

public class TranspileResponse {
    private String originalSql;
    private String transpiledSql;
    private long executionTimeMs;

    public TranspileResponse() {}

    public TranspileResponse(String originalSql, String transpiledSql, long executionTimeMs) {
        this.originalSql = originalSql;
        this.transpiledSql = transpiledSql;
        this.executionTimeMs = executionTimeMs;
    }

    public String getOriginalSql() { return originalSql; }
    public void setOriginalSql(String originalSql) { this.originalSql = originalSql; }
    public String getTranspiledSql() { return transpiledSql; }
    public void setTranspiledSql(String transpiledSql) { this.transpiledSql = transpiledSql; }
    public long getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; }
}
