package top.zymnb.mcpservertest.security;

import lombok.Data;
import org.springframework.stereotype.Component;
import java.util.regex.Pattern;

/**
 * SQL 安全检查器
 * 对 SQL 语句进行安全性检查，防止危险操作
 *
 * 检查规则：
 * 1. DELETE 和 UPDATE 语句必须包含 WHERE 条件
 * 2. DROP TABLE、TRUNCATE、ALTER TABLE 等危险操作需要二次确认
 */
@Component
public class SqlSecurityChecker {

    /** DELETE 语句匹配模式 */
    private static final Pattern DELETE_PATTERN = Pattern.compile(
            "^\\s*DELETE\\s+FROM\\s+", Pattern.CASE_INSENSITIVE);
    /** UPDATE 语句匹配模式 */
    private static final Pattern UPDATE_PATTERN = Pattern.compile(
            "^\\s*UPDATE\\s+", Pattern.CASE_INSENSITIVE);
    /** WHERE 子句匹配模式 */
    private static final Pattern WHERE_PATTERN = Pattern.compile(
            "\\bWHERE\\b", Pattern.CASE_INSENSITIVE);
    /** DROP TABLE 语句匹配模式 */
    private static final Pattern DROP_TABLE_PATTERN = Pattern.compile(
            "^\\s*DROP\\s+TABLE\\b", Pattern.CASE_INSENSITIVE);
    /** TRUNCATE 语句匹配模式 */
    private static final Pattern TRUNCATE_PATTERN = Pattern.compile(
            "^\\s*TRUNCATE\\b", Pattern.CASE_INSENSITIVE);
    /** ALTER TABLE 语句匹配模式 */
    private static final Pattern ALTER_TABLE_PATTERN = Pattern.compile(
            "^\\s*ALTER\\s+TABLE\\b", Pattern.CASE_INSENSITIVE);

    /**
     * 检查 SQL 语句的安全性
     *
     * @param sql 待检查的 SQL 语句
     * @return 检查结果，包含是否安全、是否需要确认等信息
     */
    public CheckResult check(String sql) {
        CheckResult result = new CheckResult();
        result.setSafe(true);
        result.setNeedsConfirmation(false);

        String trimmedSql = sql.trim();

        // 检查DELETE是否有WHERE条件
        if (DELETE_PATTERN.matcher(trimmedSql).find()) {
            if (!WHERE_PATTERN.matcher(trimmedSql).find()) {
                result.setSafe(false);
                result.setMessage("DELETE语句必须包含WHERE条件");
                return result;
            }
        }

        // 检查UPDATE是否有WHERE条件
        if (UPDATE_PATTERN.matcher(trimmedSql).find()) {
            if (!WHERE_PATTERN.matcher(trimmedSql).find()) {
                result.setSafe(false);
                result.setMessage("UPDATE语句必须包含WHERE条件");
                return result;
            }
        }

        // 检查需要二次确认的DDL操作
        if (DROP_TABLE_PATTERN.matcher(trimmedSql).find()) {
            result.setNeedsConfirmation(true);
            result.setConfirmationType("DROP_TABLE");
            result.setMessage("DROP TABLE操作需要二次确认");
        } else if (TRUNCATE_PATTERN.matcher(trimmedSql).find()) {
            result.setNeedsConfirmation(true);
            result.setConfirmationType("TRUNCATE");
            result.setMessage("TRUNCATE操作需要二次确认");
        } else if (ALTER_TABLE_PATTERN.matcher(trimmedSql).find()) {
            result.setNeedsConfirmation(true);
            result.setConfirmationType("ALTER_TABLE");
            result.setMessage("ALTER TABLE操作需要二次确认");
        }

        return result;
    }

    /**
     * 安全检查结果
     * 封装 SQL 安全检查的结果信息
     */
    @Data
    public static class CheckResult {
        /** 是否安全（false 表示禁止执行） */
        private boolean safe;
        /** 是否需要二次确认 */
        private boolean needsConfirmation;
        /** 确认类型（DROP_TABLE、TRUNCATE、ALTER_TABLE） */
        private String confirmationType;
        /** 提示消息 */
        private String message;
    }
}
