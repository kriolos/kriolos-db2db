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

    public static class PartitionElement {
        private String name;
        private String boundValue; // e.g. LESS THAN (...) or VALUES IN (...)
        private String highValue;

        public PartitionElement(String name, String boundValue) {
            this.name = name;
            this.boundValue = boundValue;
        }

        public String getName() { return name; }
        public String getBoundValue() { return boundValue; }
        public String getHighValue() { return highValue; }
        public void setHighValue(String highValue) { this.highValue = highValue; }
    }

    public PartitionType getType() { return type; }
    public void setType(PartitionType type) { this.type = type; }
    public List<String> getPartitionColumns() { return partitionColumns; }
    public void setPartitionColumns(List<String> partitionColumns) { this.partitionColumns = partitionColumns; }
    public List<PartitionElement> getPartitions() { return partitions; }
    public void setPartitions(List<PartitionElement> partitions) { this.partitions = partitions; }
}
