package com.zettais.db2db.core.transpiler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlSqlTranspilerTest {

    private PlSqlTranspiler transpiler;

    @BeforeEach
    void setUp() {
        transpiler = new PlSqlTranspiler();
    }

    @Test
    void testNvlAndNvl2Transpilation() {
        String plsql = "SELECT NVL(commission, 0), NVL2(manager_id, 'HAS_MGR', 'NO_MGR') FROM DUAL;";
        String pgsql = transpiler.transpile(plsql);

        assertThat(pgsql).contains("COALESCE(commission, 0)");
        assertThat(pgsql).contains("(CASE WHEN manager_id IS NOT NULL THEN 'HAS_MGR' ELSE 'NO_MGR' END)");
        assertThat(pgsql).doesNotContain("FROM DUAL");
    }

    @Test
    void testSysdateAndDateFunctions() {
        String plsql = "UPDATE orders SET order_date = SYSDATE, due_date = ADD_MONTHS(SYSDATE, 6);";
        String pgsql = transpiler.transpile(plsql);

        assertThat(pgsql).contains("clock_timestamp()");
        assertThat(pgsql).contains("(clock_timestamp() + (6 || ' months')::interval)");
    }

    @Test
    void testDecodeTranspilation() {
        String plsql = "SELECT DECODE(status, 'O', 'OPEN', 'C', 'CLOSED', 'UNKNOWN') FROM orders;";
        String pgsql = transpiler.transpile(plsql);

        assertThat(pgsql).contains("(CASE WHEN status = 'O' THEN 'OPEN' WHEN status = 'C' THEN 'CLOSED' ELSE 'UNKNOWN' END)");
    }

    @Test
    void testTypeAndExceptionTranspilation() {
        String plsql = """
            DECLARE
                v_name VARCHAR2(50);
                v_salary NUMBER(10,2);
            BEGIN
                IF v_salary < 0 THEN
                    RAISE_APPLICATION_ERROR(-20001, 'Salary cannot be negative');
                END IF;
            EXCEPTION
                WHEN OTHERS THEN
                    dbms_output.put_line(SQLCODE);
            END;
            """;
        String pgsql = transpiler.transpile(plsql);

        assertThat(pgsql).contains("v_name varchar(50);");
        assertThat(pgsql).contains("v_salary numeric(10,2);");
        assertThat(pgsql).contains("RAISE EXCEPTION '%', 'Salary cannot be negative'");
        assertThat(pgsql).contains("SQLSTATE");
    }

    @Test
    void testFunctionWrapping() {
        String body = "BEGIN\n    RETURN 42;\nEND;";
        String func = transpiler.wrapAsPostgreSqlFunction("calc_tax", "integer", "p_id integer", body);

        assertThat(func).contains("CREATE OR REPLACE FUNCTION calc_tax(p_id integer)");
        assertThat(func).contains("RETURNS integer");
        assertThat(func).contains("LANGUAGE plpgsql");
        assertThat(func).contains("RETURN 42;");
    }
}
