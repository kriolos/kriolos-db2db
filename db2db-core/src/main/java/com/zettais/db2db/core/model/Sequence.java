package com.zettais.db2db.core.model;

import java.math.BigDecimal;

public class Sequence {
    private String schema;
    private String name;
    private BigDecimal minValue = BigDecimal.ONE;
    private BigDecimal maxValue = new BigDecimal("9223372036854775807");
    private BigDecimal incrementBy = BigDecimal.ONE;
    private BigDecimal lastNumber = BigDecimal.ONE;
    private boolean cycle;
    private int cacheSize = 20;

    public Sequence() {}

    public Sequence(String schema, String name) {
        this.schema = schema;
        this.name = name;
    }

    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getMinValue() { return minValue; }
    public void setMinValue(BigDecimal minValue) { this.minValue = minValue; }
    public BigDecimal getMaxValue() { return maxValue; }
    public void setMaxValue(BigDecimal maxValue) { this.maxValue = maxValue; }
    public BigDecimal getIncrementBy() { return incrementBy; }
    public void setIncrementBy(BigDecimal incrementBy) { this.incrementBy = incrementBy; }
    public BigDecimal getLastNumber() { return lastNumber; }
    public void setLastNumber(BigDecimal lastNumber) { this.lastNumber = lastNumber; }
    public boolean isCycle() { return cycle; }
    public void setCycle(boolean cycle) { this.cycle = cycle; }
    public int getCacheSize() { return cacheSize; }
    public void setCacheSize(int cacheSize) { this.cacheSize = cacheSize; }
}
