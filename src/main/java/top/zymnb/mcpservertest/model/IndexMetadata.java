package top.zymnb.mcpservertest.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * 索引元数据
 * 描述数据库表中的索引信息
 */
@Data
@Builder
public class IndexMetadata {
    /** 索引名称 */
    private String indexName;
    /** 是否为唯一索引 */
    private Boolean unique;
    /** 索引包含的列名列表 */
    private List<String> columns;
}
