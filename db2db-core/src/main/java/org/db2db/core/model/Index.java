package org.db2db.core.model;

import java.util.ArrayList;
import java.util.List;

public class Index {
    public enum IndexType {
        NORMAL,
        BITMAP,
        REVERSE,
        UNIQUE,
        FUNCTION_BASED
    }

    private String name;
    private String tableName;
    private IndexType type = IndexType.NORMAL;
    private boolean unique;
    private List<String> columns = new ArrayList<>();
    private String expression;
    private String tablespace;

    public Index() {}

    public Index(String name, String tableName, boolean unique) {
        this.name = name;
        this.tableName = tableName;
        this.unique = unique;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public IndexType getType() { return type; }
    public void setType(IndexType type) { this.type = type; }
    public boolean isUnique() { return unique; }
    public void setUnique(boolean unique) { this.unique = unique; }
    public List<String> getColumns() { return columns; }
    public void setColumns(List<String> columns) { this.columns = columns; }
    public String getExpression() { return expression; }
    public void setExpression(String expression) { this.expression = expression; }
    public String getTablespace() { return tablespace; }
    public void setTablespace(String tablespace) { this.tablespace = tablespace; }
}
