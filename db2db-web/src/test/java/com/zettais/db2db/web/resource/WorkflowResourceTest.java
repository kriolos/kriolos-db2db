package com.zettais.db2db.web.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import com.zettais.db2db.web.dto.StartWorkflowRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

@QuarkusTest
class WorkflowResourceTest {

    @Test
    void testGetStatusInitiallyIdle() {
        given()
            .when().get("/api/v1/workflow/status")
            .then()
                .statusCode(200)
                .body("totalSteps", is(12))
                .body("stepRecords.size()", is(12));
    }

    @Test
    void testGetHtmlReport() {
        given()
            .when().get("/api/v1/workflow/report/html")
            .then()
                .statusCode(200)
                .contentType(ContentType.HTML);
    }

    @Test
    void testApproveStepWhenNoStepWaiting() {
        given()
            .contentType(ContentType.JSON)
            .when().post("/api/v1/workflow/step/approve")
            .then()
                .statusCode(200)
                .body("approved", is(false));
    }

    @Test
    void testSkipStepWhenNoStepWaiting() {
        given()
            .contentType(ContentType.JSON)
            .when().post("/api/v1/workflow/step/skip")
            .then()
                .statusCode(200)
                .body("skipped", is(false));
    }

    @Test
    void testStartWorkflowWithMariaDbTarget() {
        var req = new StartWorkflowRequest(
                "jdbc:oracle:thin:@localhost:1521/XE",
                "SYSTEM",
                "secret",
                "HR",
                "jdbc:mariadb://localhost:3306/hr_target",
                "mariadb",
                "secret",
                "hr_target",
                "MARIADB",
                "AUTOMATIC",
                List.of("PRE_FLIGHT_CHECK", "SCHEMA_ASSESSMENT", "PRE_SCHEMA_SETUP", "DDL_EXPORT", "DDL_APPLY",
                        "PARALLEL_DATA_STREAMING", "POST_DATA_INDEXES", "FOREIGN_KEYS", "PROCEDURAL_CODE",
                        "SEQUENCE_SYNC", "DATA_VALIDATION_AUDIT", "FINAL_AUDIT_REPORT"),
                false,
                2
        );

        given()
            .contentType(ContentType.JSON)
            .body(req)
            .when().post("/api/v1/workflow/start")
            .then()
                .statusCode(200)
                .body("status", is("STARTED"));
    }
}
