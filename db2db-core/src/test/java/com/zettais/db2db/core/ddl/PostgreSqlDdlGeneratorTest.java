package com.zettais.db2db.core.ddl;

import com.zettais.db2db.core.config.Db2DbConfig;
import com.zettais.db2db.core.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PostgreSqlDdlGeneratorTest {

    @Test
    void testTableDdlGenerationWithConstraints() {
        Db2DbConfig config = new Db2DbConfig();
        config.setTargetSchema("public");
        config.setFkeyAddDeferred(true);

        PostgreSqlDdlGenerator generator = new PostgreSqlDdlGenerator(config);

        Table table = new Table("HR", "EMPLOYEES");
        Column c1 = new Column("EMP_ID", "NUMBER", false);
        c1.setPrecision(9);
        c1.setScale(0);

        Column c2 = new Column("EMAIL", "VARCHAR2", false);
        c2.setLength(100);

        Column c3 = new Column("HIRE_DATE", "DATE", false);
        c3.setDefaultValue("SYSDATE");

        table.setColumns(List.of(c1, c2, c3));

        Constraint pk = new Constraint("PK_EMP", Constraint.Type.PRIMARY_KEY);
        pk.setColumns(List.of("EMP_ID"));

        Constraint fk = new Constraint("FK_DEPT", Constraint.Type.FOREIGN_KEY);
        fk.setColumns(List.of("DEPT_ID"));
        fk.setReferencedTable("DEPARTMENTS");
        fk.setReferencedColumns(List.of("DEPT_ID"));

        table.setConstraints(List.of(pk, fk));

        String ddl = generator.generateTableDdl(table);

        assertThat(ddl).contains("CREATE TABLE public.employees");
        assertThat(ddl).contains("emp_id integer NOT NULL");
        assertThat(ddl).contains("email varchar(100) NOT NULL");
        assertThat(ddl).contains("hire_date timestamp(0) without time zone DEFAULT clock_timestamp() NOT NULL");
        assertThat(ddl).contains("CONSTRAINT pk_emp PRIMARY KEY (emp_id)");

        // Deferred foreign keys generated separately
        String fkDdl = generator.generateDeferredForeignKeys(table);
        assertThat(fkDdl).contains("ALTER TABLE public.employees ADD CONSTRAINT fk_dept FOREIGN KEY (dept_id) REFERENCES public.departments (dept_id)");
    }

    @Test
    void testSequenceDdlGeneration() {
        Db2DbConfig config = new Db2DbConfig();
        config.setTargetSchema("public");
        PostgreSqlDdlGenerator generator = new PostgreSqlDdlGenerator(config);

        Sequence seq = new Sequence("HR", "EMP_SEQ");
        String ddl = generator.generateSequenceDdl(seq);

        assertThat(ddl).contains("CREATE SEQUENCE public.emp_seq");
        assertThat(ddl).contains("INCREMENT BY 1");
    }
}
