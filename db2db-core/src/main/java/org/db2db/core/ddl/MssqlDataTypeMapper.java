package org.db2db.core.ddl;

import org.db2db.core.model.Column;

/**
 * Backward compatibility wrapper delegating to org.db2db.core.ddl.dialect.MssqlDataTypeMapper.
 */
public class MssqlDataTypeMapper {

    private final org.db2db.core.ddl.dialect.MssqlDataTypeMapper delegate = new org.db2db.core.ddl.dialect.MssqlDataTypeMapper();

    public String mapToPostgreSql(Column column) {
        return delegate.mapToPostgreSql(column, null);
    }
}
