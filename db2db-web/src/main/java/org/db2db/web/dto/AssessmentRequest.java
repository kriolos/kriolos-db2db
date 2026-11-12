package org.db2db.web.dto;

public class AssessmentRequest {
    private String sourceDsn;
    private String sourceUser;
    private String sourcePassword;
    private String schema;

    public String getSourceDsn() { return sourceDsn; }
    public void setSourceDsn(String sourceDsn) { this.sourceDsn = sourceDsn; }
    public String getSourceUser() { return sourceUser; }
    public void setSourceUser(String sourceUser) { this.sourceUser = sourceUser; }
    public String getSourcePassword() { return sourcePassword; }
    public void setSourcePassword(String sourcePassword) { this.sourcePassword = sourcePassword; }
    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }
}
