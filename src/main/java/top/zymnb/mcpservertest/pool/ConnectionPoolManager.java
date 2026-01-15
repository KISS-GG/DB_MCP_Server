package top.zymnb.mcpservertest.pool;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.zymnb.mcpservertest.model.ConnectionInfo;
import top.zymnb.mcpservertest.model.DbType;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 数据库连接池管理器
 * 使用 HikariCP 管理多个数据库连接池，支持连接池缓存和自动清理
 *
 * 主要特性：
 * - 连接池缓存：相同连接信息复用同一个连接池
 * - 自动清理：空闲超过1小时的连接池会被自动关闭
 * - 线程安全：使用 ConcurrentHashMap 保证并发安全
 */
@Slf4j
@Component
public class ConnectionPoolManager {

    /** 连接池缓存，key 为连接标识，value 为连接池实体 */
    private final Map<String, PoolEntry> pools = new ConcurrentHashMap<>();
    /** 定时任务调度器，用于定期清理空闲连接池 */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    /** 每个连接池的最大连接数 */
    private static final int MAX_POOL_SIZE = 100;
    /** 连接池空闲超时时间（毫秒），超过此时间未使用的连接池将被关闭 */
    private static final long IDLE_TIMEOUT_MS = 60 * 60 * 1000L; // 1小时

    /**
     * 构造函数
     * 启动定时任务，每10分钟检查并清理空闲的连接池
     */
    public ConnectionPoolManager() {
        // 每10分钟检查一次过期的连接池
        scheduler.scheduleAtFixedRate(this::cleanupIdlePools, 10, 10, TimeUnit.MINUTES);
    }

    /**
     * 获取数据库连接
     * 如果连接池不存在则自动创建，存在则复用
     *
     * @param info 数据库连接信息
     * @return 数据库连接对象
     * @throws SQLException 如果获取连接失败
     */
    public Connection getConnection(ConnectionInfo info) throws SQLException {
        String key = info.getConnectionKey();
        PoolEntry entry = pools.computeIfAbsent(key, k -> createPoolEntry(info));
        entry.updateLastAccessTime();
        return entry.getDataSource().getConnection();
    }

    /**
     * 创建新的连接池实体
     * 使用 HikariCP 配置并创建数据库连接池
     *
     * @param info 数据库连接信息
     * @return 连接池实体
     */
    private PoolEntry createPoolEntry(ConnectionInfo info) {
        DbType dbType = DbType.fromCode(info.getDbType());
        HikariConfig config = new HikariConfig();

        config.setDriverClassName(dbType.getDriverClass());
        config.setJdbcUrl(buildJdbcUrl(dbType, info));
        config.setUsername(info.getUsername());
        config.setPassword(info.getPassword());
        config.setMaximumPoolSize(MAX_POOL_SIZE);
        config.setMinimumIdle(1);
        config.setIdleTimeout(IDLE_TIMEOUT_MS);
        config.setConnectionTimeout(30000);
        config.setPoolName("pool-" + info.getConnectionKey());

        if (Boolean.TRUE.equals(info.getUseSsl())) {
            configureSsl(config, dbType);
        }

        log.info("创建连接池: {}", info.getConnectionKey());
        return new PoolEntry(new HikariDataSource(config));
    }

    /**
     * 构建 JDBC 连接 URL
     *
     * @param dbType 数据库类型
     * @param info 连接信息
     * @return 完整的 JDBC URL
     */
    private String buildJdbcUrl(DbType dbType, ConnectionInfo info) {
        return dbType.buildUrl(info.getHost(), info.getPort(), info.getDatabase());
    }

    /**
     * 配置 SSL 连接
     * 根据不同的数据库类型设置相应的 SSL 参数
     *
     * @param config HikariCP 配置对象
     * @param dbType 数据库类型
     */
    private void configureSsl(HikariConfig config, DbType dbType) {
        switch (dbType) {
            case MYSQL -> config.addDataSourceProperty("useSSL", "true");
            case POSTGRESQL -> config.addDataSourceProperty("ssl", "true");
            case SQLSERVER -> config.addDataSourceProperty("encrypt", "true");
            default -> log.warn("SSL配置不支持数据库类型: {}", dbType);
        }
    }

    /**
     * 清理空闲的连接池
     * 定时任务调用，检查并关闭超过空闲时间的连接池
     */
    private void cleanupIdlePools() {
        long now = System.currentTimeMillis();
        pools.entrySet().removeIf(entry -> {
            if (now - entry.getValue().getLastAccessTime() > IDLE_TIMEOUT_MS) {
                log.info("关闭空闲连接池: {}", entry.getKey());
                entry.getValue().close();
                return true;
            }
            return false;
        });
    }

    /**
     * 关闭所有连接池
     * 应用关闭时调用，释放所有资源
     */
    public void closeAll() {
        pools.values().forEach(PoolEntry::close);
        pools.clear();
        scheduler.shutdown();
    }

    /**
     * 连接池实体
     * 封装 HikariDataSource 和最后访问时间，用于连接池管理
     */
    private static class PoolEntry {
        /** HikariCP 数据源 */
        private final HikariDataSource dataSource;
        /** 最后访问时间戳（毫秒），用于判断是否空闲超时 */
        private volatile long lastAccessTime;

        /**
         * 构造函数
         * @param dataSource HikariCP 数据源
         */
        PoolEntry(HikariDataSource dataSource) {
            this.dataSource = dataSource;
            this.lastAccessTime = System.currentTimeMillis();
        }

        /**
         * 获取数据源
         * @return 数据源对象
         */
        DataSource getDataSource() {
            return dataSource;
        }

        /**
         * 获取最后访问时间
         * @return 最后访问时间戳（毫秒）
         */
        long getLastAccessTime() {
            return lastAccessTime;
        }

        /**
         * 更新最后访问时间为当前时间
         */
        void updateLastAccessTime() {
            this.lastAccessTime = System.currentTimeMillis();
        }

        /**
         * 关闭数据源
         * 释放连接池资源
         */
        void close() {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
        }
    }
}
