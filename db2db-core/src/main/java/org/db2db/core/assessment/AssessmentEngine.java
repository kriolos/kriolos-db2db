package org.db2db.core.assessment;

import org.db2db.core.model.*;

/**
 * Calculates migration assessment metrics, difficulty levels, and human-days cost
 * replicating Ora2Pg's estimation algorithm.
 */
public class AssessmentEngine {

    // Ora2Pg Migration Unit constants
    private static final double COST_TABLE = 1.0;
    private static final double COST_COLUMN = 0.1;
    private static final double COST_CONSTRAINT = 0.5;
    private static final double COST_INDEX = 0.5;
    private static final double COST_SEQUENCE = 0.2;
    private static final double COST_VIEW = 2.0;
    private static final double COST_TRIGGER = 4.0;
    private static final double COST_PROCEDURE = 5.0;
    private static final double COST_FUNCTION = 5.0;
    private static final double COST_PACKAGE = 15.0;
    private static final double COST_PER_PLSQL_LINE = 0.05;

    // 1 Human-Day = ~40 units (equivalent to ~8 hours with 5 units/hr)
    private static final double UNITS_PER_HUMAN_DAY = 40.0;

    public AssessmentReport assess(DatabaseCatalog catalog) {
        AssessmentReport report = new AssessmentReport();
        report.setSchemaName(catalog.getSchemaName());

        int totalTables = catalog.getTables().size();
        int totalCols = 0;
        int totalConstraints = 0;
        int totalIndexes = 0;
        for (Table t : catalog.getTables()) {
            totalCols += t.getColumns().size();
            totalConstraints += t.getConstraints().size();
            totalIndexes += t.getIndexes().size();
        }

        int totalSeqs = catalog.getSequences().size();
        int totalViews = catalog.getViews().size();
        int totalTriggers = catalog.getTriggers().size();

        int totalFuncs = 0;
        int totalProcs = 0;
        int totalPkgs = 0;
        int totalPlSqlLines = 0;

        for (PlSqlUnit unit : catalog.getPlSqlUnits()) {
            totalPlSqlLines += unit.getLineCount();
            switch (unit.getType()) {
                case FUNCTION -> totalFuncs++;
                case PROCEDURE -> totalProcs++;
                case PACKAGE_BODY, PACKAGE_SPEC -> totalPkgs++;
                default -> {}
            }
        }

        report.setTotalTables(totalTables);
        report.setTotalColumns(totalCols);
        report.setTotalConstraints(totalConstraints);
        report.setTotalIndexes(totalIndexes);
        report.setTotalSequences(totalSeqs);
        report.setTotalViews(totalViews);
        report.setTotalTriggers(totalTriggers);
        report.setTotalFunctions(totalFuncs);
        report.setTotalProcedures(totalProcs);
        report.setTotalPackages(totalPkgs);
        report.setTotalPlSqlLines(totalPlSqlLines);
        report.setTotalInvalidObjects(catalog.getInvalidObjects().size());

        // Calculate Cost Units
        double tableCost = (totalTables * COST_TABLE) + (totalCols * COST_COLUMN);
        double constraintCost = totalConstraints * COST_CONSTRAINT;
        double indexCost = totalIndexes * COST_INDEX;
        double seqCost = totalSeqs * COST_SEQUENCE;
        double viewCost = totalViews * COST_VIEW;
        double triggerCost = totalTriggers * COST_TRIGGER;
        double plsqlCost = (totalFuncs * COST_FUNCTION) + (totalProcs * COST_PROCEDURE) +
                           (totalPkgs * COST_PACKAGE) + (totalPlSqlLines * COST_PER_PLSQL_LINE);

        double totalUnits = tableCost + constraintCost + indexCost + seqCost + viewCost + triggerCost + plsqlCost;
        report.setTotalMigrationCostUnits(Math.round(totalUnits * 100.0) / 100.0);

        double humanDays = totalUnits / UNITS_PER_HUMAN_DAY;
        report.setEstimatedHumanDays(Math.round(humanDays * 10.0) / 10.0);

        // Difficulty classification
        if (totalUnits < 50) {
            report.setDifficultyLevel("LOW");
        } else if (totalUnits < 200) {
            report.setDifficultyLevel("MEDIUM");
        } else if (totalUnits < 800) {
            report.setDifficultyLevel("HIGH");
        } else {
            report.setDifficultyLevel("CRITICAL");
        }

        report.getObjectCountByType().put("TABLE", totalTables);
        report.getObjectCountByType().put("COLUMN", totalCols);
        report.getObjectCountByType().put("CONSTRAINT", totalConstraints);
        report.getObjectCountByType().put("INDEX", totalIndexes);
        report.getObjectCountByType().put("SEQUENCE", totalSeqs);
        report.getObjectCountByType().put("VIEW", totalViews);
        report.getObjectCountByType().put("TRIGGER", totalTriggers);
        report.getObjectCountByType().put("PLSQL_UNIT", totalFuncs + totalProcs + totalPkgs);

        report.getCostUnitsByType().put("TABLES", tableCost);
        report.getCostUnitsByType().put("CONSTRAINTS", constraintCost);
        report.getCostUnitsByType().put("INDEXES", indexCost);
        report.getCostUnitsByType().put("VIEWS", viewCost);
        report.getCostUnitsByType().put("TRIGGERS", triggerCost);
        report.getCostUnitsByType().put("PLSQL", plsqlCost);

        return report;
    }
}
