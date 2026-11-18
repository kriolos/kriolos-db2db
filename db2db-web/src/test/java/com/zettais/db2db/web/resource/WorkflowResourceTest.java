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
}
