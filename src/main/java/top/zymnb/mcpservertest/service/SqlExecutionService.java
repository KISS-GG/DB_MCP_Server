package top.zymnb.mcpservertest.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.zymnb.mcpservertest.audit.AuditLogService;
import top.zymnb.mcpservertest.model.ConnectionInfo;
import top.zymnb.mcpservertest.model.SqlResult;
import top.zymnb.mcpservertest.pool.ConnectionPoolManager;
import top.zymnb.mcpservertest.security.SqlSecurityChecker;

import java.sql.*;
import java.util.*;

/**
 * SQL执行服务
 * <p>
 * 提供数据库SQL语句的执行能力，支持查询、更新和批量操作。
 * </p>
 * <p>
 * 主要功能：
 * <ul>
 *   <li>executeQuery: 执行查询SQL，返回结果集</li>
 *   <li>executeUpdate: 执行写操作SQL（INSERT/UPDATE/DELETE）</li>
 *   <li>executeBatch: 批量执行SQL，在同一事务中执行</li>
 * </ul>
 * </p>
 * <p>
 * 特性：
 * <ul>
 *   <li>支持参数化SQL，防止SQL注入</li>
 *   <li>支持超时控制，默认30秒</li>
 *   <li>查询结果默认限制1000行</li>
 *   <li>自动记录审计日志</li>
 *   <li>友好的错误信息翻译</li>
 *   <li>批量操作支持事务，失败自动回滚</li>
 * </ul>
 * </p>
 *
 * @author zym
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqlExecutionService {

    /** 连接池管理器，用于获取数据库连接 */
    private final ConnectionPoolManager poolManager;

    /** SQL安全检查器，用于检查SQL语句的安全性 */
    private final SqlSecurityChecker securityChecker;

    /** 审计日志服务，用于记录SQL执行日志 */
    private final AuditLogService auditLogService;

    /** 默认查询结果行数限制：1000行 */
    private static final int DEFAULT_LIMIT = 1000;

    /** 默认SQL执行超时时间：30秒 */
    private static final int DEFAULT_TIMEOUT = 30;

    /**
     * 执行查询SQL语句
     * <p>
     * 执行SELECT查询，返回结果集。支持参数化查询、结果行数限制和超时控制。
     * </p>
     *
     * @param connInfo 数据库连接信息
     * @param sql SQL查询语句
     * @param params SQL参数数组，可为null
     * @param limit 结果行数限制，null则使用默认值1000
     * @param timeout 超时时间（秒），null则使用默认值30秒
     * @return SQL执行结果，包含查询数据和执行时间
     */
    public SqlResult executeQuery(ConnectionInfo connInfo, String sql,
                                   Object[] params, Integer limit, Integer timeout) {
        long startTime = System.currentTimeMillis();
        // 使用默认值处理可选参数
        int actualLimit = limit != null ? limit : DEFAULT_LIMIT;
        int actualTimeout = timeout != null ? timeout : DEFAULT_TIMEOUT;

        try (Connection conn = poolManager.getConnection(connInfo);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            // 设置查询超时时间
            stmt.setQueryTimeout(actualTimeout);
            // 设置SQL参数
            setParameters(stmt, params);

            try (ResultSet rs = stmt.executeQuery()) {
                // 提取结果集数据
                List<Map<String, Object>> data = extractResultSet(rs, actualLimit);
                long executionTime = System.currentTimeMillis() - startTime;

                // 记录审计日志
                auditLogService.logSqlExecution(connInfo, sql, true, executionTime, null);

                return SqlResult.builder()
                        .success(true)
                        .message("查询成功")
                        .friendlyMessage("成功查询到 " + data.size() + " 条记录")
                        .data(data)
                        .executionTime(executionTime)
                        .build();
            }
        } catch (SQLException e) {
            long executionTime = System.currentTimeMillis() - startTime;
            // 记录失败日志
            auditLogService.logSqlExecution(connInfo, sql, false, executionTime, e.getMessage());
            return buildErrorResult(e, executionTime);
        }
    }

    /**
     * 执行写操作SQL语句
     * <p>
     * 执行INSERT、UPDATE、DELETE等写操作，返回影响的行数。支持参数化SQL和超时控制。
     * </p>
     *
     * @param connInfo 数据库连接信息
     * @param sql SQL写操作语句
     * @param params SQL参数数组，可为null
     * @param timeout 超时时间（秒），null则使用默认值30秒
     * @return SQL执行结果，包含影响行数和执行时间
     */
    public SqlResult executeUpdate(ConnectionInfo connInfo, String sql,
                                    Object[] params, Integer timeout) {
        long startTime = System.currentTimeMillis();
        // 使用默认值处理可选参数
        int actualTimeout = timeout != null ? timeout : DEFAULT_TIMEOUT;

        try (Connection conn = poolManager.getConnection(connInfo);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            // 设置执行超时时间
            stmt.setQueryTimeout(actualTimeout);
            // 设置SQL参数
            setParameters(stmt, params);

            // 执行更新操作
            int affectedRows = stmt.executeUpdate();
            long executionTime = System.currentTimeMillis() - startTime;

            // 记录审计日志
            auditLogService.logSqlExecution(connInfo, sql, true, executionTime, null);

            return SqlResult.builder()
                    .success(true)
                    .message("执行成功")
                    .friendlyMessage("成功影响 " + affectedRows + " 条记录")
                    .affectedRows(affectedRows)
                    .executionTime(executionTime)
                    .build();
        } catch (SQLException e) {
            long executionTime = System.currentTimeMillis() - startTime;
            // 记录失败日志
            auditLogService.logSqlExecution(connInfo, sql, false, executionTime, e.getMessage());
            return buildErrorResult(e, executionTime);
        }
    }

    /**
     * 批量执行SQL语句
     * <p>
     * 在同一事务中执行多条SQL语句，任何一条失败则全部回滚。
     * 适用于需要保证原子性的批量操作。
     * </p>
     *
     * @param connInfo 数据库连接信息
     * @param sqlList SQL语句列表
     * @param timeout 超时时间（秒），null则使用默认值30秒
     * @return SQL执行结果，包含总影响行数和执行时间
     */
    public SqlResult executeBatch(ConnectionInfo connInfo, List<String> sqlList,
                                   Integer timeout) {
        long startTime = System.currentTimeMillis();
        // 使用默认值处理可选参数
        int actualTimeout = timeout != null ? timeout : DEFAULT_TIMEOUT;
        int totalAffected = 0;

        try (Connection conn = poolManager.getConnection(connInfo)) {
            // 关闭自动提交，开启事务
            conn.setAutoCommit(false);

            try {
                // 逐条执行SQL
                for (String sql : sqlList) {
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setQueryTimeout(actualTimeout);
                        totalAffected += stmt.executeUpdate();
                    }
                }
                // 全部成功，提交事务
                conn.commit();

                long executionTime = System.currentTimeMillis() - startTime;
                String allSql = String.join("; ", sqlList);
                // 记录审计日志
                auditLogService.logSqlExecution(connInfo, allSql, true, executionTime, null);

                return SqlResult.builder()
                        .success(true)
                        .message("批量执行成功")
                        .friendlyMessage("成功执行 " + sqlList.size() + " 条SQL，共影响 " + totalAffected + " 条记录")
                        .affectedRows(totalAffected)
                        .executionTime(executionTime)
                        .build();
            } catch (SQLException e) {
                // 发生异常，回滚事务
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            long executionTime = System.currentTimeMillis() - startTime;
            String allSql = String.join("; ", sqlList);
            // 记录失败日志
            auditLogService.logSqlExecution(connInfo, allSql, false, executionTime, e.getMessage());
            return buildErrorResult(e, executionTime);
        }
    }

    /**
     * 设置PreparedStatement的参数
     * <p>
     * 将参数数组中的值按顺序设置到PreparedStatement中，参数索引从1开始
     * </p>
     *
     * @param stmt PreparedStatement对象
     * @param params 参数数组，可为null
     * @throws SQLException 设置参数失败时抛出
     */
    private void setParameters(PreparedStatement stmt, Object[] params) throws SQLException {
        if (params != null) {
            for (int i = 0; i < params.length; i++) {
                // JDBC参数索引从1开始
                stmt.setObject(i + 1, params[i]);
            }
        }
    }

    /**
     * 从ResultSet中提取数据
     * <p>
     * 将ResultSet转换为List<Map<String, Object>>格式，每行数据转换为一个Map，
     * 列名作为key，列值作为value
     * </p>
     *
     * @param rs ResultSet对象
     * @param limit 最大行数限制
     * @return 结果数据列表
     * @throws SQLException 读取数据失败时抛出
     */
    private List<Map<String, Object>> extractResultSet(ResultSet rs, int limit)
            throws SQLException {
        List<Map<String, Object>> result = new ArrayList<>();
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        int count = 0;
        while (rs.next() && count < limit) {
            Map<String, Object> row = new LinkedHashMap<>();
            // 遍历所有列，列索引从1开始
            for (int i = 1; i <= columnCount; i++) {
                row.put(metaData.getColumnLabel(i), rs.getObject(i));
            }
            result.add(row);
            count++;
        }
        return result;
    }

    /**
     * 构建错误结果
     * <p>
     * 将SQLException转换为SqlResult对象，包含原始错误信息和友好的错误描述
     * </p>
     *
     * @param e SQL异常
     * @param executionTime 执行耗时（毫秒）
     * @return SQL执行结果（失败）
     */
    private SqlResult buildErrorResult(SQLException e, long executionTime) {
        return SqlResult.builder()
                .success(false)
                .message(e.getMessage())
                .friendlyMessage(translateError(e))
                .executionTime(executionTime)
                .build();
    }

    /**
     * 翻译SQL错误信息
     * <p>
     * 将数据库的原始错误信息翻译为用户友好的中文描述
     * </p>
     *
     * @param e SQL异常
     * @return 友好的错误描述
     */
    private String translateError(SQLException e) {
        String msg = e.getMessage();
        int errorCode = e.getErrorCode();

        // 根据错误信息关键字进行翻译
        if (msg.contains("Access denied")) {
            return "数据库访问被拒绝，请检查用户名和密码";
        } else if (msg.contains("Unknown database")) {
            return "数据库不存在，请检查数据库名称";
        } else if (msg.contains("Table") && msg.contains("doesn't exist")) {
            return "表不存在，请检查表名";
        } else if (msg.contains("Duplicate entry")) {
            return "数据重复，违反唯一约束";
        } else if (msg.contains("Connection refused")) {
            return "无法连接到数据库服务器，请检查地址和端口";
        } else if (msg.contains("timeout")) {
            return "SQL执行超时，请优化查询或增加超时时间";
        }
        return "数据库错误: " + msg;
    }
}
