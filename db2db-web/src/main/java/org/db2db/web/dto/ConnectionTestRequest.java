package org.db2db.web.dto;

public class ConnectionTestRequest {
    private String sourceDsn;
    private String sourceUser;
    private String sourcePassword;
    private String targetDsn;
    private String targetUser;
    private String targetPassword;

    public String getSourceDsn() { return sourceDsn; }
    public void setSourceDsn(String sourceDsn) { this.sourceDsn = sourceDsn; }
    public String getSourceUser() { return sourceUser; }
    public void setSourceUser(String sourceUser) { this.sourceUser = sourceUser; }
    public String getSourcePassword() { return sourcePassword; }
    public void setSourcePassword(String sourcePassword) { this.sourcePassword = sourcePassword; }
    public String getTargetDsn() { return targetDsn; }
    public void setTargetDsn(String targetDsn) { this.targetDsn = targetDsn; }
    public String getTargetUser() { return targetUser; }
    public void setTargetUser(String targetUser) { this.targetUser = targetUser; }
    public String getTargetPassword() { return targetPassword; }
    public void setTargetPassword(String targetPassword) { this.targetPassword = targetPassword; }
}
