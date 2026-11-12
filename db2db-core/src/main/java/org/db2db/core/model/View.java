package org.db2db.core.model;

public class View {
    private String schema;
    private String name;
    private String definition;
    private boolean materialized;

    public View() {}

    public View(String schema, String name, String definition, boolean materialized) {
        this.schema = schema;
        this.name = name;
        this.definition = definition;
        this.materialized = materialized;
    }

    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDefinition() { return definition; }
    public void setDefinition(String definition) { this.definition = definition; }
    public boolean isMaterialized() { return materialized; }
    public void setMaterialized(boolean materialized) { this.materialized = materialized; }
}
