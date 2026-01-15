package top.zymnb.mcpservertest.model;

import lombok.Builder;
import lombok.Data;

/**
 * 列元数据
 * 描述数据库表中单个列的详细信息
 */
@Data
@Builder
public class ColumnMetadata {
    /** 列名 */
    private String columnName;
    /** 数据类型（如 VARCHAR、INT、TIMESTAMP 等） */
    private String dataType;
    /** 列大小/长度 */
    private Integer columnSize;
    /** 是否允许为空 */
    private Boolean nullable;
    /** 默认值 */
    private String defaultValue;
    /** 列注释/说明 */
    private String comment;
    /** 是否为主键 */
    private Boolean isPrimaryKey;
}
