package com.zettais.db2db.core.spatial;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-performance Oracle SDO_GEOMETRY to PostGIS spatial converter for DB2DB by ZettaIS.
 */
public class SdoGeometryConverter {

    private static final Pattern SDO_REGEX = Pattern.compile(
            "(?i)SDO_GEOMETRY\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+|NULL)?\\s*,\\s*(?:SDO_POINT_TYPE\\s*\\(\\s*([-\\d.]+)\\s*,\\s*([-\\d.]+)\\s*(?:,\\s*([-\\d.]+|NULL))?\\s*\\)|NULL)?\\s*,\\s*(?:SDO_ELEM_INFO_ARRAY\\s*\\(([^)]*)\\)|NULL)?\\s*,\\s*(?:SDO_ORDINATE_ARRAY\\s*\\(([^)]*)\\)|NULL)?\\s*\\)"
    );

    /**
     * Converts SDO_GEOMETRY to OGC Well-Known Text (WKT).
     */
    public String toWkt(SdoGeometry geom) {
        if (geom == null) {
            return null;
        }

        var dim = geom.getDimension();
        var gtype = geom.getGeometryType();
        var dimSuffix = (dim == 3) ? " Z " : (dim == 4) ? " ZM " : " ";

        return switch (gtype) {
            case 1 -> formatPoint(geom, dim);
            case 2 -> formatLineString(geom, dim, dimSuffix);
            case 3 -> formatPolygon(geom, dim, dimSuffix);
            case 5 -> formatMultiPoint(geom, dim, dimSuffix);
            case 6 -> formatMultiLineString(geom, dim, dimSuffix);
            case 7 -> formatMultiPolygon(geom, dim, dimSuffix);
            default -> formatPoint(geom, dim);
        };
    }

    /**
     * Converts SDO_GEOMETRY to PostGIS Extended Well-Known Text (EWKT) including SRID.
     */
    public String toEwkt(SdoGeometry geom) {
        if (geom == null) return null;
        var wkt = toWkt(geom);
        if (geom.srid() != null && geom.srid() > 0) {
            return "SRID=" + geom.srid() + ";" + wkt;
        }
        return wkt;
    }

    /**
     * Wraps the geometry into a PostGIS SQL function call.
     */
    public String toPostGisSql(SdoGeometry geom) {
        if (geom == null) return "NULL";
        var ewkt = toEwkt(geom);
        return "ST_GeomFromEWKT('" + ewkt + "')";
    }

    /**
     * Parses an Oracle SDO_GEOMETRY SQL literal expression into an SdoGeometry object.
     */
    public SdoGeometry parseSdoGeometrySql(String sql) {
        if (sql == null || sql.isBlank()) return null;

        Matcher m = SDO_REGEX.matcher(sql.trim());
        if (!m.find()) return null;

        int gtype = Integer.parseInt(m.group(1));
        Integer srid = (m.group(2) != null && !m.group(2).equalsIgnoreCase("NULL")) ? Integer.parseInt(m.group(2)) : null;

        SdoPoint point = null;
        if (m.group(3) != null && m.group(4) != null) {
            Double x = Double.parseDouble(m.group(3));
            Double y = Double.parseDouble(m.group(4));
            Double z = (m.group(5) != null && !m.group(5).equalsIgnoreCase("NULL")) ? Double.parseDouble(m.group(5)) : null;
            point = new SdoPoint(x, y, z);
        }

        int[] elemInfo = parseCsvInts(m.group(6));
        double[] ordinates = parseCsvDoubles(m.group(7));

        return new SdoGeometry(gtype, srid, point, elemInfo, ordinates);
    }

    private String formatPoint(SdoGeometry geom, int dim) {
        if (geom.point() != null) {
            if (dim >= 3 && geom.point().z() != null) {
                return "POINT Z (" + geom.point().x() + " " + geom.point().y() + " " + geom.point().z() + ")";
            }
            return "POINT (" + geom.point().x() + " " + geom.point().y() + ")";
        }
        if (geom.ordinates() != null && geom.ordinates().length >= dim) {
            if (dim >= 3) {
                return "POINT Z (" + geom.ordinates()[0] + " " + geom.ordinates()[1] + " " + geom.ordinates()[2] + ")";
            }
            return "POINT (" + geom.ordinates()[0] + " " + geom.ordinates()[1] + ")";
        }
        return "POINT EMPTY";
    }

    private String formatLineString(SdoGeometry geom, int dim, String suffix) {
        if (geom.ordinates() == null || geom.ordinates().length < dim) return "LINESTRING EMPTY";
        return "LINESTRING" + suffix + "(" + formatCoordinates(geom.ordinates(), 0, geom.ordinates().length, dim) + ")";
    }

    private String formatPolygon(SdoGeometry geom, int dim, String suffix) {
        if (geom.ordinates() == null || geom.ordinates().length < dim) return "POLYGON EMPTY";

        // Check for Rectangle interpretation (ELEM_INFO: offset, 1003, 3)
        if (geom.elemInfo() != null && geom.elemInfo().length >= 3) {
            int etype = geom.elemInfo()[1];
            int interpretation = geom.elemInfo()[2];
            if ((etype == 1003 || etype == 3) && interpretation == 3 && geom.ordinates().length >= 4) {
                double x1 = geom.ordinates()[0];
                double y1 = geom.ordinates()[1];
                double x2 = geom.ordinates()[2];
                double y2 = geom.ordinates()[3];
                // 5-point closed box
                return "POLYGON" + suffix + "((" + x1 + " " + y1 + ", " + x2 + " " + y1 + ", " +
                        x2 + " " + y2 + ", " + x1 + " " + y2 + ", " + x1 + " " + y1 + "))";
            }
        }

        // Standard polygon ring(s)
        List<String> rings = new ArrayList<>();
        if (geom.elemInfo() != null && geom.elemInfo().length >= 3) {
            for (int i = 0; i < geom.elemInfo().length; i += 3) {
                int startOffset = geom.elemInfo()[i] - 1; // 1-based index in Oracle
                int nextOffset = (i + 3 < geom.elemInfo().length) ? (geom.elemInfo()[i + 3] - 1) : geom.ordinates().length;
                rings.add("(" + formatCoordinates(geom.ordinates(), startOffset, nextOffset, dim) + ")");
            }
        } else {
            rings.add("(" + formatCoordinates(geom.ordinates(), 0, geom.ordinates().length, dim) + ")");
        }

        return "POLYGON" + suffix + "(" + String.join(", ", rings) + ")";
    }

    private String formatMultiPoint(SdoGeometry geom, int dim, String suffix) {
        if (geom.ordinates() == null || geom.ordinates().length < dim) return "MULTIPOINT EMPTY";
        List<String> pts = new ArrayList<>();
        for (int i = 0; i < geom.ordinates().length; i += dim) {
            pts.add("(" + formatCoordinates(geom.ordinates(), i, i + dim, dim) + ")");
        }
        return "MULTIPOINT" + suffix + "(" + String.join(", ", pts) + ")";
    }

    private String formatMultiLineString(SdoGeometry geom, int dim, String suffix) {
        if (geom.ordinates() == null || geom.ordinates().length < dim) return "MULTILINESTRING EMPTY";
        List<String> lines = new ArrayList<>();
        if (geom.elemInfo() != null && geom.elemInfo().length >= 3) {
            for (int i = 0; i < geom.elemInfo().length; i += 3) {
                int start = geom.elemInfo()[i] - 1;
                int end = (i + 3 < geom.elemInfo().length) ? (geom.elemInfo()[i + 3] - 1) : geom.ordinates().length;
                lines.add("(" + formatCoordinates(geom.ordinates(), start, end, dim) + ")");
            }
        } else {
            lines.add("(" + formatCoordinates(geom.ordinates(), 0, geom.ordinates().length, dim) + ")");
        }
        return "MULTILINESTRING" + suffix + "(" + String.join(", ", lines) + ")";
    }

    private String formatMultiPolygon(SdoGeometry geom, int dim, String suffix) {
        if (geom.ordinates() == null || geom.ordinates().length < dim) return "MULTIPOLYGON EMPTY";
        return "MULTIPOLYGON" + suffix + "(((" + formatCoordinates(geom.ordinates(), 0, geom.ordinates().length, dim) + ")))";
    }

    private String formatCoordinates(double[] ords, int start, int end, int dim) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end && i < ords.length; i += dim) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(ords[i]).append(" ").append(ords[i + 1]);
            if (dim >= 3 && i + 2 < ords.length) {
                sb.append(" ").append(ords[i + 2]);
            }
        }
        return sb.toString();
    }

    private int[] parseCsvInts(String csv) {
        if (csv == null || csv.isBlank()) return new int[0];
        String[] parts = csv.split("\\s*,\\s*");
        int[] arr = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            arr[i] = Integer.parseInt(parts[i].trim());
        }
        return arr;
    }

    private double[] parseCsvDoubles(String csv) {
        if (csv == null || csv.isBlank()) return new double[0];
        String[] parts = csv.split("\\s*,\\s*");
        double[] arr = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            arr[i] = Double.parseDouble(parts[i].trim());
        }
        return arr;
    }
}
