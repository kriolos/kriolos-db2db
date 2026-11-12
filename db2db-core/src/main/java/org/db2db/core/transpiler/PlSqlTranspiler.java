package org.db2db.core.transpiler;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Transpiler that translates Oracle PL/SQL stored procedures, functions, packages,
 * triggers, and SQL expressions into PostgreSQL PL/pgSQL code.
 * Implements the core logic found in Ora2Pg's PLSQL.pm.
 */
public class PlSqlTranspiler {

    // Regex patterns for Oracle syntax constructs
    private static final Pattern PATTERN_NVL = Pattern.compile("(?i)\\bNVL\\s*\\(([^,]+),\\s*([^)]+)\\)");
    private static final Pattern PATTERN_NVL2 = Pattern.compile("(?i)\\bNVL2\\s*\\(([^,]+),\\s*([^,]+),\\s*([^)]+)\\)");
    private static final Pattern PATTERN_SYSDATE = Pattern.compile("(?i)\\bSYSDATE\\b");
    private static final Pattern PATTERN_SYSTIMESTAMP = Pattern.compile("(?i)\\bSYSTIMESTAMP\\b");
    private static final Pattern PATTERN_FROM_DUAL = Pattern.compile("(?i)\\s+FROM\\s+DUAL\\b");
    private static final Pattern PATTERN_INSTR = Pattern.compile("(?i)\\bINSTR\\s*\\(([^,]+),\\s*([^)]+)\\)");
    private static final Pattern PATTERN_ADD_MONTHS = Pattern.compile("(?i)\\bADD_MONTHS\\s*\\(([^,]+),\\s*([^)]+)\\)");
    private static final Pattern PATTERN_VARCHAR2 = Pattern.compile("(?i)\\bVARCHAR2\\b");
    private static final Pattern PATTERN_NUMBER = Pattern.compile("(?i)\\bNUMBER\\b");
    private static final Pattern PATTERN_PLS_INTEGER = Pattern.compile("(?i)\\b(PLS_INTEGER|BINARY_INTEGER)\\b");
    private static final Pattern PATTERN_RAISE_APP_ERR = Pattern.compile("(?i)\\bRAISE_APPLICATION_ERROR\\s*\\(\\s*([^,]+)\\s*,\\s*([^)]+)\\s*\\)");
    private static final Pattern PATTERN_SQLCODE = Pattern.compile("(?i)\\bSQLCODE\\b");
    private static final Pattern PATTERN_OUTER_JOIN = Pattern.compile("([\\w.]+)\\s*=\\s*([\\w.]+)\\s*\\(\\+\\)");

    public String transpile(String plsql) {
        if (plsql == null || plsql.isBlank()) {
            return "";
        }

        String result = plsql;

        // 1. Remove Oracle DUAL
        result = PATTERN_FROM_DUAL.matcher(result).replaceAll("");

        // 2. Transpile Date / Time functions
        result = PATTERN_SYSDATE.matcher(result).replaceAll("clock_timestamp()");
        result = PATTERN_SYSTIMESTAMP.matcher(result).replaceAll("clock_timestamp()");
        result = PATTERN_ADD_MONTHS.matcher(result).replaceAll("($1 + ($2 || ' months')::interval)");

        // 3. Transpile NVL & NVL2
        result = PATTERN_NVL.matcher(result).replaceAll("COALESCE($1, $2)");
        result = PATTERN_NVL2.matcher(result).replaceAll("(CASE WHEN $1 IS NOT NULL THEN $2 ELSE $3 END)");

        // 4. Transpile String functions
        result = PATTERN_INSTR.matcher(result).replaceAll("strpos($1, $2)");

        // 5. Transpile DECODE
        result = transpileDecode(result);

        // 6. Transpile Datatypes in PL/SQL headers and variable declarations
        result = PATTERN_VARCHAR2.matcher(result).replaceAll("varchar");
        result = PATTERN_NUMBER.matcher(result).replaceAll("numeric");
        result = PATTERN_PLS_INTEGER.matcher(result).replaceAll("integer");

        // 7. Transpile Exception handling & errors
        result = PATTERN_RAISE_APP_ERR.matcher(result).replaceAll("RAISE EXCEPTION '%', $2");
        result = PATTERN_SQLCODE.matcher(result).replaceAll("SQLSTATE");

        // 8. Transpile outer join (+) syntax hint
        Matcher joinMatcher = PATTERN_OUTER_JOIN.matcher(result);
        if (joinMatcher.find()) {
            result = joinMatcher.replaceAll("/* Transpiled outer-join: use LEFT OUTER JOIN $2 ON $1 = $2 */ $1 = $2");
        }

        return result;
    }

    /**
     * Translates Oracle DECODE(cond, v1, r1, v2, r2, ... default) to PostgreSQL CASE WHEN.
     */
    public String transpileDecode(String input) {
        Pattern decodePattern = Pattern.compile("(?i)\\bDECODE\\s*\\(([^)]+)\\)");
        Matcher matcher = decodePattern.matcher(input);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String argsStr = matcher.group(1);
            String[] args = splitArguments(argsStr);
            if (args.length >= 3) {
                StringBuilder caseStmt = new StringBuilder("(CASE ");
                String expr = args[0].trim();
                int i = 1;
                while (i + 1 < args.length) {
                    caseStmt.append("WHEN ").append(expr).append(" = ").append(args[i].trim())
                            .append(" THEN ").append(args[i + 1].trim()).append(" ");
                    i += 2;
                }
                if (i < args.length) {
                    caseStmt.append("ELSE ").append(args[i].trim()).append(" ");
                }
                caseStmt.append("END)");
                matcher.appendReplacement(sb, Matcher.quoteReplacement(caseStmt.toString()));
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public String wrapAsPostgreSqlFunction(String functionName, String returnType, String arguments, String transpiledBody) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE OR REPLACE FUNCTION ").append(functionName.toLowerCase())
          .append("(").append(arguments != null ? arguments : "").append(")\n")
          .append("RETURNS ").append(returnType != null ? returnType : "void").append("\n")
          .append("LANGUAGE plpgsql\nAS $function$\n")
          .append(transpiledBody.trim()).append("\n")
          .append("$function$;\n");
        return sb.toString();
    }

    public String wrapAsPostgreSqlTrigger(String triggerName, String tableName, String timing, String event, String transpiledBody) {
        StringBuilder sb = new StringBuilder();
        String funcName = "trg_fn_" + triggerName.toLowerCase();

        // Function
        sb.append("CREATE OR REPLACE FUNCTION ").append(funcName).append("()\n")
          .append("RETURNS trigger\nLANGUAGE plpgsql\nAS $function$\n")
          .append("BEGIN\n")
          .append(transpiledBody.trim()).append("\n")
          .append("RETURN NEW;\nEND;\n$function$;\n\n");

        // Trigger definition
        sb.append("CREATE OR REPLACE TRIGGER ").append(triggerName.toLowerCase()).append("\n")
          .append(timing != null ? timing : "BEFORE").append(" ").append(event != null ? event : "INSERT OR UPDATE")
          .append(" ON ").append(tableName.toLowerCase()).append("\n")
          .append("FOR EACH ROW EXECUTE FUNCTION ").append(funcName).append("();\n");

        return sb.toString();
    }

    private String[] splitArguments(String argsStr) {
        // Splits by comma respecting nested parenthesis
        java.util.List<String> list = new java.util.ArrayList<>();
        int level = 0;
        StringBuilder current = new StringBuilder();
        for (char c : argsStr.toCharArray()) {
            if (c == '(') level++;
            else if (c == ')') level--;
            if (c == ',' && level == 0) {
                list.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            list.add(current.toString().trim());
        }
        return list.toArray(new String[0]);
    }
}
