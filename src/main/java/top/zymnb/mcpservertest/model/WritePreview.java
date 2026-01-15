package top.zymnb.mcpservertest.model;

import lombok.Builder;
import lombok.Data;

/**
 * 写操作预览信息
 * 用于两阶段写操作确认机制，先预览后执行
 */
@Data
@Builder
public class WritePreview {
    /** 确认ID，用于后续确认执行时的唯一标识 */
    private String confirmId;
    /** 待执行的 SQL 语句 */
    private String sql;
    /** 预估影响的行数 */
    private Integer estimatedAffectedRows;
    /** 操作类型（INSERT、UPDATE、DELETE） */
    private String operationType;
    /** 过期时间戳（毫秒），超过此时间预览失效 */
    private Long expireTime;
    /** 数据库连接信息 */
    private ConnectionInfo connectionInfo;
    /** SQL 参数数组 */
    private Object[] params;
}
