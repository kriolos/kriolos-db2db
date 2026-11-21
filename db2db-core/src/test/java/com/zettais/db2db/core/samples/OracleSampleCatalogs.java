package com.zettais.db2db.core.samples;

import com.zettais.db2db.core.model.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Standard ground-truth sample database catalogs sourced canonically from Oracle Data Modeler samples.
 * Used for rigorous benchmark tests across PostgreSQL and MariaDB migration targets.
 */
public final class OracleSampleCatalogs {

    private OracleSampleCatalogs() {
    }

    /**
     * Builds the complete, canonical Oracle Human Resources (HR) sample schema catalog.
     */
    public static DatabaseCatalog createOracleHrCatalog() {
        var catalog = new DatabaseCatalog();
        catalog.setSchemaName("HR");
        catalog.setDialect(DatabaseDialect.ORACLE);

        // 1. REGIONS
        var regions = new Table("HR", "REGIONS");
        regions.addColumn(createCol("REGION_ID", "NUMBER", 0, 0, 0, false));
        regions.addColumn(createCol("REGION_NAME", "VARCHAR2", 25, 0, 0, true));
        regions.addConstraint(createPk("REG_ID_PK", List.of("REGION_ID")));
        catalog.addTable(regions);

        // 2. COUNTRIES
        var countries = new Table("HR", "COUNTRIES");
        countries.addColumn(createCol("COUNTRY_ID", "CHAR", 2, 0, 0, false));
        countries.addColumn(createCol("COUNTRY_NAME", "VARCHAR2", 40, 0, 0, true));
        countries.addColumn(createCol("REGION_ID", "NUMBER", 0, 0, 0, true));
        countries.addConstraint(createPk("COUNTRY_C_ID_PK", List.of("COUNTRY_ID")));
        countries.addConstraint(createFk("COUNTR_REG_FK", List.of("REGION_ID"), "REGIONS", List.of("REGION_ID")));
        catalog.addTable(countries);

        // 3. LOCATIONS
        var locations = new Table("HR", "LOCATIONS");
        locations.addColumn(createCol("LOCATION_ID", "NUMBER", 0, 4, 0, false));
        locations.addColumn(createCol("STREET_ADDRESS", "VARCHAR2", 40, 0, 0, true));
        locations.addColumn(createCol("POSTAL_CODE", "VARCHAR2", 12, 0, 0, true));
        locations.addColumn(createCol("CITY", "VARCHAR2", 30, 0, 0, false));
        locations.addColumn(createCol("STATE_PROVINCE", "VARCHAR2", 25, 0, 0, true));
        locations.addColumn(createCol("COUNTRY_ID", "CHAR", 2, 0, 0, true));
        locations.addConstraint(createPk("LOC_ID_PK", List.of("LOCATION_ID")));
        locations.addConstraint(createFk("LOC_C_ID_FK", List.of("COUNTRY_ID"), "COUNTRIES", List.of("COUNTRY_ID")));
        catalog.addTable(locations);

        // 4. DEPARTMENTS
        var departments = new Table("HR", "DEPARTMENTS");
        departments.addColumn(createCol("DEPARTMENT_ID", "NUMBER", 0, 4, 0, false));
        departments.addColumn(createCol("DEPARTMENT_NAME", "VARCHAR2", 30, 0, 0, false));
        departments.addColumn(createCol("MANAGER_ID", "NUMBER", 0, 6, 0, true));
        departments.addColumn(createCol("LOCATION_ID", "NUMBER", 0, 4, 0, true));
        departments.addConstraint(createPk("DEPT_ID_PK", List.of("DEPARTMENT_ID")));
        departments.addConstraint(createFk("DEPT_LOC_FK", List.of("LOCATION_ID"), "LOCATIONS", List.of("LOCATION_ID")));
        departments.addConstraint(createFk("DEPT_MGR_FK", List.of("MANAGER_ID"), "EMPLOYEES", List.of("EMPLOYEE_ID")));
        catalog.addTable(departments);

        // 5. JOBS
        var jobs = new Table("HR", "JOBS");
        jobs.addColumn(createCol("JOB_ID", "VARCHAR2", 10, 0, 0, false));
        jobs.addColumn(createCol("JOB_TITLE", "VARCHAR2", 35, 0, 0, false));
        jobs.addColumn(createCol("MIN_SALARY", "NUMBER", 0, 6, 0, true));
        jobs.addColumn(createCol("MAX_SALARY", "NUMBER", 0, 6, 0, true));
        jobs.addConstraint(createPk("JOB_ID_PK", List.of("JOB_ID")));
        catalog.addTable(jobs);

        // 6. EMPLOYEES
        var employees = new Table("HR", "EMPLOYEES");
        employees.addColumn(createCol("EMPLOYEE_ID", "NUMBER", 0, 6, 0, false));
        employees.addColumn(createCol("FIRST_NAME", "VARCHAR2", 20, 0, 0, true));
        employees.addColumn(createCol("LAST_NAME", "VARCHAR2", 25, 0, 0, false));
        employees.addColumn(createCol("EMAIL", "VARCHAR2", 25, 0, 0, false));
        employees.addColumn(createCol("PHONE_NUMBER", "VARCHAR2", 20, 0, 0, true));
        employees.addColumn(createCol("HIRE_DATE", "DATE", 0, 0, 0, false));
        employees.addColumn(createCol("JOB_ID", "VARCHAR2", 10, 0, 0, false));
        employees.addColumn(createCol("SALARY", "NUMBER", 0, 8, 2, true));
        employees.addColumn(createCol("COMMISSION_PCT", "NUMBER", 0, 2, 2, true));
        employees.addColumn(createCol("MANAGER_ID", "NUMBER", 0, 6, 0, true));
        employees.addColumn(createCol("DEPARTMENT_ID", "NUMBER", 0, 4, 0, true));

        employees.addConstraint(createPk("EMP_EMP_ID_PK", List.of("EMPLOYEE_ID")));
        employees.addConstraint(createUk("EMP_EMAIL_UK", List.of("EMAIL")));
        employees.addConstraint(createCheck("EMP_SALARY_MIN", "salary > 0"));
        employees.addConstraint(createFk("EMP_DEPT_FK", List.of("DEPARTMENT_ID"), "DEPARTMENTS", List.of("DEPARTMENT_ID")));
        employees.addConstraint(createFk("EMP_JOB_FK", List.of("JOB_ID"), "JOBS", List.of("JOB_ID")));
        employees.addConstraint(createFk("EMP_MGR_FK", List.of("MANAGER_ID"), "EMPLOYEES", List.of("EMPLOYEE_ID")));
        catalog.addTable(employees);

        // 7. JOB_HISTORY
        var jobHistory = new Table("HR", "JOB_HISTORY");
        jobHistory.addColumn(createCol("EMPLOYEE_ID", "NUMBER", 0, 6, 0, false));
        jobHistory.addColumn(createCol("START_DATE", "DATE", 0, 0, 0, false));
        jobHistory.addColumn(createCol("END_DATE", "DATE", 0, 0, 0, false));
        jobHistory.addColumn(createCol("JOB_ID", "VARCHAR2", 10, 0, 0, false));
        jobHistory.addColumn(createCol("DEPARTMENT_ID", "NUMBER", 0, 4, 0, true));

        jobHistory.addConstraint(createPk("JHIST_EMP_ID_ST_DATE_PK", List.of("EMPLOYEE_ID", "START_DATE")));
        jobHistory.addConstraint(createCheck("JHIST_DATE_INTERVAL", "end_date > start_date"));
        jobHistory.addConstraint(createFk("JHIST_EMP_FK", List.of("EMPLOYEE_ID"), "EMPLOYEES", List.of("EMPLOYEE_ID")));
        jobHistory.addConstraint(createFk("JHIST_JOB_FK", List.of("JOB_ID"), "JOBS", List.of("JOB_ID")));
        jobHistory.addConstraint(createFk("JHIST_DEPT_FK", List.of("DEPARTMENT_ID"), "DEPARTMENTS", List.of("DEPARTMENT_ID")));
        catalog.addTable(jobHistory);

        // Sequences
        catalog.addSequence(createSeq("LOCATIONS_SEQ", "3300", "100", "9900"));
        catalog.addSequence(createSeq("DEPARTMENTS_SEQ", "280", "10", "9990"));
        catalog.addSequence(createSeq("EMPLOYEES_SEQ", "207", "1", null));

        return catalog;
    }

    private static Column createCol(String name, String type, int length, int precision, int scale, boolean nullable) {
        var col = new Column(name, type, nullable);
        col.setLength(length);
        col.setPrecision(precision);
        col.setScale(scale);
        return col;
    }

    private static Constraint createPk(String name, List<String> columns) {
        var c = new Constraint(name, Constraint.Type.PRIMARY_KEY);
        c.setColumns(columns);
        return c;
    }

    private static Constraint createUk(String name, List<String> columns) {
        var c = new Constraint(name, Constraint.Type.UNIQUE);
        c.setColumns(columns);
        return c;
    }

    private static Constraint createCheck(String name, String condition) {
        var c = new Constraint(name, Constraint.Type.CHECK);
        c.setCheckCondition(condition);
        return c;
    }

    private static Constraint createFk(String name, List<String> cols, String refTable, List<String> refCols) {
        var c = new Constraint(name, Constraint.Type.FOREIGN_KEY);
        c.setColumns(cols);
        c.setReferencedTable(refTable);
        c.setReferencedColumns(refCols);
        return c;
    }

    private static Sequence createSeq(String name, String startVal, String incrementBy, String maxVal) {
        var seq = new Sequence("HR", name);
        seq.setLastNumber(new BigDecimal(startVal));
        seq.setIncrementBy(new BigDecimal(incrementBy));
        if (maxVal != null) {
            seq.setMaxValue(new BigDecimal(maxVal));
        }
        seq.setCacheSize(0);
        return seq;
    }
}
