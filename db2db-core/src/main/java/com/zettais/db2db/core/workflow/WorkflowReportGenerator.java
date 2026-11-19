package com.zettais.db2db.core.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zettais.db2db.core.i18n.I18nMessages;
import com.zettais.db2db.core.i18n.Language;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Generates JSON and standalone executive HTML audit reports for DB2DB migration workflows.
 * Fully responsive across mobile, tablet, desktop, and print/PDF views.
 */
public class WorkflowReportGenerator {

    private final ObjectMapper objectMapper;
    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public WorkflowReportGenerator() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    public String toJson(WorkflowExecutionReport report) throws IOException {
        return objectMapper.writeValueAsString(report);
    }

    public String toHtml(WorkflowExecutionReport report) {
        return toHtml(report, Language.EN);
    }

    public String toHtml(WorkflowExecutionReport report, Language lang) {
        var effectiveLang = lang != null ? lang : Language.EN;
        String startedStr = report.startedAtEpochMs() > 0 ? ISO_FMT.format(Instant.ofEpochMilli(report.startedAtEpochMs())) : "N/A";
        String completedStr = report.completedAtEpochMs() > 0 ? ISO_FMT.format(Instant.ofEpochMilli(report.completedAtEpochMs())) : "In Progress";
        double durationSec = report.totalDurationMs() / 1000.0;

        String statusBadgeClass = report.overallSuccess() ? "bg-emerald-500/20 text-emerald-400 border-emerald-500/30"
                : "bg-rose-500/20 text-rose-400 border-rose-500/30";
        String statusText = report.overallSuccess()
                ? I18nMessages.get("report.status.success", effectiveLang)
                : I18nMessages.get("wf.status.attention", effectiveLang);

        StringBuilder stepsHtml = new StringBuilder();
        for (var step : report.stepRecords()) {
            String stepStatusClass = switch (step.status()) {
                case SUCCESS -> "bg-emerald-500/10 text-emerald-400 border-emerald-500/30";
                case SKIPPED -> "bg-slate-500/10 text-slate-400 border-slate-500/30";
                case FAILED -> "bg-rose-500/10 text-rose-400 border-rose-500/30";
                case WAITING_APPROVAL -> "bg-amber-500/10 text-amber-400 border-amber-500/30";
                case RUNNING -> "bg-cyan-500/10 text-cyan-400 border-cyan-500/30 animate-pulse";
                default -> "bg-zinc-800 text-zinc-400 border-zinc-700";
            };

            String stepIcon = switch (step.status()) {
                case SUCCESS -> "✓";
                case SKIPPED -> "⊘";
                case FAILED -> "✗";
                case WAITING_APPROVAL -> "⏳";
                case RUNNING -> "⚙";
                default -> "○";
            };

            StringBuilder logLinesHtml = new StringBuilder();
            for (String logLine : step.logs()) {
                logLinesHtml.append("<div class=\"font-mono text-xs text-zinc-300 py-0.5\">")
                        .append(escapeHtml(logLine))
                        .append("</div>");
            }

            StringBuilder metricsHtml = new StringBuilder();
            if (!step.metrics().isEmpty()) {
                metricsHtml.append("<div class=\"mt-2 flex flex-wrap gap-2\">");
                for (Map.Entry<String, Object> entry : step.metrics().entrySet()) {
                    metricsHtml.append("<span class=\"inline-flex items-center px-2 py-0.5 rounded text-xs font-mono bg-zinc-800 text-zinc-300 border border-zinc-700\">")
                            .append(escapeHtml(entry.getKey())).append(": ").append(escapeHtml(String.valueOf(entry.getValue())))
                            .append("</span>");
                }
                metricsHtml.append("</div>");
            }

            String localizedStepName = step.stepId().getDisplayName(effectiveLang);
            String localizedStepDesc = step.stepId().getDescription(effectiveLang);

            stepsHtml.append("""
                    <div class="border border-zinc-800 rounded-lg p-4 bg-zinc-900/60 transition-all hover:border-zinc-700 mb-3 step-card">
                        <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                            <div class="flex items-center gap-3">
                                <span class="w-8 h-8 rounded-full flex items-center justify-center font-bold text-sm border %s">
                                    %s
                                </span>
                                <div>
                                    <h4 class="font-semibold text-white text-base">%s</h4>
                                    <p class="text-xs text-zinc-400">%s</p>
                                </div>
                            </div>
                            <div class="flex items-center gap-3 self-end sm:self-center">
                                <span class="px-2.5 py-1 text-xs font-bold rounded-full border %s uppercase tracking-wider">
                                    %s
                                </span>
                                <span class="text-xs text-zinc-400 font-mono">%.2fs</span>
                            </div>
                        </div>

                        %s

                        %s
                    </div>
                    """.formatted(
                    stepStatusClass,
                    stepIcon,
                    escapeHtml(localizedStepName),
                    escapeHtml(localizedStepDesc),
                    stepStatusClass,
                    step.status().name(),
                    step.durationMs() / 1000.0,
                    metricsHtml.toString(),
                    logLinesHtml.length() > 0 ? """
                        <div class="mt-3 p-3 rounded bg-zinc-950 border border-zinc-800/80 max-h-48 overflow-y-auto log-container">
                            %s
                        </div>
                        """.formatted(logLinesHtml.toString()) : ""
            ));
        }

        return """
                <!DOCTYPE html>
                <html lang="%s">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>DB2DB by ZettaIS - %s</title>
                    <script src="https://cdn.tailwindcss.com"></script>
                    <link href="https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@400;600&family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap" rel="stylesheet">
                    <script>
                        tailwind.config = {
                            darkMode: 'class',
                            theme: {
                                extend: {
                                    fontFamily: {
                                        sans: ['"Plus Jakarta Sans"', 'sans-serif'],
                                        mono: ['"JetBrains Mono"', 'monospace']
                                    }
                                }
                            }
                        }
                    </script>
                    <style>
                        body { background-color: #09090b; color: #f4f4f5; font-family: 'Plus Jakarta Sans', sans-serif; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
                        ::-webkit-scrollbar { width: 6px; height: 6px; }
                        ::-webkit-scrollbar-track { background: #18181b; }
                        ::-webkit-scrollbar-thumb { background: #3f3f46; border-radius: 3px; }

                        /* Print Mode Styles */
                        @media print {
                            @page {
                                size: A4 portrait;
                                margin: 12mm 10mm;
                            }
                            body {
                                background-color: #ffffff !important;
                                color: #0f172a !important;
                                padding: 0 !important;
                                font-size: 10pt !important;
                            }
                            .no-print {
                                display: none !important;
                            }
                            .backdrop-blur-md, [class*="bg-zinc-900"], [class*="bg-zinc-950"] {
                                background-color: #ffffff !important;
                                color: #0f172a !important;
                                border-color: #cbd5e1 !important;
                                box-shadow: none !important;
                            }
                            [class*="text-white"], [class*="text-zinc-100"] {
                                color: #0f172a !important;
                            }
                            [class*="text-zinc-400"], [class*="text-zinc-500"] {
                                color: #475569 !important;
                            }
                            [class*="border-zinc-800"], [class*="border-zinc-700"] {
                                border-color: #cbd5e1 !important;
                            }
                            [class*="bg-zinc-800"] {
                                background-color: #f1f5f9 !important;
                                color: #334155 !important;
                                border-color: #cbd5e1 !important;
                            }
                            .step-card {
                                background-color: #ffffff !important;
                                border: 1px solid #cbd5e1 !important;
                                page-break-inside: avoid;
                                break-inside: avoid;
                                margin-bottom: 0.75rem !important;
                            }
                            .log-container {
                                background-color: #f8fafc !important;
                                border: 1px solid #e2e8f0 !important;
                                max-height: none !important;
                                overflow: visible !important;
                            }
                            .log-container div {
                                color: #1e293b !important;
                            }
                            .blur-3xl {
                                display: none !important;
                            }
                        }
                    </style>
                </head>
                <body class="min-h-screen antialiased py-8 px-4 sm:px-6 lg:px-8">
                    <div class="max-w-7xl mx-auto space-y-8">
                        
                        <!-- Header Banner -->
                        <div class="border border-zinc-800 rounded-xl p-6 bg-zinc-900/80 backdrop-blur-md shadow-2xl relative overflow-hidden">
                            <div class="absolute -right-12 -top-12 w-64 h-64 bg-cyan-500/10 rounded-full blur-3xl pointer-events-none"></div>
                            <div class="flex flex-col md:flex-row md:items-center justify-between gap-4 relative z-10">
                                <div>
                                    <div class="flex items-center gap-2 mb-2">
                                        <span class="px-2.5 py-0.5 rounded text-xs font-bold uppercase tracking-wider bg-cyan-500/20 text-cyan-400 border border-cyan-500/30">
                                            DB2DB by ZettaIS
                                        </span>
                                        <span class="text-xs text-zinc-500 font-mono">%s: %s</span>
                                    </div>
                                    <h1 class="text-2xl sm:text-3xl font-extrabold tracking-tight text-white">%s</h1>
                                    <p class="text-sm text-zinc-400 mt-1">
                                        %s: <span class="text-cyan-300 font-mono">%s (%s)</span> &rarr; %s: <span class="text-emerald-300 font-mono">%s (%s)</span>
                                    </p>
                                </div>
                                <div class="flex flex-col sm:flex-row items-start sm:items-center gap-3">
                                    <button onclick="window.print()" class="no-print inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold bg-zinc-800 hover:bg-zinc-700 text-zinc-200 border border-zinc-700 shadow-sm transition">
                                        🖨️ %s
                                    </button>
                                    <div class="flex flex-col items-start sm:items-end justify-center">
                                        <span class="px-4 py-1.5 rounded-full text-xs font-bold border tracking-wider %s">
                                            %s
                                        </span>
                                        <span class="text-xs text-zinc-400 font-mono mt-1">%s: %s</span>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <!-- KPI Stat Cards -->
                        <div class="grid grid-cols-2 sm:grid-cols-4 gap-4">
                            <div class="border border-zinc-800 rounded-xl p-4 bg-zinc-900/50">
                                <div class="text-xs text-zinc-400 font-medium">%s</div>
                                <div class="text-2xl font-bold text-white mt-1">%d</div>
                                <div class="text-xs text-zinc-500 mt-1">Lifecycle Phases</div>
                            </div>
                            <div class="border border-zinc-800 rounded-xl p-4 bg-zinc-900/50">
                                <div class="text-xs text-emerald-400 font-medium">%s</div>
                                <div class="text-2xl font-bold text-emerald-400 mt-1">%d</div>
                                <div class="text-xs text-zinc-500 mt-1">100%% Completed</div>
                            </div>
                            <div class="border border-zinc-800 rounded-xl p-4 bg-zinc-900/50">
                                <div class="text-xs text-slate-400 font-medium">%s</div>
                                <div class="text-2xl font-bold text-slate-300 mt-1">%d</div>
                                <div class="text-xs text-zinc-500 mt-1">Configured by Rule</div>
                            </div>
                            <div class="border border-zinc-800 rounded-xl p-4 bg-zinc-900/50">
                                <div class="text-xs text-cyan-400 font-medium">%s</div>
                                <div class="text-2xl font-bold text-cyan-400 mt-1">%.2fs</div>
                                <div class="text-xs text-zinc-500 mt-1">%s to %s</div>
                            </div>
                        </div>

                        <!-- Step Execution Lifecycle -->
                        <div class="border border-zinc-800 rounded-xl p-6 bg-zinc-900/40">
                            <div class="flex items-center justify-between mb-6">
                                <div>
                                    <h2 class="text-xl font-bold text-white">%s</h2>
                                    <p class="text-xs text-zinc-400">%s</p>
                                </div>
                                <span class="text-xs text-zinc-500 font-mono">JDK 25 Virtual Threads</span>
                            </div>

                            <div class="space-y-3">
                                %s
                            </div>
                        </div>

                        <!-- Footer -->
                        <div class="pt-6 border-t border-zinc-800 flex flex-col sm:flex-row items-center justify-between text-xs text-zinc-500 gap-2">
                            <div>Generated by <strong>DB2DB by ZettaIS</strong> Enterprise Multi-Dialect Platform</div>
                            <div class="font-mono">JDK 25.0.4 &bull; GraalVM Native Ready &bull; PostgreSQL Certified</div>
                        </div>

                    </div>
                </body>
                </html>
                """.formatted(
                effectiveLang.getCode(),
                I18nMessages.get("report.title", effectiveLang),
                I18nMessages.get("report.migrationId", effectiveLang),
                escapeHtml(report.migrationId()),
                I18nMessages.get("report.title", effectiveLang),
                I18nMessages.get("report.sourceDb", effectiveLang),
                escapeHtml(report.sourceDatabase()),
                escapeHtml(report.sourceSchema()),
                I18nMessages.get("report.targetDb", effectiveLang),
                escapeHtml(report.targetDatabase()),
                escapeHtml(report.targetSchema()),
                I18nMessages.get("report.print", effectiveLang),
                statusBadgeClass,
                statusText,
                I18nMessages.get("report.mode", effectiveLang),
                report.mode().name(),
                I18nMessages.get("report.totalSteps", effectiveLang),
                report.stepRecords().size(),
                I18nMessages.get("report.successful", effectiveLang),
                report.getSuccessCount(),
                I18nMessages.get("report.skipped", effectiveLang),
                report.getSkippedCount(),
                I18nMessages.get("report.totalDuration", effectiveLang),
                durationSec,
                startedStr,
                completedStr,
                I18nMessages.get("report.stepsAudit", effectiveLang),
                I18nMessages.get("report.stepsAuditSub", effectiveLang),
                stepsHtml.toString()
        );
    }

    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
