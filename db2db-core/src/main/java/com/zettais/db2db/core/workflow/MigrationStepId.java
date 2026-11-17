package com.zettais.db2db.core.workflow;

/**
 * The 12 industrial migration lifecycle steps supported by DB2DB by ZettaIS.
 */
public enum MigrationStepId {
    PRE_FLIGHT_CHECK("Pre-Flight Check", "Verifies network connectivity, credentials, database versions, and required target extensions"),
    SCHEMA_ASSESSMENT("Schema Assessment", "Inspects metadata catalog, calculates migration complexity score, invalid objects, and keywords"),
    PRE_SCHEMA_SETUP("Target Schema Setup", "Initializes PostgreSQL target schema and enables extensions (postgis, btree_gin, uuid-ossp)"),
    DDL_EXPORT("DDL Generation", "Generates PostgreSQL-compatible DDL scripts for tables, sequences, views, and types"),
    DDL_APPLY("DDL Schema Application", "Applies structural DDL (tables without deferred FKs/indexes) against target PostgreSQL"),
    PARALLEL_DATA_STREAMING("Parallel Data Streaming", "High-throughput data extraction and streaming into PostgreSQL via Virtual Threads"),
    POST_DATA_INDEXES("Index Creation", "Builds primary keys, unique, secondary, and spatial/GIN indexes post-load for speed"),
    FOREIGN_KEYS("Foreign Key Constraints", "Applies deferred foreign key relationships and integrity constraints"),
    PROCEDURAL_CODE("PL/SQL Transpilation & Deployment", "Transpiles Oracle stored procedures, functions, packages to PL/pgSQL"),
    SEQUENCE_SYNC("Sequence Synchronization", "Synchronizes sequence counters with table maximum IDs in PostgreSQL"),
    DATA_VALIDATION_AUDIT("Data Validation Audit", "Executes row count and checksum reconciliation between Source and Target"),
    FINAL_AUDIT_REPORT("Final Audit Report", "Compiles comprehensive JSON and executive HTML audit migration reports");

    private final String displayName;
    private final String description;

    MigrationStepId(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
