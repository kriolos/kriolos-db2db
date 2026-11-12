package org.db2db.core.model;

import java.util.ArrayList;
import java.util.List;

public class PartitionInfo {
    public enum PartitionType {
        RANGE,
        LIST,
        HASH
    }

    private PartitionType type;
    private List<String> partitionColumns = new ArrayList<>();
    private List<PartitionElement> partitions = new ArrayList<>();

    /**
     * Modern Java Record representing a partition element.
     */
    public record PartitionElement(String name, String boundValue, String highValue) {
        public PartitionElement(String name, String boundValue) {
            this(name, boundValue, null);
        }
    }

    public PartitionType getType() { return type; }
    public void setType(PartitionType type) { this.type = type; }
    public List<String> getPartitionColumns() { return partitionColumns; }
    public void setPartitionColumns(List<String> partitionColumns) { this.partitionColumns = partitionColumns; }
    public List<PartitionElement> getPartitions() { return partitions; }
    public void setPartitions(List<PartitionElement> partitions) { this.partitions = partitions; }
}
