package top.zymnb.mcpservertest.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import top.zymnb.mcpservertest.model.*;
import top.zymnb.mcpservertest.security.SqlSecurityChecker;

import java.sql.SQLException;
import java.util.*;

/**
 * MCP工具服务
 * <p>
 * 提供6个MCP工具，供AI模型调用以执行数据库操作。
 * 所有工具方法都使用 @Tool 注解标记，会被自动注册为MCP工具。
 * </p>
 * <p>
 * 工具列表：
 * <ul>
 *   <li>executeQuery: 执行查询SQL，返回结果集</li>
 *   <li>executeWrite: 执行写操作预览，返回确认ID</li>
 *   <li>confirmWrite: 确认执行写操作</li>
 *   <li>executeBatch: 批量执行SQL（事务）</li>
 *   <li>getMetadata: 查询数据库元数据</li>
 *   <li>executeDDL: 执行DDL语句</li>
 * </ul>
 * </p>
 * <p>
 * 安全特性：
 * <ul>
 *   <li>所有写操作都经过安全检查</li>
 *   <li>危险操作需要二次确认</li>
 *   <li>自动记录审计日志</li>
 * </ul>
 * </p>
 *
 * @author zym
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpService {

    /** SQL执行服务，用于执行实际的SQL操作 */
    private final SqlExecutionService sqlExecutionService;

    /** 元数据服务，用于查询数据库结构信息 */
    private final MetadataService metadataService;

    /** 写操作确认服务，用于实现两阶段确认机制 */
    private final WriteConfirmService writeConfirmService;

    /** SQL安全检查器，用于检查SQL语句的安全性 */
    private final SqlSecurityChecker securityChecker;

    /**
     * MCP工具：执行查询SQL语句
     * <p>
     * 执行SELECT查询，返回查询结果。支持参数化查询、结果行数限制和超时控制。
     * </p>
     *
     * @param dbType 数据库类型（mysql/postgresql/oracle/sqlserver/kingbase）
     * @param host 数据库主机地址
     * @param port 数据库端口
     * @param username 用户名
     * @param password 密码
     * @param database 数据库名
     * @param sql SQL查询语句
     * @param params SQL参数列表（可选）
     * @param limit 结果行数限制（可选，默认1000）
     * @param timeout 超时时间秒（可选，默认30）
     * @param useSsl 是否使用SSL（可选）
     * @return 执行结果Map，包含success、message、data、rowCount、executionTime等字段
     */
    @Tool(name = "executeQuery", description = "执行查询SQL语句，返回查询结果")
    public Map<String, Object> executeQuery(
            @ToolParam(description = "数据库类型: mysql/postgresql/oracle/sqlserver/kingbase") String dbType,
            @ToolParam(description = "数据库主机地址") String host,
            @ToolParam(description = "数据库端口") Integer port,
            @ToolParam(description = "用户名") String username,
            @ToolParam(description = "密码") String password,
            @ToolParam(description = "数据库名") String database,
            @ToolParam(description = "SQL查询语句") String sql,
            @ToolParam(description = "SQL参数列表(可选)") List<Object> params,
            @ToolParam(description = "结果行数限制(可选,默认1000)") Integer limit,
            @ToolParam(description = "超时时间秒(可选,默认30)") Integer timeout,
            @ToolParam(description = "是否使用SSL(可选)") Boolean useSsl) {

        ConnectionInfo connInfo = buildConnectionInfo(dbType, host, port, username, password, database, useSsl);
        Object[] paramArray = params != null ? params.toArray() : null;

        SqlResult result = sqlExecutionService.executeQuery(connInfo, sql, paramArray, limit, timeout);
        return convertResult(result);
    }

    /**
     * MCP工具：执行写操作预览
     * <p>
     * 创建写操作（INSERT/UPDATE/DELETE）的预览，返回确认ID。
     * 这是两阶段确认机制的第一步，需要使用confirmWrite工具确认执行。
     * </p>
     * <p>
     * 安全检查：
     * <ul>
     *   <li>禁止无WHERE条件的DELETE/UPDATE</li>
     *   <li>预览信息30分钟有效期</li>
     * </ul>
     * </p>
     *
     * @param dbType 数据库类型（mysql/postgresql/oracle/sqlserver/kingbase）
     * @param host 数据库主机地址
     * @param port 数据库端口
     * @param username 用户名
     * @param password 密码
     * @param database 数据库名
     * @param sql SQL写操作语句
     * @param params SQL参数列表（可选）
     * @param useSsl 是否使用SSL（可选）
     * @return 执行结果Map，包含success、confirmId、sql、operationType、expireMinutes等字段
     */
    @Tool(name = "executeWrite", description = "执行写操作SQL(INSERT/UPDATE/DELETE)的预览，返回确认ID")
    public Map<String, Object> executeWrite(
            @ToolParam(description = "数据库类型: mysql/postgresql/oracle/sqlserver/kingbase") String dbType,
            @ToolParam(description = "数据库主机地址") String host,
            @ToolParam(description = "数据库端口") Integer port,
            @ToolParam(description = "用户名") String username,
            @ToolParam(description = "密码") String password,
            @ToolParam(description = "数据库名") String database,
            @ToolParam(description = "SQL写操作语句") String sql,
            @ToolParam(description = "SQL参数列表(可选)") List<Object> params,
            @ToolParam(description = "是否使用SSL(可选)") Boolean useSsl) {

        // 安全检查
        SqlSecurityChecker.CheckResult checkResult = securityChecker.check(sql);
        if (!checkResult.isSafe()) {
            return Map.of(
                    "success", false,
                    "message", checkResult.getMessage(),
                    "friendlyMessage", "安全检查未通过: " + checkResult.getMessage()
            );
        }

        ConnectionInfo connInfo = buildConnectionInfo(dbType, host, port, username, password, database, useSsl);
        Object[] paramArray = params != null ? params.toArray() : null;
        String opType = detectOperationType(sql);

        WritePreview preview = writeConfirmService.createPreview(connInfo, sql, paramArray, opType);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("confirmId", preview.getConfirmId());
        response.put("sql", preview.getSql());
        response.put("operationType", preview.getOperationType());
        response.put("message", "预览已创建，请使用confirmWrite工具确认执行");
        response.put("expireMinutes", 30);
        return response;
    }

    /**
     * MCP工具：确认执行写操作
     * <p>
     * 使用executeWrite返回的确认ID执行实际的写操作。
     * 这是两阶段确认机制的第二步。
     * </p>
     * <p>
     * 注意事项：
     * <ul>
     *   <li>确认ID只能使用一次</li>
     *   <li>确认ID有30分钟有效期</li>
     *   <li>过期或无效的确认ID会返回错误</li>
     * </ul>
     * </p>
     *
     * @param confirmId executeWrite返回的确认ID
     * @param timeout 超时时间秒（可选，默认30）
     * @return 执行结果Map，包含success、message、affectedRows、executionTime等字段
     */
    @Tool(name = "confirmWrite", description = "确认执行写操作，需要先调用executeWrite获取confirmId")
    public Map<String, Object> confirmWrite(
            @ToolParam(description = "executeWrite返回的确认ID") String confirmId,
            @ToolParam(description = "超时时间秒(可选,默认30)") Integer timeout) {

        SqlResult result = writeConfirmService.confirmAndExecute(confirmId, timeout);
        return convertResult(result);
    }

    /**
     * MCP工具：批量执行SQL语句
     * <p>
     * 在同一事务中执行多条SQL语句，任何一条失败则全部回滚。
     * 适用于需要保证原子性的批量操作。
     * </p>
     * <p>
     * 安全检查：
     * <ul>
     *   <li>每条SQL都会进行安全检查</li>
     *   <li>禁止无WHERE条件的DELETE/UPDATE</li>
     *   <li>任何一条SQL不安全则全部拒绝</li>
     * </ul>
     * </p>
     *
     * @param dbType 数据库类型（mysql/postgresql/oracle/sqlserver/kingbase）
     * @param host 数据库主机地址
     * @param port 数据库端口
     * @param username 用户名
     * @param password 密码
     * @param database 数据库名
     * @param sqlList SQL语句列表
     * @param timeout 超时时间秒（可选，默认30）
     * @param useSsl 是否使用SSL（可选）
     * @return 执行结果Map，包含success、message、affectedRows、executionTime等字段
     */
    @Tool(name = "executeBatch", description = "批量执行多条SQL语句，在同一事务中执行，失败则全部回滚")
    public Map<String, Object> executeBatch(
            @ToolParam(description = "数据库类型: mysql/postgresql/oracle/sqlserver/kingbase") String dbType,
            @ToolParam(description = "数据库主机地址") String host,
            @ToolParam(description = "数据库端口") Integer port,
            @ToolParam(description = "用户名") String username,
            @ToolParam(description = "密码") String password, 
            @ToolParam(description = "数据库名") String database,
            @ToolParam(description = "SQL语句列表") List<String> sqlList,
            @ToolParam(description = "超时时间秒(可选,默认30)") Integer timeout,
            @ToolParam(description = "是否使用SSL(可选)") Boolean useSsl) {

        // 安全检查每条SQL
        for (String sql : sqlList) {
            SqlSecurityChecker.CheckResult checkResult = securityChecker.check(sql);
            if (!checkResult.isSafe()) {
                return Map.of(
                        "success", false,
                        "message", checkResult.getMessage(),
                        "friendlyMessage", "安全检查未通过: " + checkResult.getMessage(),
                        "failedSql", sql
                );
            }
        }

        ConnectionInfo connInfo = buildConnectionInfo(dbType, host, port, username, password, database, useSsl);
        SqlResult result = sqlExecutionService.executeBatch(connInfo, sqlList, timeout);
        return convertResult(result);
    }

    /**
     * MCP工具：查询数据库元数据
     * <p>
     * 查询数据库的结构信息，包括表列表、表结构、索引、约束等。
     * </p>
     * <p>
     * 使用方式：
     * <ul>
     *   <li>tableName为空：返回数据库中所有表名列表</li>
     *   <li>tableName不为空：返回指定表的详细元数据（列、索引、约束）</li>
     * </ul>
     * </p>
     *
     * @param dbType 数据库类型（mysql/postgresql/oracle/sqlserver/kingbase）
     * @param host 数据库主机地址
     * @param port 数据库端口
     * @param username 用户名
     * @param password 密码
     * @param database 数据库名
     * @param tableName 表名（可选，不填则返回所有表名）
     * @param useSsl 是否使用SSL（可选）
     * @return 执行结果Map，包含success和tables（表名列表）或metadata（表详细信息）
     */
    @Tool(name = "getMetadata", description = "查询数据库元数据，包括表结构、索引、约束、注释等信息")
    public Map<String, Object> getMetadata(
            @ToolParam(description = "数据库类型: mysql/postgresql/oracle/sqlserver/kingbase") String dbType,
            @ToolParam(description = "数据库主机地址") String host,
            @ToolParam(description = "数据库端口") Integer port,
            @ToolParam(description = "用户名") String username,
            @ToolParam(description = "密码") String password,
            @ToolParam(description = "数据库名") String database,
            @ToolParam(description = "表名(可选,不填则返回所有表名)") String tableName,
            @ToolParam(description = "是否使用SSL(可选)") Boolean useSsl) {

        ConnectionInfo connInfo = buildConnectionInfo(dbType, host, port, username, password, database, useSsl);

        try {
            if (tableName == null || tableName.isEmpty()) {
                List<String> tables = metadataService.getTables(connInfo);
                return Map.of("success", true, "tables", tables);
            } else {
                TableMetadata metadata = metadataService.getTableMetadata(connInfo, tableName);
                return Map.of("success", true, "metadata", metadata);
            }
        } catch (SQLException e) {
            return Map.of(
                    "success", false,
                    "message", e.getMessage(),
                    "friendlyMessage", "查询元数据失败: " + e.getMessage()
            );
        }
    }

    /**
     * MCP工具：执行DDL语句
     * <p>
     * 执行数据库定义语言（DDL）语句，如CREATE、ALTER、DROP等。
     * </p>
     * <p>
     * 安全机制：
     * <ul>
     *   <li>DROP TABLE：需要confirmed=true</li>
     *   <li>TRUNCATE：需要confirmed=true</li>
     *   <li>ALTER TABLE：需要confirmed=true</li>
     *   <li>其他DDL：直接执行</li>
     * </ul>
     * </p>
     *
     * @param dbType 数据库类型（mysql/postgresql/oracle/sqlserver/kingbase）
     * @param host 数据库主机地址
     * @param port 数据库端口
     * @param username 用户名
     * @param password 密码
     * @param database 数据库名
     * @param sql DDL语句
     * @param confirmed 确认执行危险操作（DROP/TRUNCATE/ALTER需要设为true）
     * @param timeout 超时时间秒（可选，默认30）
     * @param useSsl 是否使用SSL（可选）
     * @return 执行结果Map，包含success、message、needsConfirmation、confirmationType等字段
     */
    @Tool(name = "executeDDL", description = "执行DDL语句(CREATE/ALTER/DROP等)，危险操作需要确认")
    public Map<String, Object> executeDDL(
            @ToolParam(description = "数据库类型: mysql/postgresql/oracle/sqlserver/kingbase") String dbType,
            @ToolParam(description = "数据库主机地址") String host,
            @ToolParam(description = "数据库端口") Integer port,
            @ToolParam(description = "用户名") String username,
            @ToolParam(description = "密码") String password,
            @ToolParam(description = "数据库名") String database,
            @ToolParam(description = "DDL语句") String sql,
            @ToolParam(description = "确认执行危险操作(DROP/TRUNCATE/ALTER需要设为true)") Boolean confirmed,
            @ToolParam(description = "超时时间秒(可选,默认30)") Integer timeout,
            @ToolParam(description = "是否使用SSL(可选)") Boolean useSsl) {

        SqlSecurityChecker.CheckResult checkResult = securityChecker.check(sql);

        if (checkResult.isNeedsConfirmation() && !Boolean.TRUE.equals(confirmed)) {
            return Map.of(
                    "success", false,
                    "needsConfirmation", true,
                    "confirmationType", checkResult.getConfirmationType(),
                    "message", checkResult.getMessage(),
                    "friendlyMessage", "此操作需要确认，请将confirmed参数设为true后重新执行"
            );
        }

        ConnectionInfo connInfo = buildConnectionInfo(dbType, host, port, username, password, database, useSsl);
        SqlResult result = sqlExecutionService.executeUpdate(connInfo, sql, null, timeout);
        return convertResult(result);
    }

    /**
     * 构建数据库连接信息对象
     * <p>
     * 将分散的连接参数封装为ConnectionInfo对象
     * </p>
     *
     * @param dbType 数据库类型
     * @param host 主机地址
     * @param port 端口
     * @param username 用户名
     * @param password 密码
     * @param database 数据库名
     * @param useSsl 是否使用SSL
     * @return 连接信息对象
     */
    private ConnectionInfo buildConnectionInfo(String dbType, String host, Integer port,
                                                String username, String password, String database, Boolean useSsl) {
        return ConnectionInfo.builder()
                .dbType(dbType)
                .host(host)
                .port(port)
                .username(username)
                .password(password)
                .database(database)
                .useSsl(useSsl)
                .build();
    }

    /**
     * 检测SQL操作类型
     * <p>
     * 根据SQL语句的开头关键字判断操作类型
     * </p>
     *
     * @param sql SQL语句
     * @return 操作类型（INSERT/UPDATE/DELETE/UNKNOWN）
     */
    private String detectOperationType(String sql) {
        String upper = sql.trim().toUpperCase();
        if (upper.startsWith("INSERT")) return "INSERT";
        if (upper.startsWith("UPDATE")) return "UPDATE";
        if (upper.startsWith("DELETE")) return "DELETE";
        return "UNKNOWN";
    }

    /**
     * 转换SQL执行结果为Map格式
     * <p>
     * 将SqlResult对象转换为Map格式，便于MCP工具返回
     * </p>
     *
     * @param result SQL执行结果对象
     * @return Map格式的结果，包含success、message、data、affectedRows、executionTime等字段
     */
    private Map<String, Object> convertResult(SqlResult result) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("success", result.isSuccess());
        map.put("message", result.getMessage());
        map.put("friendlyMessage", result.getFriendlyMessage());
        if (result.getData() != null) {
            map.put("data", result.getData());
            map.put("rowCount", result.getData().size());
        }
        if (result.getAffectedRows() != null) {
            map.put("affectedRows", result.getAffectedRows());
        }
        map.put("executionTime", result.getExecutionTime());
        return map;
    }
}
