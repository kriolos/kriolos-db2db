package com.zettais.db2db.core.model;

import java.util.ArrayList;
import java.util.List;

public class DatabaseCatalog {
    private String schemaName;
    private DatabaseDialect dialect = DatabaseDialect.ORACLE;
    private List<Table> tables = new ArrayList<>();
    private List<Sequence> sequences = new ArrayList<>();
    private List<View> views = new ArrayList<>();
    private List<Trigger> triggers = new ArrayList<>();
    private List<PlSqlUnit> plSqlUnits = new ArrayList<>();
    private List<String> invalidObjects = new ArrayList<>();

    public DatabaseCatalog() {}

    public DatabaseCatalog(String schemaName) {
        this.schemaName = schemaName;
    }

    public String getSchemaName() { return schemaName; }
    public void setSchemaName(String schemaName) { this.schemaName = schemaName; }
    public DatabaseDialect getDialect() { return dialect; }
    public void setDialect(DatabaseDialect dialect) { this.dialect = dialect; }
    public List<Table> getTables() { return tables; }
    public void setTables(List<Table> tables) { this.tables = tables; }
    public List<Sequence> getSequences() { return sequences; }
    public void setSequences(List<Sequence> sequences) { this.sequences = sequences; }
    public List<View> getViews() { return views; }
    public void setViews(List<View> views) { this.views = views; }
    public List<Trigger> getTriggers() { return triggers; }
    public void setTriggers(List<Trigger> triggers) { this.triggers = triggers; }
    public List<PlSqlUnit> getPlSqlUnits() { return plSqlUnits; }
    public void setPlSqlUnits(List<PlSqlUnit> plSqlUnits) { this.plSqlUnits = plSqlUnits; }
    public List<String> getInvalidObjects() { return invalidObjects; }
    public void setInvalidObjects(List<String> invalidObjects) { this.invalidObjects = invalidObjects; }

    public void addTable(Table table) { this.tables.add(table); }
    public void addSequence(Sequence sequence) { this.sequences.add(sequence); }
    public void addView(View view) { this.views.add(view); }
    public void addTrigger(Trigger trigger) { this.triggers.add(trigger); }
    public void addPlSqlUnit(PlSqlUnit unit) { this.plSqlUnits.add(unit); }
}
