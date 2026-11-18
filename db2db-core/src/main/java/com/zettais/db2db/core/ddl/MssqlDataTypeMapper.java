package com.zettais.db2db.core.ddl;

import com.zettais.db2db.core.model.Column;

/**
 * Backward compatibility wrapper delegating to com.zettais.db2db.core.ddl.dialect.MssqlDataTypeMapper.
 */
public class MssqlDataTypeMapper {

    private final com.zettais.db2db.core.ddl.dialect.MssqlDataTypeMapper delegate = new com.zettais.db2db.core.ddl.dialect.MssqlDataTypeMapper();

    public String mapToPostgreSql(Column column) {
        return delegate.mapToPostgreSql(column, null);
    }
}
