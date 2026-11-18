package com.zettais.db2db.core.assessment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.zettais.db2db.core.i18n.I18nMessages;
import com.zettais.db2db.core.i18n.Language;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Formats AssessmentReport into responsive HTML or JSON formats,
 * presenting executive metrics, detailed inventory, procedural code complexity,
 * spatial conversions, and actionable migration recommendations.
 * Fully responsive across mobile, tablet, desktop, and print/PDF views.
 */
public class AssessmentReportGenerator {

    private final ObjectMapper objectMapper;

    public AssessmentReportGenerator() {
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public String toJson(AssessmentReport report) throws IOException {
        return objectMapper.writeValueAsString(report);
    }

    public String toHtml(AssessmentReport report) {
        return toHtml(report, Language.EN);
    }

    public String toHtml(AssessmentReport report, Language lang) {
        var effectiveLang = lang != null ? lang : Language.EN;
        var now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        var sb = new StringBuilder();

        sb.append("<!DOCTYPE html>\n<html lang=\"").append(effectiveLang.getCode()).append("\">\n<head>\n")
          .append("    <meta charset=\"UTF-8\">\n")
          .append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
          .append("    <title>").append(I18nMessages.get("assess.report.title", effectiveLang))
          .append(" - ").append(escapeHtml(report.getSchemaName())).append("</title>\n");

        sb.append("""
    <style>
        :root {
            --primary: #1e3a8a;
            --primary-light: #3b82f6;
            --success: #16a34a;
            --warning: #ca8a04;
            --danger: #dc2626;
            --dark: #0f172a;
            --card-bg: #ffffff;
            --body-bg: #f8fafc;
            --border: #e2e8f0;
            --text: #334155;
            --text-heading: #0f172a;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            background-color: var(--body-bg);
            color: var(--text);
            line-height: 1.6;
            padding: 2.5rem 2rem;
            -webkit-print-color-adjust: exact;
            print-color-adjust: exact;
        }
        .container { max-width: 1400px; margin: 0 auto; width: 100%; }
        header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            border-bottom: 2px solid var(--border);
            padding-bottom: 1.5rem;
            margin-bottom: 2rem;
            gap: 1.5rem;
        }
        .logo-box h1 {
            font-size: clamp(1.4rem, 2.5vw, 1.95rem);
            color: var(--primary);
            font-weight: 800;
            letter-spacing: -0.5px;
        }
        .logo-box p { font-size: 0.95rem; color: #64748b; margin-top: 0.25rem; }
        .meta-container {
            display: flex;
            flex-direction: column;
            align-items: flex-end;
            gap: 0.75rem;
        }
        .meta-box { text-align: right; font-size: 0.9rem; color: #64748b; }
        .meta-box strong { color: var(--text-heading); }

        .btn-print {
            display: inline-flex;
            align-items: center;
            gap: 0.5rem;
            padding: 0.55rem 1.15rem;
            background: var(--primary);
            color: #ffffff;
            border: 1px solid var(--primary);
            border-radius: 8px;
            font-size: 0.88rem;
            font-weight: 600;
            cursor: pointer;
            box-shadow: 0 1px 3px rgba(0,0,0,0.1);
            transition: all 0.2s ease;
        }
        .btn-print:hover {
            background: var(--primary-light);
            border-color: var(--primary-light);
            transform: translateY(-1px);
        }

        /* KPI Grid */
        .kpi-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
            gap: 1.25rem;
            margin-bottom: 2rem;
        }
        .kpi-card {
            background: var(--card-bg);
            border-radius: 10px;
            padding: 1.25rem 1.5rem;
            border: 1px solid var(--border);
            box-shadow: 0 1px 3px rgba(0,0,0,0.05);
            position: relative;
            overflow: hidden;
        }
        .kpi-card::before {
            content: '';
            position: absolute;
            left: 0;
            top: 0;
            bottom: 0;
            width: 4px;
            background: var(--primary-light);
        }
        .kpi-card.effort::before { background: #8b5cf6; }
        .kpi-card.difficulty::before { background: var(--warning); }
        .kpi-card.code::before { background: #06b6d4; }
        .kpi-title { font-size: 0.85rem; font-weight: 600; text-transform: uppercase; color: #64748b; letter-spacing: 0.5px; }
        .kpi-value { font-size: clamp(1.5rem, 3vw, 2.2rem); font-weight: 800; color: var(--text-heading); margin-top: 0.35rem; }
        .kpi-sub { font-size: 0.85rem; color: #94a3b8; margin-top: 0.25rem; }

        .card {
            background: var(--card-bg);
            border-radius: 10px;
            padding: 1.75rem;
            border: 1px solid var(--border);
            box-shadow: 0 1px 3px rgba(0,0,0,0.05);
            margin-bottom: 2rem;
        }
        .card h2 {
            font-size: 1.35rem;
            color: var(--text-heading);
            margin-bottom: 1.25rem;
            display: flex;
            align-items: center;
            gap: 0.5rem;
        }

        /* Badges */
        .badge {
            display: inline-block;
            padding: 0.35rem 0.75rem;
            border-radius: 9999px;
            font-size: 0.8rem;
            font-weight: 700;
            text-transform: uppercase;
        }
        .badge-LOW { background: #dcfce7; color: #166534; }
        .badge-MEDIUM { background: #fef9c3; color: #854d0e; }
        .badge-HIGH { background: #fee2e2; color: #991b1b; }
        .badge-CRITICAL { background: #7f1d1d; color: #fee2e2; }

        /* Tables & Responsiveness */
        .table-responsive {
            width: 100%;
            overflow-x: auto;
            -webkit-overflow-scrolling: touch;
            margin-top: 0.75rem;
        }
        table { width: 100%; border-collapse: collapse; font-size: 0.95rem; }
        th, td { padding: 0.85rem 1rem; text-align: left; border-bottom: 1px solid var(--border); }
        th { background: #f8fafc; font-weight: 600; color: #475569; font-size: 0.85rem; text-transform: uppercase; letter-spacing: 0.5px; }
        tr:hover td { background: #f1f5f9; }

        .grid-two-col {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
            gap: 1.5rem;
        }

        /* Progress Bar */
        .progress-container { margin: 1rem 0; }
        .progress-bar-wrapper {
            display: flex;
            height: 24px;
            border-radius: 6px;
            overflow: hidden;
            background: #e2e8f0;
        }
        .progress-segment {
            height: 100%;
            transition: width 0.3s ease;
        }
        .legend { display: flex; flex-wrap: wrap; gap: 1rem; margin-top: 0.75rem; font-size: 0.85rem; }
        .legend-item { display: flex; align-items: center; gap: 0.4rem; }
        .legend-color { width: 12px; height: 12px; border-radius: 3px; }

        /* Recommendation cards */
        .rec-item {
            padding: 1rem;
            border: 1px solid var(--border);
            border-left: 4px solid var(--primary);
            border-radius: 6px;
            margin-bottom: 1rem;
            background: #fbfcfe;
        }
        .rec-item.HIGH { border-left-color: var(--danger); }
        .rec-item.MEDIUM { border-left-color: var(--warning); }
        .rec-item.LOW { border-left-color: var(--success); }
        .rec-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.5rem; }
        .rec-title { font-weight: 700; color: var(--text-heading); font-size: 1rem; }
        .rec-desc { font-size: 0.9rem; color: #475569; }

        /* Collapsible details list */
        .details-list { margin-top: 0.5rem; padding-left: 1.25rem; font-size: 0.88rem; color: #475569; }
        .details-list li { margin-bottom: 0.25rem; }

        /* Alert warning */
        .alert {
            padding: 1rem 1.25rem;
            border-radius: 6px;
            margin-bottom: 1.5rem;
            font-size: 0.95rem;
        }
        .alert-warning { background: #fffbeb; border: 1px solid #fde68a; color: #92400e; }

        footer { text-align: center; margin-top: 3rem; font-size: 0.85rem; color: #94a3b8; }

        /* Responsive Media Queries (Mobile & Small Tablets) */
        @media screen and (max-width: 768px) {
            body { padding: 1.25rem 1rem; }
            header {
                flex-direction: column;
                align-items: flex-start;
            }
            .meta-container {
                width: 100%;
                align-items: flex-start;
            }
            .meta-box { text-align: left; }
            .kpi-grid {
                grid-template-columns: 1fr;
                gap: 1rem;
            }
            .card { padding: 1.25rem; }
            .card h2 { font-size: 1.15rem; }
            table { min-width: 540px; }
        }

        /* Large Screen Refinements (Widescreen 1440px+) */
        @media screen and (min-width: 1440px) {
            .container { max-width: 1540px; }
            body { padding: 3rem 2.5rem; }
        }

        /* Print Mode Optimization */
        @media print {
            @page {
                size: A4 portrait;
                margin: 12mm 10mm;
            }
            body {
                background: #ffffff !important;
                color: #0f172a !important;
                padding: 0 !important;
                font-size: 10pt !important;
                line-height: 1.35 !important;
            }
            .container {
                max-width: 100% !important;
                margin: 0 !important;
                padding: 0 !important;
            }
            .no-print {
                display: none !important;
            }
            header {
                border-bottom: 2px solid #0f172a !important;
                padding-bottom: 0.75rem !important;
                margin-bottom: 1rem !important;
            }
            .meta-container {
                align-items: flex-end !important;
            }
            .kpi-grid {
                display: grid !important;
                grid-template-columns: repeat(4, 1fr) !important;
                gap: 0.5rem !important;
                margin-bottom: 1rem !important;
            }
            .kpi-card {
                background: #ffffff !important;
                border: 1px solid #cbd5e1 !important;
                box-shadow: none !important;
                padding: 0.65rem 0.85rem !important;
                page-break-inside: avoid;
                break-inside: avoid;
            }
            .kpi-value {
                font-size: 1.4rem !important;
            }
            .card {
                background: #ffffff !important;
                border: 1px solid #cbd5e1 !important;
                box-shadow: none !important;
                padding: 0.85rem !important;
                margin-bottom: 1rem !important;
                page-break-inside: avoid;
                break-inside: avoid;
            }
            .rec-item {
                background: #ffffff !important;
                border: 1px solid #cbd5e1 !important;
                page-break-inside: avoid;
                break-inside: avoid;
            }
            .table-responsive {
                overflow: visible !important;
            }
            table {
                width: 100% !important;
                min-width: unset !important;
                font-size: 8.5pt !important;
            }
            th, td {
                padding: 0.35rem 0.5rem !important;
                border-bottom: 1px solid #e2e8f0 !important;
            }
            tr {
                page-break-inside: avoid;
                break-inside: avoid;
            }
            footer {
                margin-top: 1.25rem !important;
                font-size: 8pt !important;
                color: #64748b !important;
            }
        }
    </style>
</head>
<body>
<div class="container">
    <header>
        <div class="logo-box">
            <h1>""").append(I18nMessages.get("assess.report.title", effectiveLang)).append("""
</h1>
            <p>""").append(I18nMessages.get("assess.report.subtitle", effectiveLang)).append("""
</p>
        </div>
        <div class="meta-container">
            <div class="meta-box">
                <p><strong>""").append(I18nMessages.get("assess.report.targetSchema", effectiveLang)).append(":</strong> ")
                  .append(escapeHtml(report.getSchemaName())).append("</p>\n")
                  .append("                <p><strong>").append(I18nMessages.get("assess.report.sourceDb", effectiveLang)).append(":</strong> ")
                  .append(escapeHtml(report.getDatabaseVersion())).append("</p>\n")
                  .append("                <p><strong>").append(I18nMessages.get("assess.report.footprint", effectiveLang)).append(":</strong> ")
                  .append(escapeHtml(report.getDatabaseSize())).append("</p>\n")
                  .append("                <p><strong>").append(I18nMessages.get("assess.report.generatedAt", effectiveLang)).append(":</strong> ")
                  .append(now).append("""
</p>
            </div>
            <div class="no-print">
                <button class="btn-print" onclick="window.print()">🖨️ """).append(I18nMessages.get("assess.report.print", effectiveLang)).append("""
</button>
            </div>
        </div>
    </header>

    <!-- Executive KPI Row -->
    <div class="kpi-grid">
        <div class="kpi-card effort">
            <div class="kpi-title">""").append(I18nMessages.get("assess.report.effortTitle", effectiveLang)).append("""
</div>
            <div class="kpi-value">""").append(String.format(java.util.Locale.US, "%.1f", report.getTotalMigrationCostUnits())).append("""
 <span style="font-size: 1rem; font-weight: 600;">units</span></div>
            <div class="kpi-sub">≈ <strong>""").append(String.format(java.util.Locale.US, "%.1f", report.getEstimatedHumanDays())).append("</strong> ")
            .append(I18nMessages.get("assess.report.effortSub", effectiveLang)).append("""
</div>
        </div>
        <div class="kpi-card difficulty">
            <div class="kpi-title">""").append(I18nMessages.get("assess.report.complexityTitle", effectiveLang)).append("""
</div>
            <div class="kpi-value"><span class="badge badge-""").append(report.getDifficultyLevel()).append("""
">""").append(report.getDifficultyLevel()).append("""
</span></div>
            <div class="kpi-sub">""").append(I18nMessages.get("assess.report.complexitySub", effectiveLang)).append("""
</div>
        </div>
        <div class="kpi-card">
            <div class="kpi-title">""").append(I18nMessages.get("assess.report.inventoryTitle", effectiveLang)).append("""
</div>
            <div class="kpi-value">""").append(report.getTotalTables()).append("""
 <span style="font-size: 1rem; font-weight: 600;">tables</span></div>
            <div class="kpi-sub">""").append(report.getTotalColumns()).append(" cols, ")
            .append(String.format(java.util.Locale.US, "%,d", report.getTotalRows())).append("""
 rows</div>
        </div>
        <div class="kpi-card code">
            <div class="kpi-title">""").append(I18nMessages.get("assess.report.proceduralTitle", effectiveLang)).append("""
</div>
            <div class="kpi-value">""").append(report.getTotalPlSqlLines()).append("""
 <span style="font-size: 1rem; font-weight: 600;">lines</span></div>
            <div class="kpi-sub">""").append(report.getTotalPackages()).append(" pkgs, ")
            .append(report.getTotalProcedures()).append(" procs, ")
            .append(report.getTotalFunctions()).append("""
 funcs</div>
        </div>
    </div>
""");

        // Cost breakdown progress bar
        double totalCost = report.getTotalMigrationCostUnits() > 0 ? report.getTotalMigrationCostUnits() : 1.0;
        String[] colors = {"#3b82f6", "#10b981", "#f59e0b", "#8b5cf6", "#ec4899", "#06b6d4", "#6366f1"};
        int colorIdx = 0;

        sb.append("""
    <div class="card">
        <h2>""").append(I18nMessages.get("assess.report.distributionTitle", effectiveLang)).append("""
</h2>
        <div class="progress-container">
            <div class="progress-bar-wrapper">
""");

        for (Map.Entry<String, Double> entry : report.getCostUnitsByType().entrySet()) {
            double pct = Math.max(1.0, (entry.getValue() / totalCost) * 100.0);
            String color = colors[colorIdx % colors.length];
            colorIdx++;
            sb.append("                <div class=\"progress-segment\" style=\"width: ")
              .append(String.format(java.util.Locale.US, "%.1f", pct)).append("%; background-color: ")
              .append(color).append(";\" title=\"")
              .append(escapeHtml(entry.getKey())).append(": ")
              .append(entry.getValue()).append(" units (")
              .append(String.format(java.util.Locale.US, "%.1f", pct)).append("%)\"></div>\n");
        }

        sb.append("""
            </div>
            <div class="legend">
""");
        colorIdx = 0;
        for (Map.Entry<String, Double> entry : report.getCostUnitsByType().entrySet()) {
            double pct = (entry.getValue() / totalCost) * 100.0;
            String color = colors[colorIdx % colors.length];
            colorIdx++;
            sb.append("                <div class=\"legend-item\">")
              .append("<div class=\"legend-color\" style=\"background-color: ").append(color).append(";\"></div>")
              .append("<span>").append(escapeHtml(entry.getKey())).append(" (")
              .append(String.format(java.util.Locale.US, "%.1f", pct)).append("%)</span></div>\n");
        }

        sb.append("""
            </div>
        </div>
    </div>
""");

        // Object Inventory Section
        sb.append("""
    <div class="card">
        <h2>""").append(I18nMessages.get("assess.report.inventorySectionTitle", effectiveLang)).append("""
</h2>
        <div class="table-responsive">
            <table>
                <thead>
                    <tr>
                        <th>Object Type</th>
                        <th>Count</th>
                        <th>Invalid</th>
                        <th>Migration Cost Units</th>
                        <th>Analysis & Transformation Remarks</th>
                    </tr>
                </thead>
                <tbody>
""");

        for (Map.Entry<String, Integer> entry : report.getObjectCountByType().entrySet()) {
            String type = entry.getKey();
            int count = entry.getValue();
            var detail = report.getObjectDetails().get(type);
            double units = detail != null ? detail.costUnits() : 0.0;
            int invalid = detail != null ? detail.invalidCount() : 0;
            String comment = detail != null ? detail.comments() : "Standard catalog object";

            sb.append("                <tr>")
              .append("<td><strong>").append(escapeHtml(type)).append("</strong></td>")
              .append("<td>").append(count).append("</td>")
              .append("<td>").append(invalid > 0 ? "<span style='color:red; font-weight:bold'>" + invalid + "</span>" : "0").append("</td>")
              .append("<td>").append(units > 0 ? String.format(java.util.Locale.US, "%.1f", units) : "-").append("</td>")
              .append("<td>").append(escapeHtml(comment));

            if (detail != null && detail.details() != null && !detail.details().isEmpty()) {
                sb.append("<ul class=\"details-list\">");
                for (String d : detail.details()) {
                    sb.append("<li>").append(escapeHtml(d)).append("</li>");
                }
                sb.append("</ul>");
            }

            sb.append("</td></tr>\n");
        }

        sb.append("""
                </tbody>
            </table>
        </div>
    </div>
""");

        // Top Tables Section
        if (!report.getTopTablesByRows().isEmpty() || !report.getTopTablesBySize().isEmpty()) {
            sb.append("""
    <div class="card">
        <h2>""").append(I18nMessages.get("assess.report.largestTablesTitle", effectiveLang)).append("""
</h2>
        <div class="grid-two-col">
            <div>
                <h3 style="font-size: 1.05rem; margin-bottom: 0.75rem; color: #475569;">""").append(I18nMessages.get("assess.report.byRows", effectiveLang)).append("""
</h3>
                <div class="table-responsive">
                    <table>
                        <thead>
                            <tr><th>Table</th><th>Rows</th><th>Columns</th><th>Indexes</th></tr>
                        </thead>
                        <tbody>
""");
            for (var tm : report.getTopTablesByRows()) {
                sb.append("<tr><td><strong>").append(escapeHtml(tm.tableName())).append("</strong></td>")
                  .append("<td>").append(String.format(java.util.Locale.US, "%,d", tm.rowCount())).append("</td>")
                  .append("<td>").append(tm.columnCount()).append("</td>")
                  .append("<td>").append(tm.indexCount()).append("</td></tr>\n");
            }
            sb.append("""
                        </tbody>
                    </table>
                </div>
            </div>
            <div>
                <h3 style="font-size: 1.05rem; margin-bottom: 0.75rem; color: #475569;">""").append(I18nMessages.get("assess.report.bySize", effectiveLang)).append("""
</h3>
                <div class="table-responsive">
                    <table>
                        <thead>
                            <tr><th>Table</th><th>Allocated Size</th><th>Rows</th></tr>
                        </thead>
                        <tbody>
""");
            for (var tm : report.getTopTablesBySize()) {
                sb.append("<tr><td><strong>").append(escapeHtml(tm.tableName())).append("</strong></td>")
                  .append("<td><strong>").append(escapeHtml(tm.sizeFormatted())).append("</strong></td>")
                  .append("<td>").append(String.format(java.util.Locale.US, "%,d", tm.rowCount())).append("</td></tr>\n");
            }
            sb.append("""
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    </div>
""");
        }

        // PL/SQL Procedural Assessment Section
        if (report.getPlSqlDetail() != null) {
            var pl = report.getPlSqlDetail();
            sb.append("""
    <div class="card">
        <h2>""").append(I18nMessages.get("assess.report.plsqlSectionTitle", effectiveLang)).append("""
</h2>
        <div class="kpi-grid" style="margin-bottom: 1.5rem;">
            <div class="kpi-card">
                <div class="kpi-title">Packages</div>
                <div class="kpi-value">""").append(pl.packages()).append("""
</div>
                <div class="kpi-sub">Transpiled to dedicated schemas</div>
            </div>
            <div class="kpi-card">
                <div class="kpi-title">Autonomous Tx</div>
                <div class="kpi-value" style="color: var(--danger);">""").append(pl.autonomousTransactions()).append("""
</div>
                <div class="kpi-sub">Requires pg_background / dblink</div>
            </div>
            <div class="kpi-card">
                <div class="kpi-title">Dynamic SQL</div>
                <div class="kpi-value" style="color: var(--warning);">""").append(pl.dynamicSqlOccurrences()).append("""
</div>
                <div class="kpi-sub">EXECUTE IMMEDIATE expressions</div>
            </div>
            <div class="kpi-card">
                <div class="kpi-title">Cursors & Ref Cursors</div>
                <div class="kpi-value">""").append(pl.cursorCount()).append("""
</div>
                <div class="kpi-sub">Mapped to REFCURSOR in PL/pgSQL</div>
            </div>
        </div>
    </div>
""");
        }

        // Spatial / PostGIS Assessment
        if (report.getSpatialDetail() != null) {
            var sp = report.getSpatialDetail();
            sb.append("""
    <div class="card">
        <h2>""").append(I18nMessages.get("assess.report.spatialSectionTitle", effectiveLang)).append("""
</h2>
        <p>Found <strong>""").append(sp.sdoGeometryColumns()).append("""
</strong> columns utilizing Oracle MDSYS.SDO_GEOMETRY.</p>
        <ul class="details-list" style="margin-top: 0.75rem;">
            <li>Point Geometries (2D / 3D): <strong>""").append(sp.pointLayers()).append("""
</strong> layers mapped to <code>geometry(Point, 4326)</code></li>
            <li>Polygon Geometries: <strong>""").append(sp.polygonLayers()).append("""
</strong> layers mapped to <code>geometry(Polygon, 4326)</code></li>
            <li>Prerequisite SQL statement: <code>""").append(escapeHtml(sp.postGisExtensionRecommendation())).append("""
</code></li>
        </ul>
    </div>
""");
        }

        // Reserved Keyword Warnings
        if (!report.getReservedKeywordWarnings().isEmpty()) {
            sb.append("""
    <div class="card">
        <h2>""").append(I18nMessages.get("assess.report.reservedTitle", effectiveLang)).append("""
</h2>
        <div class="alert alert-warning">
            The following table or column names conflict with PostgreSQL reserved keywords and require automatic double-quoting:
        </div>
        <ul class="details-list">
""");
            for (String kw : report.getReservedKeywordWarnings()) {
                sb.append("            <li><strong>").append(escapeHtml(kw)).append("</strong></li>\n");
            }
            sb.append("""
        </ul>
    </div>
""");
        }

        // Migration Recommendations
        if (!report.getRecommendations().isEmpty()) {
            sb.append("""
    <div class="card">
        <h2>""").append(I18nMessages.get("assess.report.recommendationsTitle", effectiveLang)).append("""
</h2>
""");
            for (var rec : report.getRecommendations()) {
                sb.append("        <div class=\"rec-item ").append(escapeHtml(rec.severity())).append("\">\n")
                  .append("            <div class=\"rec-header\">\n")
                  .append("                <span class=\"rec-title\">").append(escapeHtml(rec.title())).append("</span>\n")
                  .append("                <span class=\"badge badge-").append(escapeHtml(rec.severity())).append("\">").append(escapeHtml(rec.severity())).append("</span>\n")
                  .append("            </div>\n")
                  .append("            <div class=\"rec-desc\">").append(escapeHtml(rec.description())).append("</div>\n")
                  .append("        </div>\n");
            }
            sb.append("    </div>\n");
        }

        sb.append("""
    <footer>
        <p>DB2DB by ZettaIS v1.0.0 &bull; Powered by Modern Java 25 &bull; Quarkus 4</p>
    </footer>
</div>
</body>
</html>
""");

        return sb.toString();
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
