package org.db2db.core.model;

import java.util.ArrayList;
import java.util.List;

public class Table {
    private String schema;
    private String name;
    private List<Column> columns = new ArrayList<>();
    private List<Constraint> constraints = new ArrayList<>();
    private List<Index> indexes = new ArrayList<>();
    private PartitionInfo partitionInfo;
    private long estimatedRowCount;
    private String comment;

    public Table() {}

    public Table(String schema, String name) {
        this.schema = schema;
        this.name = name;
    }

    public void addColumn(Column column) {
        this.columns.add(column);
    }

    public void addConstraint(Constraint constraint) {
        this.constraints.add(constraint);
    }

    public void addIndex(Index index) {
        this.indexes.add(index);
    }

    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<Column> getColumns() { return columns; }
    public void setColumns(List<Column> columns) { this.columns = columns; }
    public List<Constraint> getConstraints() { return constraints; }
    public void setConstraints(List<Constraint> constraints) { this.constraints = constraints; }
    public List<Index> getIndexes() { return indexes; }
    public void setIndexes(List<Index> indexes) { this.indexes = indexes; }
    public PartitionInfo getPartitionInfo() { return partitionInfo; }
    public void setPartitionInfo(PartitionInfo partitionInfo) { this.partitionInfo = partitionInfo; }
    public long getEstimatedRowCount() { return estimatedRowCount; }
    public void setEstimatedRowCount(long estimatedRowCount) { this.estimatedRowCount = estimatedRowCount; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
