package org.db2db.web.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.db2db.web.dto.TranspileRequest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
class MigrationResourceTest {

    @Test
    void testHealthEndpoint() {
        given()
            .when().get("/api/v1/health")
            .then()
                .statusCode(200)
                .body("status", is("UP"))
                .body("engine", is("db2db Enterprise Migration Platform"))
                .body("virtualThreads", is("supported"));
    }

    @Test
    void testTranspileEndpoint() {
        var req = new TranspileRequest("SELECT NVL(val, 0) FROM DUAL;");

        given()
            .contentType(ContentType.JSON)
            .body(req)
            .when().post("/api/v1/transpile")
            .then()
                .statusCode(200)
                .body("transpiledSql", containsString("COALESCE(val, 0)"));
    }
}
