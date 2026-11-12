package org.db2db.core.assessment;

import java.util.LinkedHashMap;
import java.util.Map;

public class AssessmentReport {
    private String schemaName;
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
    private String difficultyLevel; // LOW, MEDIUM, HIGH, CRITICAL

    private Map<String, Integer> objectCountByType = new LinkedHashMap<>();
    private Map<String, Double> costUnitsByType = new LinkedHashMap<>();

    public String getSchemaName() { return schemaName; }
    public void setSchemaName(String schemaName) { this.schemaName = schemaName; }
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
    public Map<String, Double> getCostUnitsByType() { return costUnitsByType; }
}
