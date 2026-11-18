package com.zettais.db2db.web.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import com.zettais.db2db.core.i18n.Language;
import com.zettais.db2db.core.workflow.MigrationWorkflowOrchestrator;
import com.zettais.db2db.web.dto.StartWorkflowRequest;
import com.zettais.db2db.web.service.WorkflowService;

import java.util.Map;

@Path("/api/v1/workflow")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WorkflowResource {

    @Inject
    WorkflowService workflowService;

    @POST
    @Path("/start")
    public Response startWorkflow(StartWorkflowRequest request) {
        if (request == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Request body cannot be empty"))
                    .build();
        }

        try {
            workflowService.startWorkflow(request);
            return Response.ok(Map.of(
                    "status", "STARTED",
                    "mode", request.mode() != null ? request.mode() : "AUTOMATIC"
            )).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/status")
    public Response getStatus() {
        MigrationWorkflowOrchestrator.OrchestratorStatus status = workflowService.getStatus();
        return Response.ok(status).build();
    }

    @POST
    @Path("/step/approve")
    public Response approveStep() {
        boolean approved = workflowService.approveCurrentStep();
        return Response.ok(Map.of("approved", approved)).build();
    }

    @POST
    @Path("/step/skip")
    public Response skipStep() {
        boolean skipped = workflowService.skipCurrentStep();
        return Response.ok(Map.of("skipped", skipped)).build();
    }

    @POST
    @Path("/cancel")
    public Response cancelWorkflow() {
        boolean cancelled = workflowService.cancelWorkflow();
        return Response.ok(Map.of("cancelled", cancelled)).build();
    }

    @GET
    @Path("/report/html")
    @Produces(MediaType.TEXT_HTML)
    public Response getHtmlReport(@CookieParam(I18nResource.COOKIE_NAME) String cookieLang,
                                 @QueryParam("lang") String queryLang) {
        var lang = Language.fromString(queryLang != null && !queryLang.isBlank() ? queryLang : cookieLang);
        String html = workflowService.getHtmlReport(lang);
        return Response.ok(html).build();
    }

    @GET
    @Path("/report/json")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getJsonReport() {
        try {
            String json = workflowService.getJsonReport();
            return Response.ok(json).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }
}
