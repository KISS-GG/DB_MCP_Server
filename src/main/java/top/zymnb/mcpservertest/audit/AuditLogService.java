package top.zymnb.mcpservertest.audit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import top.zymnb.mcpservertest.model.ConnectionInfo;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * SQL 审计日志服务
 * 记录所有 SQL 执行情况到文件，用于审计和追踪
 *
 * 日志格式：
 * [时间] DB=数据库类型://主机:端口/数据库名 USER=用户名 SQL="SQL语句" SUCCESS=是否成功 TIME=执行时间ms MSG="消息"
 */
@Slf4j
@Component
public class AuditLogService {

    /** 审计日志文件名 */
    @Value("${sqlLogFile}")
    private String logFilePath;
    
    /** 时间格式化器 */
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 记录 SQL 执行日志
     *
     * @param connInfo 数据库连接信息
     * @param sql SQL 语句
     * @param success 是否执行成功
     * @param executionTime 执行耗时（毫秒）
     * @param message 附加消息（错误信息等）
     */
    public void logSqlExecution(ConnectionInfo connInfo, String sql,
                                 boolean success, long executionTime, String message) {
        String logEntry = buildLogEntry(connInfo, sql, success, executionTime, message);
        writeToFile(logEntry);
        log.info("SQL审计: {}", logEntry);
    }

    /**
     * 构建日志条目
     *
     * @param connInfo 数据库连接信息
     * @param sql SQL 语句
     * @param success 是否执行成功
     * @param executionTime 执行耗时（毫秒）
     * @param message 附加消息
     * @return 格式化的日志字符串
     */
    private String buildLogEntry(ConnectionInfo connInfo, String sql,
                                  boolean success, long executionTime, String message) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(LocalDateTime.now().format(FORMATTER)).append("] ");
        sb.append("DB=").append(connInfo.getDbType()).append("://");
        sb.append(connInfo.getHost()).append(":").append(connInfo.getPort());
        sb.append("/").append(connInfo.getDatabase()).append(" ");
        sb.append("USER=").append(connInfo.getUsername()).append(" ");
        sb.append("SQL=\"").append(sql.replace("\n", " ")).append("\" ");
        sb.append("SUCCESS=").append(success).append(" ");
        sb.append("TIME=").append(executionTime).append("ms ");
        if (message != null) {
            sb.append("MSG=\"").append(message).append("\"");
        }
        return sb.toString();
    }

    /**
     * 将日志写入文件
     * 使用 synchronized 保证线程安全
     *
     * @param logEntry 日志条目
     */
    private synchronized void writeToFile(String logEntry) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFilePath, true))) {
            writer.println(logEntry);
        } catch (IOException e) {
            log.error("写入审计日志失败", e);
        }
    }
}
