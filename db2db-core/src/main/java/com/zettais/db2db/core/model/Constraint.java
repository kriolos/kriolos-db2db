package com.zettais.db2db.core.model;

import java.util.ArrayList;
import java.util.List;

public class Constraint {
    public enum Type {
        PRIMARY_KEY,
        FOREIGN_KEY,
        UNIQUE,
        CHECK
    }

    private String name;
    private Type type;
    private List<String> columns = new ArrayList<>();
    private String referencedTable;
    private List<String> referencedColumns = new ArrayList<>();
    private String checkCondition;
    private boolean deferrable;
    private boolean deferred;

    public Constraint() {}

    public Constraint(String name, Type type) {
        this.name = name;
        this.type = type;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public List<String> getColumns() { return columns; }
    public void setColumns(List<String> columns) { this.columns = columns; }
    public String getReferencedTable() { return referencedTable; }
    public void setReferencedTable(String referencedTable) { this.referencedTable = referencedTable; }
    public List<String> getReferencedColumns() { return referencedColumns; }
    public void setReferencedColumns(List<String> referencedColumns) { this.referencedColumns = referencedColumns; }
    public String getCheckCondition() { return checkCondition; }
    public void setCheckCondition(String checkCondition) { this.checkCondition = checkCondition; }
    public boolean isDeferrable() { return deferrable; }
    public void setDeferrable(boolean deferrable) { this.deferrable = deferrable; }
    public boolean isDeferred() { return deferred; }
    public void setDeferred(boolean deferred) { this.deferred = deferred; }
}
