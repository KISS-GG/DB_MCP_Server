package top.zymnb.mcpservertest.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * SQL 执行结果
 * 封装 SQL 执行的所有返回信息，包括成功状态、数据、错误信息等
 */
@Data
@Builder
public class SqlResult {
    /** 执行是否成功 */
    private boolean success;
    /** 原始错误信息（技术性描述） */
    private String message;
    /** 友好的错误信息（用户可读） */
    private String friendlyMessage;
    /** 查询结果数据，每行数据为一个 Map */
    private List<Map<String, Object>> data;
    /** 受影响的行数（用于 INSERT/UPDATE/DELETE） */
    private Integer affectedRows;
    /** SQL 执行耗时（毫秒） */
    private Long executionTime;
}
