package top.zymnb.mcpservertest.model;

import lombok.Builder;
import lombok.Data;

/**
 * 数据库连接信息
 * 用于封装数据库连接所需的所有参数
 */
@Data
@Builder
public class ConnectionInfo {
    /** 数据库类型（mysql、postgresql、oracle、sqlserver、kingbase） */
    private String dbType;
    /** 数据库主机地址 */
    private String host;
    /** 数据库端口 */
    private Integer port;
    /** 数据库用户名 */
    private String username;
    /** 数据库密码 */
    private String password;
    /** 数据库名称 */
    private String database;
    /** 是否使用 SSL 连接 */
    private Boolean useSsl;

    /**
     * 生成连接池的唯一标识键
     * 用于连接池缓存，相同连接信息会复用同一个连接池
     * @return 连接池唯一标识键，格式：dbType_host_port_database_username
     */
    public String getConnectionKey() {
        return String.format("%s_%s_%d_%s_%s", dbType, host, port, database, username);
    }
}
