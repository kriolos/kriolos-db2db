package com.zettais.db2db.core.model;

public class PlSqlUnit {
    public enum UnitType {
        PACKAGE_SPEC,
        PACKAGE_BODY,
        PROCEDURE,
        FUNCTION,
        TYPE_SPEC,
        TYPE_BODY
    }

    private String schema;
    private String name;
    private UnitType type;
    private String sourceCode;
    private int lineCount;
    private boolean valid = true;

    public PlSqlUnit() {}

    public PlSqlUnit(String schema, String name, UnitType type, String sourceCode) {
        this.schema = schema;
        this.name = name;
        this.type = type;
        this.sourceCode = sourceCode;
        if (sourceCode != null) {
            this.lineCount = sourceCode.split("\r\n|\r|\n").length;
        }
    }

    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public UnitType getType() { return type; }
    public void setType(UnitType type) { this.type = type; }
    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
        if (sourceCode != null) {
            this.lineCount = sourceCode.split("\r\n|\r|\n").length;
        }
    }
    public int getLineCount() { return lineCount; }
    public void setLineCount(int lineCount) { this.lineCount = lineCount; }
    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }
}
