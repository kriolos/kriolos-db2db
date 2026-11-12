package org.db2db.core.model;

public class Trigger {
    private String schema;
    private String name;
    private String tableName;
    private String triggerType; // BEFORE EACH ROW, AFTER EACH ROW, INSTEAD OF
    private String triggeringEvent; // INSERT, UPDATE, DELETE
    private String condition;
    private String body;

    public Trigger() {}

    public Trigger(String schema, String name, String tableName) {
        this.schema = schema;
        this.name = name;
        this.tableName = tableName;
    }

    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
    public String getTriggeringEvent() { return triggeringEvent; }
    public void setTriggeringEvent(String triggeringEvent) { this.triggeringEvent = triggeringEvent; }
    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
}
