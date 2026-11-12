package org.db2db.core.extractor;

import org.db2db.core.model.DatabaseCatalog;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Service Provider Interface (SPI) for extracting database schema metadata.
 */
public interface MetadataExtractor {

    /**
     * Extracts full database catalog metadata from the connected database.
     *
     * @param connection the active database JDBC connection
     * @param schema the target schema name to inspect
     * @return populated DatabaseCatalog
     * @throws SQLException on database extraction errors
     */
    DatabaseCatalog extractCatalog(Connection connection, String schema) throws SQLException;
}
