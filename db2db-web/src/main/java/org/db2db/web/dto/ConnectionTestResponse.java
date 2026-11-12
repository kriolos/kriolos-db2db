package org.db2db.web.dto;

public class ConnectionTestResponse {
    private boolean sourceOk;
    private String sourceMessage;
    private boolean targetOk;
    private String targetMessage;

    public boolean isSourceOk() { return sourceOk; }
    public void setSourceOk(boolean sourceOk) { this.sourceOk = sourceOk; }
    public String getSourceMessage() { return sourceMessage; }
    public void setSourceMessage(String sourceMessage) { this.sourceMessage = sourceMessage; }
    public boolean isTargetOk() { return targetOk; }
    public void setTargetOk(boolean targetOk) { this.targetOk = targetOk; }
    public String getTargetMessage() { return targetMessage; }
    public void setTargetMessage(String targetMessage) { this.targetMessage = targetMessage; }
}
