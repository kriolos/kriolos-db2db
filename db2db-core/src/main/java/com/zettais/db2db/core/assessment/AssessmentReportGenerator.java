package com.zettais.db2db.core.assessment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Formats AssessmentReport into responsive HTML or JSON formats,
 * presenting executive metrics, detailed inventory, procedural code complexity,
 * spatial conversions, and actionable migration recommendations.
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
        var now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        var sb = new StringBuilder();

        sb.append("""
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>db2db Migration Assessment Report - """).append(report.getSchemaName()).append("""
</title>
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
        }
        .container { max-width: 1280px; margin: 0 auto; }
        header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            border-bottom: 2px solid var(--border);
            padding-bottom: 1.5rem;
            margin-bottom: 2rem;
        }
        .logo-box h1 { font-size: 1.85rem; color: var(--primary); font-weight: 800; letter-spacing: -0.5px; }
        .logo-box p { font-size: 0.95rem; color: #64748b; margin-top: 0.25rem; }
        .meta-box { text-align: right; font-size: 0.9rem; color: #64748b; }
        .meta-box strong { color: var(--text-heading); }

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
        .kpi-value { font-size: 2rem; font-weight: 800; color: var(--text-heading); margin-top: 0.35rem; }
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

        /* Tables */
        table { width: 100%; border-collapse: collapse; margin-top: 0.75rem; font-size: 0.95rem; }
        th, td { padding: 0.85rem 1rem; text-align: left; border-bottom: 1px solid var(--border); }
        th { background: #f8fafc; font-weight: 600; color: #475569; font-size: 0.85rem; text-transform: uppercase; letter-spacing: 0.5px; }
        tr:hover td { background: #f1f5f9; }

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
    </style>
</head>
<body>
<div class="container">
    <header>
        <div class="logo-box">
            <h1>DB2DB Migration Assessment</h1>
            <p>Enterprise Database Migration & Analysis Platform &bull; DB2DB by ZettaIS</p>
        </div>
        <div class="meta-box">
            <p><strong>Target Schema:</strong> """).append(report.getSchemaName()).append("""
</p>
            <p><strong>Source Database:</strong> """).append(report.getDatabaseVersion()).append("""
</p>
            <p><strong>Database Footprint:</strong> """).append(report.getDatabaseSize()).append("""
</p>
            <p><strong>Generated At:</strong> """).append(now).append("""
</p>
        </div>
    </header>

    <!-- Executive KPI Row -->
    <div class="kpi-grid">
        <div class="kpi-card effort">
            <div class="kpi-title">Migration Effort</div>
            <div class="kpi-value">""").append(report.getTotalMigrationCostUnits()).append("""
 <span style="font-size: 1rem; font-weight: 600;">units</span></div>
            <div class="kpi-sub">≈ <strong>""").append(report.getEstimatedHumanDays()).append("""
</strong> human-days effort</div>
        </div>
        <div class="kpi-card difficulty">
            <div class="kpi-title">Complexity Level</div>
            <div class="kpi-value"><span class="badge badge-""").append(report.getDifficultyLevel()).append("""
">""").append(report.getDifficultyLevel()).append("""
</span></div>
            <div class="kpi-sub">Classification based on PL/SQL & schema scale</div>
        </div>
        <div class="kpi-card">
            <div class="kpi-title">Inventory Scale</div>
            <div class="kpi-value">""").append(report.getTotalTables()).append("""
 <span style="font-size: 1rem; font-weight: 600;">tables</span></div>
            <div class="kpi-sub">""").append(report.getTotalColumns()).append("""
 cols, """).append(String.format(java.util.Locale.US, "%,d", report.getTotalRows())).append("""
 rows</div>
        </div>
        <div class="kpi-card code">
            <div class="kpi-title">Procedural Code</div>
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
        <h2>Effort Cost Distribution</h2>
        <div class="progress-container">
            <div class="progress-bar-wrapper">
""");

        for (Map.Entry<String, Double> entry : report.getCostUnitsByType().entrySet()) {
            double pct = Math.max(1.0, (entry.getValue() / totalCost) * 100.0);
            String color = colors[colorIdx % colors.length];
            colorIdx++;
            sb.append("                <div class=\"progress-segment\" style=\"width: ")
              .append(String.format("%.1f", pct)).append("%; background-color: ")
              .append(color).append(";\" title=\"")
              .append(entry.getKey()).append(": ")
              .append(entry.getValue()).append(" units (")
              .append(String.format("%.1f", pct)).append("%)\"></div>\n");
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
              .append("<span>").append(entry.getKey()).append(" (")
              .append(String.format("%.1f", pct)).append("%)</span></div>\n");
        }

        sb.append("""
            </div>
        </div>
    </div>
""");

        // Object Inventory Section
        sb.append("""
    <div class="card">
        <h2>Catalog Object Inventory & Migration Units</h2>
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
              .append("<td><strong>").append(type).append("</strong></td>")
              .append("<td>").append(count).append("</td>")
              .append("<td>").append(invalid > 0 ? "<span style='color:red; font-weight:bold'>" + invalid + "</span>" : "0").append("</td>")
              .append("<td>").append(units > 0 ? String.format("%.1f", units) : "-").append("</td>")
              .append("<td>").append(comment);

            if (detail != null && detail.details() != null && !detail.details().isEmpty()) {
                sb.append("<ul class=\"details-list\">");
                for (String d : detail.details()) {
                    sb.append("<li>").append(d).append("</li>");
                }
                sb.append("</ul>");
            }

            sb.append("</td></tr>\n");
        }

        sb.append("""
            </tbody>
        </table>
    </div>
""");

        // Top Tables Section
        if (!report.getTopTablesByRows().isEmpty() || !report.getTopTablesBySize().isEmpty()) {
            sb.append("""
    <div class="card">
        <h2>Top Largest Tables by Volume & Footprint</h2>
        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 1.5rem;">
            <div>
                <h3 style="font-size: 1.05rem; margin-bottom: 0.75rem; color: #475569;">By Row Count (Parallel Copy Candidates)</h3>
                <table>
                    <thead>
                        <tr><th>Table</th><th>Rows</th><th>Columns</th><th>Indexes</th></tr>
                    </thead>
                    <tbody>
""");
            for (var tm : report.getTopTablesByRows()) {
                sb.append("<tr><td><strong>").append(tm.tableName()).append("</strong></td>")
                  .append("<td>").append(String.format(java.util.Locale.US, "%,d", tm.rowCount())).append("</td>")
                  .append("<td>").append(tm.columnCount()).append("</td>")
                  .append("<td>").append(tm.indexCount()).append("</td></tr>\n");
            }
            sb.append("""
                    </tbody>
                </table>
            </div>
            <div>
                <h3 style="font-size: 1.05rem; margin-bottom: 0.75rem; color: #475569;">By Disk Footprint</h3>
                <table>
                    <thead>
                        <tr><th>Table</th><th>Allocated Size</th><th>Rows</th></tr>
                    </thead>
                    <tbody>
""");
            for (var tm : report.getTopTablesBySize()) {
                sb.append("<tr><td><strong>").append(tm.tableName()).append("</strong></td>")
                  .append("<td><strong>").append(tm.sizeFormatted()).append("</strong></td>")
                  .append("<td>").append(String.format(java.util.Locale.US, "%,d", tm.rowCount())).append("</td></tr>\n");
            }
            sb.append("""
                    </tbody>
                </table>
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
        <h2>Procedural Code Analysis (PL/SQL to PL/pgSQL)</h2>
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
        <h2>Spatial & PostGIS Geographic Conversion</h2>
        <p>Found <strong>""").append(sp.sdoGeometryColumns()).append("""
</strong> columns utilizing Oracle MDSYS.SDO_GEOMETRY.</p>
        <ul class="details-list" style="margin-top: 0.75rem;">
            <li>Point Geometries (2D / 3D): <strong>""").append(sp.pointLayers()).append("""
</strong> layers mapped to <code>geometry(Point, 4326)</code></li>
            <li>Polygon Geometries: <strong>""").append(sp.polygonLayers()).append("""
</strong> layers mapped to <code>geometry(Polygon, 4326)</code></li>
            <li>Prerequisite SQL statement: <code>""").append(sp.postGisExtensionRecommendation()).append("""
</code></li>
        </ul>
    </div>
""");
        }

        // Reserved Keyword Warnings
        if (!report.getReservedKeywordWarnings().isEmpty()) {
            sb.append("""
    <div class="card">
        <h2>PostgreSQL Reserved Keyword Collisions</h2>
        <div class="alert alert-warning">
            The following table or column names conflict with PostgreSQL reserved keywords and require automatic double-quoting:
        </div>
        <ul class="details-list">
""");
            for (String kw : report.getReservedKeywordWarnings()) {
                sb.append("            <li><strong>").append(kw).append("</strong></li>\n");
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
        <h2>Strategic Migration Recommendations & Action Plan</h2>
""");
            for (var rec : report.getRecommendations()) {
                sb.append("        <div class=\"rec-item ").append(rec.severity()).append("\">\n")
                  .append("            <div class=\"rec-header\">\n")
                  .append("                <span class=\"rec-title\">").append(rec.title()).append("</span>\n")
                  .append("                <span class=\"badge badge-").append(rec.severity()).append("\">").append(rec.severity()).append("</span>\n")
                  .append("            </div>\n")
                  .append("            <div class=\"rec-desc\">").append(rec.description()).append("</div>\n")
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
}
