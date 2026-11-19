package com.zettais.db2db.core.spatial;

/**
 * Modern Java Record representing an Oracle SDO_POINT_TYPE object.
 */
public record SdoPoint(Double x, Double y, Double z) {
    public SdoPoint(Double x, Double y) {
        this(x, y, null);
    }
}
