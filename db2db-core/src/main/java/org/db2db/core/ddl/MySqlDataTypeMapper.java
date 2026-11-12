package org.db2db.core.ddl;

import org.db2db.core.model.Column;

/**
 * Backward compatibility wrapper delegating to org.db2db.core.ddl.dialect.MySqlDataTypeMapper.
 */
public class MySqlDataTypeMapper {

    private final org.db2db.core.ddl.dialect.MySqlDataTypeMapper delegate = new org.db2db.core.ddl.dialect.MySqlDataTypeMapper();

    public String mapToPostgreSql(Column column) {
        return delegate.mapToPostgreSql(column, null);
    }
}
