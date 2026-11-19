package com.zettais.db2db.core.assessment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Migration Assessment Report capturing inventory, complexity metrics,
 * migration cost units, and actionable recommendations.
 */
public class AssessmentReport {

    public record TableMetric(
            String tableName,
            long rowCount,
            String sizeFormatted,
            int columnCount,
            int indexCount
    ) {}

    public record ObjectAssessmentDetail(
            String objectType,
            int count,
            int invalidCount,
            double costUnits,
            String comments,
            List<String> details
    ) {}

    public record PlSqlAssessmentDetail(
            int packages,
            int procedures,
            int functions,
            int triggers,
            int totalLines,
            int autonomousTransactions,
            int dynamicSqlOccurrences,
            int cursorCount,
            double costUnits
    ) {}

    public record SpatialAssessmentDetail(
            int sdoGeometryColumns,
            int pointLayers,
            int polygonLayers,
            int compoundLayers,
            String postGisExtensionRecommendation
    ) {}

    public record MigrationRecommendation(
            String category,
            String title,
            String description,
            String severity // LOW, MEDIUM, HIGH, CRITICAL
    ) {}

    private String schemaName = "";
    private String databaseType = "ORACLE";
    private String databaseVersion = "19.3.0.0.0 Enterprise Edition";
    private String databaseSize = "184.6 GB";
    private long totalRows = 0;

    private int totalTables;
    private int totalColumns;
    private int totalConstraints;
    private int totalIndexes;
    private int totalSequences;
    private int totalViews;
    private int totalTriggers;
    private int totalFunctions;
    private int totalProcedures;
    private int totalPackages;
    private int totalPlSqlLines;
    private int totalInvalidObjects;

    private double totalMigrationCostUnits;
    private double estimatedHumanDays;
    private String difficultyLevel = "MEDIUM"; // LOW, MEDIUM, HIGH, CRITICAL

    private Map<String, Integer> objectCountByType = new LinkedHashMap<>();
    private Map<String, Double> costUnitsByType = new LinkedHashMap<>();
    private Map<String, ObjectAssessmentDetail> objectDetails = new LinkedHashMap<>();

    private List<TableMetric> topTablesByRows = new ArrayList<>();
    private List<TableMetric> topTablesBySize = new ArrayList<>();
    private PlSqlAssessmentDetail plSqlDetail;
    private SpatialAssessmentDetail spatialDetail;
    private List<MigrationRecommendation> recommendations = new ArrayList<>();
    private List<String> reservedKeywordWarnings = new ArrayList<>();

    // Getters and Setters
    public String getSchemaName() { return schemaName; }
    public void setSchemaName(String schemaName) { this.schemaName = schemaName; }

    public String getDatabaseType() { return databaseType; }
    public void setDatabaseType(String databaseType) { this.databaseType = databaseType; }

    public String getDatabaseVersion() { return databaseVersion; }
    public void setDatabaseVersion(String databaseVersion) { this.databaseVersion = databaseVersion; }

    public String getDatabaseSize() { return databaseSize; }
    public void setDatabaseSize(String databaseSize) { this.databaseSize = databaseSize; }

    public long getTotalRows() { return totalRows; }
    public void setTotalRows(long totalRows) { this.totalRows = totalRows; }

    public int getTotalTables() { return totalTables; }
    public void setTotalTables(int totalTables) { this.totalTables = totalTables; }

    public int getTotalColumns() { return totalColumns; }
    public void setTotalColumns(int totalColumns) { this.totalColumns = totalColumns; }

    public int getTotalConstraints() { return totalConstraints; }
    public void setTotalConstraints(int totalConstraints) { this.totalConstraints = totalConstraints; }

    public int getTotalIndexes() { return totalIndexes; }
    public void setTotalIndexes(int totalIndexes) { this.totalIndexes = totalIndexes; }

    public int getTotalSequences() { return totalSequences; }
    public void setTotalSequences(int totalSequences) { this.totalSequences = totalSequences; }

    public int getTotalViews() { return totalViews; }
    public void setTotalViews(int totalViews) { this.totalViews = totalViews; }

    public int getTotalTriggers() { return totalTriggers; }
    public void setTotalTriggers(int totalTriggers) { this.totalTriggers = totalTriggers; }

    public int getTotalFunctions() { return totalFunctions; }
    public void setTotalFunctions(int totalFunctions) { this.totalFunctions = totalFunctions; }

    public int getTotalProcedures() { return totalProcedures; }
    public void setTotalProcedures(int totalProcedures) { this.totalProcedures = totalProcedures; }

    public int getTotalPackages() { return totalPackages; }
    public void setTotalPackages(int totalPackages) { this.totalPackages = totalPackages; }

    public int getTotalPlSqlLines() { return totalPlSqlLines; }
    public void setTotalPlSqlLines(int totalPlSqlLines) { this.totalPlSqlLines = totalPlSqlLines; }

    public int getTotalInvalidObjects() { return totalInvalidObjects; }
    public void setTotalInvalidObjects(int totalInvalidObjects) { this.totalInvalidObjects = totalInvalidObjects; }

    public double getTotalMigrationCostUnits() { return totalMigrationCostUnits; }
    public void setTotalMigrationCostUnits(double totalMigrationCostUnits) { this.totalMigrationCostUnits = totalMigrationCostUnits; }

    public double getEstimatedHumanDays() { return estimatedHumanDays; }
    public void setEstimatedHumanDays(double estimatedHumanDays) { this.estimatedHumanDays = estimatedHumanDays; }

    public String getDifficultyLevel() { return difficultyLevel; }
    public void setDifficultyLevel(String difficultyLevel) { this.difficultyLevel = difficultyLevel; }

    public Map<String, Integer> getObjectCountByType() { return objectCountByType; }
    public void setObjectCountByType(Map<String, Integer> objectCountByType) { this.objectCountByType = objectCountByType; }

    public Map<String, Double> getCostUnitsByType() { return costUnitsByType; }
    public void setCostUnitsByType(Map<String, Double> costUnitsByType) { this.costUnitsByType = costUnitsByType; }

    public Map<String, ObjectAssessmentDetail> getObjectDetails() { return objectDetails; }
    public void setObjectDetails(Map<String, ObjectAssessmentDetail> objectDetails) { this.objectDetails = objectDetails; }

    public List<TableMetric> getTopTablesByRows() { return topTablesByRows; }
    public void setTopTablesByRows(List<TableMetric> topTablesByRows) { this.topTablesByRows = topTablesByRows; }

    public List<TableMetric> getTopTablesBySize() { return topTablesBySize; }
    public void setTopTablesBySize(List<TableMetric> topTablesBySize) { this.topTablesBySize = topTablesBySize; }

    public PlSqlAssessmentDetail getPlSqlDetail() { return plSqlDetail; }
    public void setPlSqlDetail(PlSqlAssessmentDetail plSqlDetail) { this.plSqlDetail = plSqlDetail; }

    public SpatialAssessmentDetail getSpatialDetail() { return spatialDetail; }
    public void setSpatialDetail(SpatialAssessmentDetail spatialDetail) { this.spatialDetail = spatialDetail; }

    public List<MigrationRecommendation> getRecommendations() { return recommendations; }
    public void setRecommendations(List<MigrationRecommendation> recommendations) { this.recommendations = recommendations; }

    public List<String> getReservedKeywordWarnings() { return reservedKeywordWarnings; }
    public void setReservedKeywordWarnings(List<String> reservedKeywordWarnings) { this.reservedKeywordWarnings = reservedKeywordWarnings; }
}
