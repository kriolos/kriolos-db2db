package org.db2db.core.spatial;

/**
 * Modern Java Record representing an Oracle SDO_GEOMETRY object:
 * SDO_GEOMETRY(SDO_GTYPE, SDO_SRID, SDO_POINT, SDO_ELEM_INFO, SDO_ORDINATES).
 */
public record SdoGeometry(
        int gtype,
        Integer srid,
        SdoPoint point,
        int[] elemInfo,
        double[] ordinates
) {
    public SdoGeometry(int gtype, Integer srid, SdoPoint point) {
        this(gtype, srid, point, null, null);
    }

    public SdoGeometry(int gtype, Integer srid, int[] elemInfo, double[] ordinates) {
        this(gtype, srid, null, elemInfo, ordinates);
    }

    /**
     * Extracts dimension D from SDO_GTYPE (DLTT format).
     */
    public int getDimension() {
        int d = gtype / 1000;
        return d > 0 ? d : 2;
    }

    /**
     * Extracts Linear Referencing System L from SDO_GTYPE.
     */
    public int getLrs() {
        return (gtype % 1000) / 100;
    }

    /**
     * Extracts OGC geometry type TT from SDO_GTYPE.
     */
    public int getGeometryType() {
        return gtype % 100;
    }
}
