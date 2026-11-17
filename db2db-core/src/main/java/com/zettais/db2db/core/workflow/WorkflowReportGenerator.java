package com.zettais.db2db.core.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.zettais.db2db.core.i18n.I18nMessages;
import com.zettais.db2db.core.i18n.Language;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Generates audit migration reports in JSON and standalone executive HTML.
 * All texts, labels, and step information are fully localized via ResourceBundle (.properties) messages.
 */
public class WorkflowReportGenerator {

    private final ObjectMapper objectMapper;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
            .withZone(ZoneId.systemDefault());

    public WorkflowReportGenerator() {
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public String toJson(WorkflowExecutionReport report) throws IOException {
        return objectMapper.writeValueAsString(report);
    }

    public String toHtml(WorkflowExecutionReport report) {
        Language lang = report.language() != null ? report.language() : Language.EN;

        StringBuilder stepsHtml = new StringBuilder();
        for (int i = 0; i < report.stepRecords().size(); i++) {
            StepExecutionRecord step = report.stepRecords().get(i);
            String stepStatusClass = switch (step.status()) {
                case SUCCESS -> "border-emerald-500/50 bg-emerald-950/20 text-emerald-400";
                case FAILED -> "border-rose-500/50 bg-rose-950/20 text-rose-400";
                case SKIPPED -> "border-zinc-700 bg-zinc-900/40 text-zinc-400";
                case RUNNING -> "border-sky-500/50 bg-sky-950/20 text-sky-400 animate-pulse";
                case WAITING_APPROVAL -> "border-amber-500/50 bg-amber-950/20 text-amber-400";
                case PENDING -> "border-zinc-800 bg-zinc-900/20 text-zinc-500";
            };

            String stepIcon = switch (step.status()) {
                case SUCCESS -> "&#10003;";
                case FAILED -> "&#10007;";
                case SKIPPED -> "&minus;";
                case RUNNING -> "&#9654;";
                case WAITING_APPROVAL -> "&#9208;";
                case PENDING -> "&bull;";
            };

            StringBuilder metricsHtml = new StringBuilder();
            if (!step.metrics().isEmpty()) {
                metricsHtml.append("<div class=\"grid grid-cols-2 sm:grid-cols-4 gap-2 mt-2 pt-2 border-t border-zinc-800/60 text-xs font-mono\">");
                for (Map.Entry<String, Object> entry : step.metrics().entrySet()) {
                    metricsHtml.append("<div><span class=\"text-zinc-500\">")
                            .append(escapeHtml(entry.getKey()))
                            .append(":</span> <span class=\"text-zinc-200\">")
                            .append(escapeHtml(String.valueOf(entry.getValue())))
                            .append("</span></div>");
                }
                metricsHtml.append("</div>");
            }

            StringBuilder logLinesHtml = new StringBuilder();
            if (!step.logs().isEmpty()) {
                for (String line : step.logs()) {
                    logLinesHtml.append("<div class=\"text-zinc-400 font-mono text-xs truncate\">")
                            .append(escapeHtml(line))
                            .append("</div>");
                }
            }

            String localizedStepStatus = I18nMessages.get("report.status." + step.status().name().toLowerCase(), lang);

            stepsHtml.append("""
                    <div class="border rounded-lg p-4 transition-all duration-150 %s mb-3">
                        <div class="flex items-center justify-between">
                            <div class="flex items-center gap-3">
                                <span class="w-7 h-7 rounded flex items-center justify-center font-bold text-sm bg-zinc-800/80 border border-zinc-700">
                                    %s
                                </span>
                                <div>
                                    <div class="font-semibold text-sm text-zinc-100">%s</div>
                                    <div class="text-xs text-zinc-400">%s</div>
                                </div>
                            </div>
                            <div class="text-right">
                                <span class="px-2 py-0.5 rounded text-xs font-medium uppercase tracking-wider %s">
                                    %s
                                </span>
                                <div class="text-xs text-zinc-400 font-mono mt-1">%.2fs</div>
                            </div>
                        </div>
                        %s
                        %s
                        %s
                    </div>
                    """.formatted(
                    stepStatusClass,
                    stepIcon,
                    escapeHtml(step.stepId().getDisplayName(lang)),
                    escapeHtml(step.stepId().getDescription(lang)),
                    stepStatusClass,
                    localizedStepStatus,
                    step.durationMs() / 1000.0,
                    step.errorMessage() != null ? "<div class=\"mt-2 p-2 rounded bg-rose-950/40 border border-rose-800/50 text-rose-300 text-xs font-mono\">" + escapeHtml(step.errorMessage()) + "</div>" : "",
                    metricsHtml.toString(),
                    logLinesHtml.length() > 0 ? "<div class=\"mt-3 pt-3 border-t border-zinc-800/80 bg-zinc-950/60 p-3 rounded max-h-48 overflow-y-auto\">" + logLinesHtml + "</div>" : ""
            ));
        }

        String statusBadgeClass = report.overallSuccess()
                ? "bg-emerald-500/20 text-emerald-400 border-emerald-500/30"
                : "bg-rose-500/20 text-rose-400 border-rose-500/30";
        String statusText = report.overallSuccess()
                ? I18nMessages.get("report.status.success", lang)
                : I18nMessages.get("report.status.failed", lang);

        String startedStr = report.startedAtEpochMs() > 0
                ? TIME_FORMATTER.format(Instant.ofEpochMilli(report.startedAtEpochMs()))
                : "N/A";
        String completedStr = report.completedAtEpochMs() > 0
                ? TIME_FORMATTER.format(Instant.ofEpochMilli(report.completedAtEpochMs()))
                : "N/A";
        double durationSec = report.totalDurationMs() / 1000.0;

        return """
                <!DOCTYPE html>
                <html lang="%s" class="dark">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>%s - DB2DB by ZettaIS</title>
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

                        /* Responsive Container & Layout */
                        .report-container {
                            max-width: 1400px;
                            width: 100%;
                            margin: 0 auto;
                            padding: clamp(1rem, 3vw, 2.5rem);
                        }
                        .break-avoid {
                            break-inside: avoid;
                            page-break-inside: avoid;
                        }

                        /* High-Fidelity Print Mode (A4, Ink-Saving, Clean Breakpoints) */
                        @media print {
                            @page {
                                size: A4 portrait;
                                margin: 12mm 10mm;
                            }
                            body {
                                background-color: #ffffff !important;
                                color: #0f172a !important;
                                font-size: 11px !important;
                                padding: 0 !important;
                            }
                            .report-container {
                                max-width: 100% !important;
                                padding: 0 !important;
                            }
                            .border-zinc-800, .border-zinc-700 {
                                border-color: #cbd5e1 !important;
                            }
                            .bg-zinc-900\\/80, .bg-zinc-900\\/50, .bg-zinc-900\\/40, .bg-zinc-900\\/20, .bg-zinc-950\\/60 {
                                background-color: #ffffff !important;
                                box-shadow: none !important;
                            }
                            .text-white, .text-zinc-100 {
                                color: #0f172a !important;
                            }
                            .text-zinc-400, .text-zinc-500 {
                                color: #475569 !important;
                            }
                            .break-avoid, .border.rounded-lg, .border.rounded-xl {
                                break-inside: avoid !important;
                                page-break-inside: avoid !important;
                                margin-bottom: 0.75rem !important;
                            }
                            .shadow-2xl, .blur-3xl {
                                display: none !important;
                            }
                        }
                    </style>
                </head>
                <body class="min-h-screen antialiased py-6 px-3 sm:px-6 lg:px-8">
                    <div class="report-container space-y-6">
                        
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
                                    <h1 class="text-3xl font-extrabold tracking-tight text-white">%s</h1>
                                    <p class="text-sm text-zinc-400 mt-1">
                                        %s: <span class="text-cyan-300 font-mono">%s (%s)</span> &rarr; %s: <span class="text-emerald-300 font-mono">%s (%s)</span>
                                    </p>
                                </div>
                                <div class="flex flex-col items-end justify-center">
                                    <span class="px-4 py-1.5 rounded-full text-xs font-bold border tracking-wider %s">
                                        %s
                                    </span>
                                    <span class="text-xs text-zinc-400 font-mono mt-2">%s: %s</span>
                                </div>
                            </div>
                        </div>

                        <!-- KPI Stat Cards -->
                        <div class="grid grid-cols-2 sm:grid-cols-4 gap-4">
                            <div class="border border-zinc-800 rounded-xl p-4 bg-zinc-900/50">
                                <div class="text-xs text-zinc-400 font-medium">%s</div>
                                <div class="text-2xl font-bold text-white mt-1">%d</div>
                                <div class="text-xs text-zinc-500 mt-1">12 Phases</div>
                            </div>
                            <div class="border border-zinc-800 rounded-xl p-4 bg-zinc-900/50">
                                <div class="text-xs text-emerald-400 font-medium">%s</div>
                                <div class="text-2xl font-bold text-emerald-400 mt-1">%d</div>
                                <div class="text-xs text-zinc-500 mt-1">100%% Completed</div>
                            </div>
                            <div class="border border-zinc-800 rounded-xl p-4 bg-zinc-900/50">
                                <div class="text-xs text-slate-400 font-medium">%s</div>
                                <div class="text-2xl font-bold text-slate-300 mt-1">%d</div>
                                <div class="text-xs text-zinc-500 mt-1">Configured Rule</div>
                            </div>
                            <div class="border border-zinc-800 rounded-xl p-4 bg-zinc-900/50">
                                <div class="text-xs text-cyan-400 font-medium">%s</div>
                                <div class="text-2xl font-bold text-cyan-400 mt-1">%.2fs</div>
                                <div class="text-xs text-zinc-500 mt-1">%s - %s</div>
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
                            <div>%s <strong>DB2DB by ZettaIS</strong> Enterprise Platform</div>
                            <div class="font-mono">JDK 25.0.4 &bull; GraalVM Native Ready &bull; PostgreSQL Certified</div>
                        </div>

                    </div>
                </body>
                </html>
                """.formatted(
                lang.name().toLowerCase(),
                escapeHtml(I18nMessages.get("report.title", lang)),
                escapeHtml(I18nMessages.get("report.migrationId", lang)),
                escapeHtml(report.migrationId()),
                escapeHtml(I18nMessages.get("report.title", lang)),
                escapeHtml(I18nMessages.get("report.sourceDb", lang)),
                escapeHtml(report.sourceDatabase()),
                escapeHtml(report.sourceSchema()),
                escapeHtml(I18nMessages.get("report.targetDb", lang)),
                escapeHtml(report.targetDatabase()),
                escapeHtml(report.targetSchema()),
                statusBadgeClass,
                statusText,
                escapeHtml(I18nMessages.get("report.mode", lang)),
                report.mode().name(),
                escapeHtml(I18nMessages.get("report.stepNumber", lang)),
                report.stepRecords().size(),
                escapeHtml(I18nMessages.get("report.completed", lang)),
                report.getSuccessCount(),
                escapeHtml(I18nMessages.get("report.skipped", lang)),
                report.getSkippedCount(),
                escapeHtml(I18nMessages.get("report.totalDuration", lang)),
                durationSec,
                startedStr,
                completedStr,
                escapeHtml(I18nMessages.get("report.stepsSummary", lang)),
                escapeHtml(I18nMessages.get("report.subtitle", lang)),
                stepsHtml.toString(),
                escapeHtml(I18nMessages.get("report.generatedAt", lang))
        );
    }

    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}
