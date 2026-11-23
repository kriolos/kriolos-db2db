package com.zettais.db2db.web.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import com.zettais.db2db.core.i18n.Language;
import com.zettais.db2db.core.model.DatabaseDialect;
import com.zettais.db2db.core.workflow.*;
import com.zettais.db2db.web.dto.StartWorkflowRequest;
import com.zettais.db2db.web.ws.MigrationWebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@ApplicationScoped
public class WorkflowService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowService.class);

    private final MigrationWorkflowOrchestrator orchestrator = new MigrationWorkflowOrchestrator();
    private final WorkflowReportGenerator reportGenerator = new WorkflowReportGenerator();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    void init() {
        orchestrator.addListener(new WorkflowEventListener() {
            @Override
            public void onWorkflowStarted(String migrationId, WorkflowConfig config) {
                broadcastEvent(Map.of(
                        "type", "WORKFLOW_STARTED",
                        "migrationId", migrationId,
                        "mode", config.mode().name()
                ));
            }

            @Override
            public void onStepStatusChanged(MigrationStepId stepId, StepStatus oldStatus, StepStatus newStatus, String message) {
                broadcastEvent(Map.of(
                        "type", "STEP_STATUS_CHANGED",
                        "stepId", stepId.name(),
                        "stepTitle", stepId.getDisplayName(),
                        "oldStatus", oldStatus.name(),
                        "newStatus", newStatus.name(),
                        "message", message != null ? message : ""
                ));
            }

            @Override
            public void onStepLog(MigrationStepId stepId, String logLine) {
                broadcastEvent(Map.of(
                        "type", "STEP_LOG",
                        "stepId", stepId.name(),
                        "logLine", logLine
                ));
            }

            @Override
            public void onWorkflowCompleted(WorkflowExecutionReport report) {
                broadcastEvent(Map.of(
                        "type", "WORKFLOW_COMPLETED",
                        "migrationId", report.migrationId(),
                        "success", report.overallSuccess(),
                        "totalDurationMs", report.totalDurationMs()
                ));
            }

            @Override
            public void onWorkflowFailed(String migrationId, String error) {
                broadcastEvent(Map.of(
                        "type", "WORKFLOW_FAILED",
                        "migrationId", migrationId,
                        "error", error != null ? error : "Unknown error"
                ));
            }
        });
    }

    public MigrationWorkflowOrchestrator.OrchestratorStatus getStatus() {
        return orchestrator.getStatus();
    }

    public void startWorkflow(StartWorkflowRequest req) {
        WorkflowMode mode = "INTERACTIVE".equalsIgnoreCase(req.mode()) ? WorkflowMode.INTERACTIVE : WorkflowMode.AUTOMATIC;
        Set<MigrationStepId> skipped = new HashSet<>();
        if (req.skippedSteps() != null) {
            for (String step : req.skippedSteps()) {
                try {
                    skipped.add(MigrationStepId.valueOf(step.trim().toUpperCase()));
                } catch (IllegalArgumentException ex) {
                    log.warn("Invalid step name to skip: {}", step);
                }
            }
        }

        DatabaseDialect dialect = DatabaseDialect.POSTGRESQL;
        if (req.targetDialect() != null && !req.targetDialect().isBlank()) {
            try {
                dialect = DatabaseDialect.valueOf(req.targetDialect().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                log.warn("Invalid target dialect: {}, falling back to POSTGRESQL", req.targetDialect());
            }
        }

        WorkflowConfig config = WorkflowConfig.builder()
                .sourceDsn(req.sourceDsn())
                .sourceUser(req.sourceUser())
                .sourcePassword(req.sourcePassword())
                .sourceSchema(req.sourceSchema())
                .targetDsn(req.targetDsn())
                .targetUser(req.targetUser())
                .targetPassword(req.targetPassword())
                .targetSchema(req.targetSchema())
                .targetDialect(dialect)
                .mode(mode)
                .skippedSteps(skipped)
                .stopOnError(req.stopOnError() == null || req.stopOnError())
                .parallelWorkers(req.parallelWorkers() != null ? req.parallelWorkers() : 4)
                .build();

        orchestrator.startWorkflow(config);
    }

    public boolean approveCurrentStep() {
        return orchestrator.approveCurrentStep();
    }

    public boolean skipCurrentStep() {
        return orchestrator.skipCurrentStep();
    }

    public boolean cancelWorkflow() {
        return orchestrator.cancelWorkflow();
    }

    public String getHtmlReport() {
        return getHtmlReport(Language.EN);
    }

    public String getHtmlReport(Language lang) {
        var status = orchestrator.getStatus();
        if (status.finalReport() != null) {
            return reportGenerator.toHtml(status.finalReport(), lang);
        }
        return "<html><body><p>Workflow has not produced a final report yet.</p></body></html>";
    }

    public String getJsonReport() throws Exception {
        var status = orchestrator.getStatus();
        if (status.finalReport() != null) {
            return reportGenerator.toJson(status.finalReport());
        }
        return "{}";
    }

    private void broadcastEvent(Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            MigrationWebSocket.broadcast(json);
        } catch (Exception ex) {
            log.warn("Failed serializing WebSocket event", ex);
        }
    }
}
