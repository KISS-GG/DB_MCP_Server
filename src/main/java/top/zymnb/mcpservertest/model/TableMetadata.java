package top.zymnb.mcpservertest.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * 表元数据
 * 包含表的完整结构信息：列、索引、约束等
 */
@Data
@Builder
public class TableMetadata {
    /** 表名 */
    private String tableName;
    /** 表注释/说明 */
    private String tableComment;
    /** 列信息列表 */
    private List<ColumnMetadata> columns;
    /** 索引信息列表 */
    private List<IndexMetadata> indexes;
    /** 约束信息列表（主键、外键等） */
    private List<ConstraintMetadata> constraints;
}
