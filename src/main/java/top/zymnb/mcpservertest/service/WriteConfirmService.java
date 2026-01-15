package top.zymnb.mcpservertest.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.zymnb.mcpservertest.model.ConnectionInfo;
import top.zymnb.mcpservertest.model.SqlResult;
import top.zymnb.mcpservertest.model.WritePreview;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 写操作预览确认服务
 * <p>
 * 实现写操作的两阶段确认机制，提高数据安全性：
 * <ul>
 *   <li>第一阶段：创建预览，生成确认ID，保存操作信息</li>
 *   <li>第二阶段：使用确认ID执行实际的写操作</li>
 * </ul>
 * </p>
 * <p>
 * 特性：
 * <ul>
 *   <li>预览信息30分钟有效期</li>
 *   <li>每5分钟自动清理过期预览</li>
 *   <li>线程安全的并发访问</li>
 * </ul>
 * </p>
 *
 * @author zym
 * @since 1.0.0
 */
@Slf4j
@Service
public class WriteConfirmService {

    /** SQL执行服务，用于执行实际的写操作 */
    private final SqlExecutionService sqlExecutionService;

    /** 待确认的写操作预览信息缓存，key为确认ID */
    private final Map<String, WritePreview> pendingWrites = new ConcurrentHashMap<>();

    /** 定时任务调度器，用于清理过期预览 */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    /** 预览信息过期时间：30分钟 */
    private static final long EXPIRE_TIME_MS = 30 * 60 * 1000L;

    /**
     * 构造函数
     * <p>
     * 初始化服务并启动定时清理任务，每5分钟清理一次过期的预览信息
     * </p>
     *
     * @param sqlExecutionService SQL执行服务
     */
    public WriteConfirmService(SqlExecutionService sqlExecutionService) {
        this.sqlExecutionService = sqlExecutionService;
        // 启动定时任务：每5分钟清理一次过期预览
        scheduler.scheduleAtFixedRate(this::cleanupExpired, 5, 5, TimeUnit.MINUTES);
    }

    /**
     * 创建写操作预览
     * <p>
     * 生成唯一的确认ID，保存SQL语句、参数和连接信息，设置30分钟有效期
     * </p>
     *
     * @param connInfo 数据库连接信息
     * @param sql SQL语句
     * @param params SQL参数数组
     * @param operationType 操作类型（INSERT/UPDATE/DELETE）
     * @return 写操作预览信息，包含确认ID
     */
    public WritePreview createPreview(ConnectionInfo connInfo, String sql,
                                       Object[] params, String operationType) {
        // 生成唯一的确认ID
        String confirmId = UUID.randomUUID().toString();
        // 计算过期时间（当前时间 + 30分钟）
        long expireTime = System.currentTimeMillis() + EXPIRE_TIME_MS;

        // 构建预览信息对象
        WritePreview preview = WritePreview.builder()
                .confirmId(confirmId)
                .sql(sql)
                .params(params)
                .operationType(operationType)
                .expireTime(expireTime)
                .connectionInfo(connInfo)
                .build();

        // 保存到待确认缓存中
        pendingWrites.put(confirmId, preview);
        log.info("创建写操作预览: {}", confirmId);

        return preview;
    }

    /**
     * 确认并执行写操作
     * <p>
     * 根据确认ID查找预览信息，验证有效性后执行实际的SQL写操作
     * </p>
     *
     * @param confirmId 确认ID（由createPreview方法生成）
     * @param timeout 超时时间（秒），可选，默认30秒
     * @return SQL执行结果
     */
    public SqlResult confirmAndExecute(String confirmId, Integer timeout) {
        // 从缓存中移除预览信息（确保只能执行一次）
        WritePreview preview = pendingWrites.remove(confirmId);

        // 检查确认ID是否存在
        if (preview == null) {
            return SqlResult.builder()
                    .success(false)
                    .message("确认ID不存在或已过期")
                    .friendlyMessage("找不到对应的预览信息，可能已过期，请重新执行预览")
                    .build();
        }

        // 检查预览是否已过期
        if (System.currentTimeMillis() > preview.getExpireTime()) {
            return SqlResult.builder()
                    .success(false)
                    .message("预览已过期")
                    .friendlyMessage("预览信息已超过30分钟有效期，请重新执行预览")
                    .build();
        }

        // 执行实际的写操作
        return sqlExecutionService.executeUpdate(
                preview.getConnectionInfo(),
                preview.getSql(),
                preview.getParams(),
                timeout
        );
    }

    /**
     * 清理过期的预览信息
     * <p>
     * 定时任务方法，每5分钟执行一次，移除所有已过期的预览信息
     * </p>
     */
    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        // 遍历所有预览信息，移除已过期的条目
        pendingWrites.entrySet().removeIf(entry -> {
            if (now > entry.getValue().getExpireTime()) {
                log.info("清理过期预览: {}", entry.getKey());
                return true;
            }
            return false;
        });
    }
}
