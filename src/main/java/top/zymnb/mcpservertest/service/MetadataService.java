package top.zymnb.mcpservertest.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.zymnb.mcpservertest.model.*;
import top.zymnb.mcpservertest.pool.ConnectionPoolManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 元数据查询服务
 * 提供数据库表结构信息的查询功能，包括表列表、列信息、索引、约束等
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataService {

    /** 连接池管理器 */
    private final ConnectionPoolManager poolManager;

    /**
     * 获取数据库中的所有表名
     *
     * @param connInfo 数据库连接信息
     * @return 表名列表
     * @throws SQLException 如果查询失败
     */
    public List<String> getTables(ConnectionInfo connInfo) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (Connection conn = poolManager.getConnection(connInfo)) {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs = metaData.getTables(
                    connInfo.getDatabase(), null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME"));
                }
            }
        }
        return tables;
    }

    /**
     * 获取指定表的完整元数据信息
     * 包括列、索引、约束、表注释等
     *
     * @param connInfo 数据库连接信息
     * @param tableName 表名
     * @return 表元数据对象
     * @throws SQLException 如果查询失败
     */
    public TableMetadata getTableMetadata(ConnectionInfo connInfo, String tableName)
            throws SQLException {
        try (Connection conn = poolManager.getConnection(connInfo)) {
            DatabaseMetaData metaData = conn.getMetaData();

            List<ColumnMetadata> columns = getColumns(metaData, connInfo, tableName);
            List<IndexMetadata> indexes = getIndexes(metaData, connInfo, tableName);
            List<ConstraintMetadata> constraints = getConstraints(metaData, connInfo, tableName);
            String comment = getTableComment(conn, connInfo, tableName);

            return TableMetadata.builder()
                    .tableName(tableName)
                    .tableComment(comment)
                    .columns(columns)
                    .indexes(indexes)
                    .constraints(constraints)
                    .build();
        }
    }

    /**
     * 获取表的列信息
     *
     * @param metaData 数据库元数据对象
     * @param connInfo 连接信息
     * @param tableName 表名
     * @return 列元数据列表
     * @throws SQLException 如果查询失败
     */
    private List<ColumnMetadata> getColumns(DatabaseMetaData metaData,
                                             ConnectionInfo connInfo, String tableName)
            throws SQLException {
        List<ColumnMetadata> columns = new ArrayList<>();
        List<String> primaryKeys = getPrimaryKeyColumns(metaData, connInfo, tableName);

        try (ResultSet rs = metaData.getColumns(
                connInfo.getDatabase(), null, tableName, "%")) {
            while (rs.next()) {
                columns.add(ColumnMetadata.builder()
                        .columnName(rs.getString("COLUMN_NAME"))
                        .dataType(rs.getString("TYPE_NAME"))
                        .columnSize(rs.getInt("COLUMN_SIZE"))
                        .nullable("YES".equals(rs.getString("IS_NULLABLE")))
                        .defaultValue(rs.getString("COLUMN_DEF"))
                        .comment(rs.getString("REMARKS"))
                        .isPrimaryKey(primaryKeys.contains(rs.getString("COLUMN_NAME")))
                        .build());
            }
        }
        return columns;
    }

    /**
     * 获取表的主键列名列表
     *
     * @param metaData 数据库元数据对象
     * @param connInfo 连接信息
     * @param tableName 表名
     * @return 主键列名列表
     * @throws SQLException 如果查询失败
     */
    private List<String> getPrimaryKeyColumns(DatabaseMetaData metaData,
                                               ConnectionInfo connInfo, String tableName)
            throws SQLException {
        List<String> pkColumns = new ArrayList<>();
        try (ResultSet rs = metaData.getPrimaryKeys(
                connInfo.getDatabase(), null, tableName)) {
            while (rs.next()) {
                pkColumns.add(rs.getString("COLUMN_NAME"));
            }
        }
        return pkColumns;
    }

    /**
     * 获取表的索引信息
     *
     * @param metaData 数据库元数据对象
     * @param connInfo 连接信息
     * @param tableName 表名
     * @return 索引元数据列表
     * @throws SQLException 如果查询失败
     */
    private List<IndexMetadata> getIndexes(DatabaseMetaData metaData,
                                            ConnectionInfo connInfo, String tableName)
            throws SQLException {
        List<IndexMetadata> indexes = new ArrayList<>();
        try (ResultSet rs = metaData.getIndexInfo(
                connInfo.getDatabase(), null, tableName, false, false)) {
            String currentIndex = null;
            List<String> currentColumns = new ArrayList<>();
            boolean currentUnique = false;

            while (rs.next()) {
                String indexName = rs.getString("INDEX_NAME");
                if (indexName == null) continue;

                if (!indexName.equals(currentIndex)) {
                    if (currentIndex != null) {
                        indexes.add(IndexMetadata.builder()
                                .indexName(currentIndex)
                                .unique(currentUnique)
                                .columns(new ArrayList<>(currentColumns))
                                .build());
                    }
                    currentIndex = indexName;
                    currentUnique = !rs.getBoolean("NON_UNIQUE");
                    currentColumns.clear();
                }
                currentColumns.add(rs.getString("COLUMN_NAME"));
            }

            if (currentIndex != null) {
                indexes.add(IndexMetadata.builder()
                        .indexName(currentIndex)
                        .unique(currentUnique)
                        .columns(currentColumns)
                        .build());
            }
        }
        return indexes;
    }

    /**
     * 获取表的约束信息
     * 包括主键约束和外键约束
     *
     * @param metaData 数据库元数据对象
     * @param connInfo 连接信息
     * @param tableName 表名
     * @return 约束元数据列表
     * @throws SQLException 如果查询失败
     */
    private List<ConstraintMetadata> getConstraints(DatabaseMetaData metaData,
                                                     ConnectionInfo connInfo, String tableName)
            throws SQLException {
        List<ConstraintMetadata> constraints = new ArrayList<>();

        // 主键约束
        try (ResultSet rs = metaData.getPrimaryKeys(
                connInfo.getDatabase(), null, tableName)) {
            while (rs.next()) {
                constraints.add(ConstraintMetadata.builder()
                        .constraintName(rs.getString("PK_NAME"))
                        .constraintType("PRIMARY KEY")
                        .columnName(rs.getString("COLUMN_NAME"))
                        .build());
            }
        }

        // 外键约束
        try (ResultSet rs = metaData.getImportedKeys(
                connInfo.getDatabase(), null, tableName)) {
            while (rs.next()) {
                constraints.add(ConstraintMetadata.builder()
                        .constraintName(rs.getString("FK_NAME"))
                        .constraintType("FOREIGN KEY")
                        .columnName(rs.getString("FKCOLUMN_NAME"))
                        .referencedTable(rs.getString("PKTABLE_NAME"))
                        .referencedColumn(rs.getString("PKCOLUMN_NAME"))
                        .build());
            }
        }

        return constraints;
    }

    /**
     * 获取表注释
     * 根据不同的数据库类型使用不同的查询方式
     *
     * @param conn 数据库连接
     * @param connInfo 连接信息
     * @param tableName 表名
     * @return 表注释，如果获取失败返回 null
     * @throws SQLException 如果查询失败
     */
    private String getTableComment(Connection conn, ConnectionInfo connInfo,
                                    String tableName) throws SQLException {
        DbType dbType = DbType.fromCode(connInfo.getDbType());
        String sql = getTableCommentSql(dbType, connInfo.getDatabase(), tableName);
        if (sql == null) return null;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getString(1);
            }
        } catch (SQLException e) {
            log.warn("获取表注释失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 根据数据库类型生成查询表注释的 SQL 语句
     *
     * @param dbType 数据库类型
     * @param database 数据库名
     * @param tableName 表名
     * @return 查询表注释的 SQL 语句
     */
    private String getTableCommentSql(DbType dbType, String database, String tableName) {
        return switch (dbType) {
            case MYSQL -> String.format(
                    "SELECT TABLE_COMMENT FROM information_schema.TABLES " +
                    "WHERE TABLE_SCHEMA='%s' AND TABLE_NAME='%s'", database, tableName);
            case POSTGRESQL, KINGBASE -> String.format(
                    "SELECT obj_description('%s'::regclass, 'pg_class')", tableName);
            case ORACLE -> String.format(
                    "SELECT COMMENTS FROM user_tab_comments WHERE TABLE_NAME='%s'",
                    tableName.toUpperCase());
            case SQLSERVER -> String.format(
                    "SELECT ep.value FROM sys.tables t " +
                    "LEFT JOIN sys.extended_properties ep ON t.object_id = ep.major_id " +
                    "WHERE t.name='%s' AND ep.minor_id=0", tableName);
        };
    }
}
