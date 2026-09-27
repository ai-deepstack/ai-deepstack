package org.deepstack.ai.tool.mcp.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * MCP Server 连接实体，对应表 {@code ai_mcp_connection}（后台动态配置）。
 */
@Data
@TableName("ai_mcp_connection")
public class AiMcpConnection implements Serializable {

    /** 主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 连接编码（全局唯一，用于 Client 注册与工具关联） */
    private String connectionCode;

    /** 连接显示名称 */
    private String connectionName;

    /** 传输类型：见 {@link org.deepstack.ai.kernel.enums.tool.McpTransportEnum} */
    private Integer transport;

    /** HTTP/SSE 端点 URL（SSE、STREAMABLE_HTTP 必填） */
    private String endpoint;

    /** STDIO 启动子进程命令（STDIO 必填） */
    private String command;

    /** STDIO 命令参数，JSON 数组字符串 */
    private String argsJson;

    /** 鉴权密钥（明文入库；对外 DTO 不回传，仅标记是否已配置） */
    private String secret;

    /** 额外 HTTP 请求头，JSON 对象字符串 */
    private String headersJson;

    /** 单次 MCP 请求超时毫秒数 */
    private Integer requestTimeoutMs;

    /** 是否启用：1=启用，0=停用 */
    private Integer enabled;

    /** 最近一次建连/同步失败原因摘要 */
    private String lastError;

    /** 逻辑删除标记 */
    @TableLogic
    private Integer isDel;

    /** 创建人 ID */
    private Long creatorId;

    /** 创建人名称 */
    private String creator;

    /** 修改人 ID */
    private Long modifierId;

    /** 修改人名称 */
    private String modifier;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
