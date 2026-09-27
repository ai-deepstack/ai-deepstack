package org.deepstack.ai.card.model.dto.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 卡片动作响应
 * <p>
 * 卡片动作处理结果（非流式场景）。
 * 流式场景（resume 后继续对话）直接返回 SSE 流。
 * </p>
 */
@Data
public class CardActionResponse implements Serializable {

    private String cardId;

    /** 状态码（ChatCardStatusEnum） */
    private Integer status;

    /** 状态中文名 */
    private String statusName;

    /** 业务落地结果（如创建的计划 ID） */
    private Object bizResult;

    /** HITL 线程 ID（resume 后回传） */
    private String threadId;

    /** 图恢复状态码（GraphRunStatusEnum）；未 resume 则为空 */
    private Integer graphStatus;

    /** 图恢复状态中文名 */
    private String graphStatusName;

    /** 图恢复后的回复文本 */
    private String graphResult;

    public CardActionResponse() {
    }

    public CardActionResponse(String cardId, Integer status, String statusName, Object bizResult) {
        this.cardId = cardId;
        this.status = status;
        this.statusName = statusName;
        this.bizResult = bizResult;
    }
}
