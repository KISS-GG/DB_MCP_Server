package top.zymnb.mcpservertest.model;

import lombok.Getter;

/**
 * 数据库类型枚举
 * 支持的数据库类型：MySQL、PostgreSQL、Oracle、SQL Server、KingBase
 */
@Getter
public enum DbType {
    /** MySQL 数据库 */
    MYSQL("mysql", "com.mysql.cj.jdbc.Driver", "jdbc:mysql://%s:%d/%s"),
    /** PostgreSQL 数据库 */
    POSTGRESQL("postgresql", "org.postgresql.Driver", "jdbc:postgresql://%s:%d/%s"),
    /** Oracle 数据库 */
    ORACLE("oracle", "oracle.jdbc.OracleDriver", "jdbc:oracle:thin:@%s:%d:%s"),
    /** SQL Server 数据库 */
    SQLSERVER("sqlserver", "com.microsoft.sqlserver.jdbc.SQLServerDriver", "jdbc:sqlserver://%s:%d;databaseName=%s"),
    /** KingBase 数据库（人大金仓） */
    KINGBASE("kingbase", "com.kingbase8.Driver", "jdbc:kingbase8://%s:%d/%s");

    /** 数据库类型代码 */
    private final String code;
    /** JDBC 驱动类名 */
    private final String driverClass;
    /** JDBC URL 模板，使用 %s 和 %d 作为占位符 */
    private final String urlTemplate;

    /**
     * 构造函数
     * @param code 数据库类型代码
     * @param driverClass JDBC 驱动类名
     * @param urlTemplate JDBC URL 模板
     */
    DbType(String code, String driverClass, String urlTemplate) {
        this.code = code;
        this.driverClass = driverClass;
        this.urlTemplate = urlTemplate;
    }

    /**
     * 根据代码获取数据库类型
     * @param code 数据库类型代码（不区分大小写）
     * @return 对应的数据库类型枚举
     * @throws IllegalArgumentException 如果代码不支持
     */
    public static DbType fromCode(String code) {
        for (DbType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("不支持的数据库类型: " + code);
    }

    /**
     * 构建 JDBC 连接 URL
     * @param host 数据库主机地址
     * @param port 数据库端口
     * @param database 数据库名称
     * @return 完整的 JDBC 连接 URL
     */
    public String buildUrl(String host, int port, String database) {
        return String.format(urlTemplate, host, port, database);
    }
}
