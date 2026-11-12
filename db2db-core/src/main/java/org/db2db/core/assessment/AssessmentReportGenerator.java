package org.db2db.core.assessment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.util.Map;

/**
 * Formats AssessmentReport into responsive HTML or JSON formats.
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
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n")
          .append("<meta charset=\"UTF-8\">\n<title>db2db Migration Assessment Report - ")
          .append(report.getSchemaName()).append("</title>\n")
          .append("<style>\n")
          .append("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; margin: 2rem; background: #f8f9fa; color: #333; }\n")
          .append(".card { background: white; border-radius: 8px; padding: 1.5rem; margin-bottom: 1.5rem; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }\n")
          .append("h1, h2 { color: #1e3a8a; }\n")
          .append(".badge { display: inline-block; padding: 0.35rem 0.65rem; border-radius: 4px; font-weight: bold; }\n")
          .append(".badge-LOW { background: #dcfce7; color: #15803d; }\n")
          .append(".badge-MEDIUM { background: #fef9c3; color: #854d0e; }\n")
          .append(".badge-HIGH { background: #fee2e2; color: #b91c1c; }\n")
          .append(".badge-CRITICAL { background: #450a0a; color: #f87171; }\n")
          .append("table { width: 100%; border-collapse: collapse; margin-top: 1rem; }\n")
          .append("th, td { text-align: left; padding: 0.75rem; border-bottom: 1px solid #e5e7eb; }\n")
          .append("th { background-color: #f3f4f6; color: #374151; }\n")
          .append("</style>\n</head>\n<body>\n")
          .append("<h1>db2db - Migration Assessment Report</h1>\n")
          .append("<div class=\"card\">\n")
          .append("<h2>Executive Summary: Schema ").append(report.getSchemaName()).append("</h2>\n")
          .append("<p><strong>Estimated Migration Units:</strong> ").append(report.getTotalMigrationCostUnits()).append("</p>\n")
          .append("<p><strong>Estimated Human Effort:</strong> ").append(report.getEstimatedHumanDays()).append(" human-days</p>\n")
          .append("<p><strong>Complexity Level:</strong> <span class=\"badge badge-").append(report.getDifficultyLevel())
          .append("\">").append(report.getDifficultyLevel()).append("</span></p>\n")
          .append("<p><strong>Invalid Objects Detected:</strong> ").append(report.getTotalInvalidObjects()).append("</p>\n")
          .append("</div>\n")
          .append("<div class=\"card\">\n")
          .append("<h2>Catalog Object Inventory</h2>\n")
          .append("<table>\n<tr><th>Object Type</th><th>Count</th></tr>\n");

        for (Map.Entry<String, Integer> entry : report.getObjectCountByType().entrySet()) {
            sb.append("<tr><td>").append(entry.getKey()).append("</td><td>").append(entry.getValue()).append("</td></tr>\n");
        }
        sb.append("</table>\n</div>\n");

        sb.append("<div class=\"card\">\n")
          .append("<h2>Effort Cost Breakdown</h2>\n")
          .append("<table>\n<tr><th>Category</th><th>Cost Units</th></tr>\n");

        for (Map.Entry<String, Double> entry : report.getCostUnitsByType().entrySet()) {
            sb.append("<tr><td>").append(entry.getKey()).append("</td><td>").append(entry.getValue()).append("</td></tr>\n");
        }
        sb.append("</table>\n</div>\n</body>\n</html>\n");

        return sb.toString();
    }
}
