package top.zymnb.mcpservertest.model;

import lombok.Builder;
import lombok.Data;

/**
 * 约束元数据
 * 描述数据库表中的约束信息（主键、外键、唯一约束等）
 */
@Data
@Builder
public class ConstraintMetadata {
    /** 约束名称 */
    private String constraintName;
    /** 约束类型（PRIMARY KEY、FOREIGN KEY、UNIQUE 等） */
    private String constraintType;
    /** 约束所在的列名 */
    private String columnName;
    /** 外键引用的表名（仅用于外键约束） */
    private String referencedTable;
    /** 外键引用的列名（仅用于外键约束） */
    private String referencedColumn;
}
