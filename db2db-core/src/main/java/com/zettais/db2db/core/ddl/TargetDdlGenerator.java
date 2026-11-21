package com.zettais.db2db.core.ddl;

import com.zettais.db2db.core.model.*;

/**
 * Strategy contract for generating target database DDL (PostgreSQL, MariaDB).
 * Uses sealed hierarchy for exhaustive pattern matching and type-safe extensibility.
 */
public sealed interface TargetDdlGenerator permits PostgreSqlDdlGenerator, MariaDbDdlGenerator {

    /**
     * Target database dialect for this generator.
     */
    DatabaseDialect getTargetDialect();

    /**
     * Generates table creation DDL including columns, inline primary keys, checks, and table options.
     */
    String generateTableDdl(Table table);

    /**
     * Generates deferred foreign key constraint alterations to prevent circular dependency deadlocks.
     */
    String generateDeferredForeignKeys(Table table);

    /**
     * Generates secondary and spatial indexes for the given table.
     */
    String generateIndexesDdl(Table table);

    /**
     * Generates sequence creation statement.
     */
    String generateSequenceDdl(Sequence seq);

    /**
     * Generates view creation statement.
     */
    String generateViewDdl(View view);
}
