package com.zettais.db2db.core.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Generates JSON and standalone executive HTML audit reports for DB2DB migration workflows.
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
        String startedStr = report.startedAtEpochMs() > 0 ? ISO_FMT.format(Instant.ofEpochMilli(report.startedAtEpochMs())) : "N/A";
        String completedStr = report.completedAtEpochMs() > 0 ? ISO_FMT.format(Instant.ofEpochMilli(report.completedAtEpochMs())) : "In Progress";
        double durationSec = report.totalDurationMs() / 1000.0;

        String statusBadgeClass = report.overallSuccess() ? "bg-emerald-500/20 text-emerald-400 border-emerald-500/30"
                : "bg-rose-500/20 text-rose-400 border-rose-500/30";
        String statusText = report.overallSuccess() ? "SUCCESSFUL MIGRATION" : "ATTENTION REQUIRED";

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

            stepsHtml.append("""
                    <div class="border border-zinc-800 rounded-lg p-4 bg-zinc-900/60 transition-all hover:border-zinc-700 mb-3">
                        <div class="flex items-center justify-between">
                            <div class="flex items-center gap-3">
                                <span class="w-8 h-8 rounded-full flex items-center justify-center font-bold text-sm border %s">
                                    %s
                                </span>
                                <div>
                                    <h4 class="font-semibold text-white text-base">%s</h4>
                                    <p class="text-xs text-zinc-400">%s</p>
                                </div>
                            </div>
                            <div class="text-right">
                                <span class="px-2.5 py-1 text-xs font-bold rounded-full border %s uppercase tracking-wider">
                                    %s
                                </span>
                                <div class="text-xs font-mono text-zinc-500 mt-1">%.2f sec</div>
                            </div>
                        </div>
                        %s
                        %s
                        %s
                    </div>
                    """.formatted(
                    stepStatusClass,
                    stepIcon,
                    escapeHtml(step.title()),
                    escapeHtml(step.stepId().getDescription()),
                    stepStatusClass,
                    step.status().name(),
                    step.durationMs() / 1000.0,
                    step.errorMessage() != null ? "<div class=\"mt-2 p-2 rounded bg-rose-950/40 border border-rose-800/50 text-rose-300 text-xs font-mono\">" + escapeHtml(step.errorMessage()) + "</div>" : "",
                    metricsHtml.toString(),
                    logLinesHtml.length() > 0 ? "<div class=\"mt-3 pt-3 border-t border-zinc-800/80 bg-zinc-950/60 p-3 rounded max-h-48 overflow-y-auto\">" + logLinesHtml + "</div>" : ""
            ));
        }

        return """
                <!DOCTYPE html>
                <html lang="en" class="dark">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>DB2DB by ZettaIS - Migration Audit Report</title>
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
                        body { background-color: #09090b; color: #f4f4f5; font-family: 'Plus Jakarta Sans', sans-serif; }
                        ::-webkit-scrollbar { width: 6px; height: 6px; }
                        ::-webkit-scrollbar-track { background: #18181b; }
                        ::-webkit-scrollbar-thumb { background: #3f3f46; border-radius: 3px; }
                    </style>
                </head>
                <body class="min-h-screen antialiased py-8 px-4 sm:px-6 lg:px-8">
                    <div class="max-w-6xl mx-auto space-y-8">
                        
                        <!-- Header Banner -->
                        <div class="border border-zinc-800 rounded-xl p-6 bg-zinc-900/80 backdrop-blur-md shadow-2xl relative overflow-hidden">
                            <div class="absolute -right-12 -top-12 w-64 h-64 bg-cyan-500/10 rounded-full blur-3xl pointer-events-none"></div>
                            <div class="flex flex-col md:flex-row md:items-center justify-between gap-4 relative z-10">
                                <div>
                                    <div class="flex items-center gap-2 mb-2">
                                        <span class="px-2.5 py-0.5 rounded text-xs font-bold uppercase tracking-wider bg-cyan-500/20 text-cyan-400 border border-cyan-500/30">
                                            DB2DB by ZettaIS
                                        </span>
                                        <span class="text-xs text-zinc-500 font-mono">ID: %s</span>
                                    </div>
                                    <h1 class="text-3xl font-extrabold tracking-tight text-white">End-to-End Migration Audit Report</h1>
                                    <p class="text-sm text-zinc-400 mt-1">
                                        Source: <span class="text-cyan-300 font-mono">%s (%s)</span> &rarr; Target: <span class="text-emerald-300 font-mono">%s (%s)</span>
                                    </p>
                                </div>
                                <div class="flex flex-col items-end justify-center">
                                    <span class="px-4 py-1.5 rounded-full text-xs font-bold border tracking-wider %s">
                                        %s
                                    </span>
                                    <span class="text-xs text-zinc-400 font-mono mt-2">Mode: %s</span>
                                </div>
                            </div>
                        </div>

                        <!-- KPI Stat Cards -->
                        <div class="grid grid-cols-2 sm:grid-cols-4 gap-4">
                            <div class="border border-zinc-800 rounded-xl p-4 bg-zinc-900/50">
                                <div class="text-xs text-zinc-400 font-medium">Total Steps</div>
                                <div class="text-2xl font-bold text-white mt-1">%d</div>
                                <div class="text-xs text-zinc-500 mt-1">Lifecycle Phases</div>
                            </div>
                            <div class="border border-zinc-800 rounded-xl p-4 bg-zinc-900/50">
                                <div class="text-xs text-emerald-400 font-medium">Successful</div>
                                <div class="text-2xl font-bold text-emerald-400 mt-1">%d</div>
                                <div class="text-xs text-zinc-500 mt-1">100%% Completed</div>
                            </div>
                            <div class="border border-zinc-800 rounded-xl p-4 bg-zinc-900/50">
                                <div class="text-xs text-slate-400 font-medium">Skipped</div>
                                <div class="text-2xl font-bold text-slate-300 mt-1">%d</div>
                                <div class="text-xs text-zinc-500 mt-1">Configured by Rule</div>
                            </div>
                            <div class="border border-zinc-800 rounded-xl p-4 bg-zinc-900/50">
                                <div class="text-xs text-cyan-400 font-medium">Total Duration</div>
                                <div class="text-2xl font-bold text-cyan-400 mt-1">%.2fs</div>
                                <div class="text-xs text-zinc-500 mt-1">%s to %s</div>
                            </div>
                        </div>

                        <!-- Step Execution Lifecycle -->
                        <div class="border border-zinc-800 rounded-xl p-6 bg-zinc-900/40">
                            <div class="flex items-center justify-between mb-6">
                                <div>
                                    <h2 class="text-xl font-bold text-white">Execution Timeline & Step Audits</h2>
                                    <p class="text-xs text-zinc-400">Step-by-step audit trail, performance metrics, and log outputs</p>
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
                escapeHtml(report.migrationId()),
                escapeHtml(report.sourceDatabase()),
                escapeHtml(report.sourceSchema()),
                escapeHtml(report.targetDatabase()),
                escapeHtml(report.targetSchema()),
                statusBadgeClass,
                statusText,
                report.mode().name(),
                report.stepRecords().size(),
                report.getSuccessCount(),
                report.getSkippedCount(),
                durationSec,
                startedStr,
                completedStr,
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
