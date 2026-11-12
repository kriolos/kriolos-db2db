package org.db2db.core.model;

import java.util.Objects;

public class Column {
    private String name;
    private String originalType;
    private String targetType;
    private int precision;
    private int scale;
    private int length;
    private boolean nullable;
    private String defaultValue;
    private String comment;
    private boolean isIdentity;

    public Column() {}

    public Column(String name, String originalType, boolean nullable) {
        this.name = name;
        this.originalType = originalType;
        this.nullable = nullable;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getOriginalType() { return originalType; }
    public void setOriginalType(String originalType) { this.originalType = originalType; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public int getPrecision() { return precision; }
    public void setPrecision(int precision) { this.precision = precision; }
    public int getScale() { return scale; }
    public void setScale(int scale) { this.scale = scale; }
    public int getLength() { return length; }
    public void setLength(int length) { this.length = length; }
    public boolean isNullable() { return nullable; }
    public void setNullable(boolean nullable) { this.nullable = nullable; }
    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public boolean isIdentity() { return isIdentity; }
    public void setIdentity(boolean identity) { isIdentity = identity; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Column column)) return false;
        return Objects.equals(name, column.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
