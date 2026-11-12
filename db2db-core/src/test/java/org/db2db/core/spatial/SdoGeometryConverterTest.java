package org.db2db.core.spatial;

import org.db2db.core.transpiler.PlSqlTranspiler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SdoGeometryConverterTest {

    private SdoGeometryConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SdoGeometryConverter();
    }

    @Test
    void test2DPointConversion() {
        SdoGeometry geom = new SdoGeometry(2001, 4326, new SdoPoint(2.3522, 48.8566));
        String wkt = converter.toWkt(geom);
        assertThat(wkt).isEqualTo("POINT (2.3522 48.8566)");

        String ewkt = converter.toEwkt(geom);
        assertThat(ewkt).isEqualTo("SRID=4326;POINT (2.3522 48.8566)");

        String sql = converter.toPostGisSql(geom);
        assertThat(sql).isEqualTo("ST_GeomFromEWKT('SRID=4326;POINT (2.3522 48.8566)')");
    }

    @Test
    void test3DPointConversion() {
        SdoGeometry geom = new SdoGeometry(3001, 4326, new SdoPoint(10.0, 20.0, 350.5));
        String wkt = converter.toWkt(geom);
        assertThat(wkt).isEqualTo("POINT Z (10.0 20.0 350.5)");

        String ewkt = converter.toEwkt(geom);
        assertThat(ewkt).isEqualTo("SRID=4326;POINT Z (10.0 20.0 350.5)");
    }

    @Test
    void testLineStringConversion() {
        double[] coords = new double[]{1.0, 2.0, 3.0, 4.0, 5.0, 6.0};
        SdoGeometry geom = new SdoGeometry(2002, 4326, new int[]{1, 2, 1}, coords);
        String wkt = converter.toWkt(geom);
        assertThat(wkt).isEqualTo("LINESTRING (1.0 2.0, 3.0 4.0, 5.0 6.0)");
    }

    @Test
    void testRectanglePolygonConversion() {
        // Lower-left (1,1) to upper-right (5,7)
        double[] coords = new double[]{1.0, 1.0, 5.0, 7.0};
        int[] elemInfo = new int[]{1, 1003, 3}; // 3 = Rectangle interpretation
        SdoGeometry geom = new SdoGeometry(2003, 4326, elemInfo, coords);

        String wkt = converter.toWkt(geom);
        assertThat(wkt).isEqualTo("POLYGON ((1.0 1.0, 5.0 1.0, 5.0 7.0, 1.0 7.0, 1.0 1.0))");
    }

    @Test
    void testSqlLiteralParsingAndTranspilerIntegration() {
        String sql = "INSERT INTO landmarks (id, geom) VALUES (1, SDO_GEOMETRY(2001, 4326, SDO_POINT_TYPE(-73.9857, 40.7484, NULL), NULL, NULL));";
        PlSqlTranspiler transpiler = new PlSqlTranspiler();
        String transpiled = transpiler.transpile(sql);

        assertThat(transpiled).contains("ST_GeomFromEWKT('SRID=4326;POINT (-73.9857 40.7484)')");
    }
}
