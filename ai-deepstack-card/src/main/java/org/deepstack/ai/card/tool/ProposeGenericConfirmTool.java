package org.deepstack.ai.card.tool;

import com.alibaba.fastjson2.JSON;
import org.deepstack.ai.card.model.dto.response.ChatCard;
import org.deepstack.ai.card.processor.GenericConfirmCardProcessor;
import org.deepstack.ai.card.service.ChatCardService;
import org.deepstack.ai.kernel.tool.ToolContextKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 向用户发出通用确认卡。副作用须等用户确认、图 resume 后再执行。
 */
@Slf4j
@Component("proposeGenericConfirmTool")
@RequiredArgsConstructor
public class ProposeGenericConfirmTool {

    private final ChatCardService chatCardService;

    /**
     * 向用户发出通用确认卡（副作用须等 confirm 后再执行）。
     *
     * @param title         确认标题
     * @param summary       摘要文案（可选）
     * @param fieldsJson    字段 JSON 数组
     * @param confirmLabel  确认按钮文案
     * @param cancelLabel   取消按钮文案
     * @param toolContext   工具上下文（含 userId、conversationId、cardEmitter）
     * @return 给模型的简短提示
     */
    @Tool(name = "propose_generic_confirm",
            description = "需要用户确认后再执行的操作时调用（退款、提交、删除等）。"
                    + "只展示确认卡，不要在本轮同时调用会改数据或动钱的工具。"
                    + "fieldsJson 为 JSON 数组：[{key,label,value,editable}]。")
    /** 提出一张通用确认卡片。 */
    public String proposeGenericConfirm(
            @ToolParam(description = "确认标题，例如「确认退款」") String title,
            @ToolParam(description = "给用户看的一两句摘要", required = false) String summary,
            @ToolParam(description = "字段 JSON 数组，每项含 key/label/value，可选 editable", required = false)
            String fieldsJson,
            @ToolParam(description = "确认按钮文案，默认「确认」", required = false) String confirmLabel,
            @ToolParam(description = "取消按钮文案，默认「取消」", required = false) String cancelLabel,
            ToolContext toolContext) {

        Map<String, Object> ctx = toolContext != null ? toolContext.getContext() : Map.of();
        String userId = str(ctx.get(ToolContextKeys.USER_ID));
        String conversationId = str(ctx.get(ToolContextKeys.CONVERSATION_ID));
        log.info("propose_generic_confirm 入口: conversationId={}, title={}", conversationId, title);

        String resolvedTitle = StringUtils.hasText(title) ? title.trim() : "请确认";
        String resolvedConfirm = StringUtils.hasText(confirmLabel) ? confirmLabel.trim() : "确认";
        String resolvedCancel = StringUtils.hasText(cancelLabel) ? cancelLabel.trim() : "取消";
        List<Map<String, Object>> fields = parseFields(fieldsJson);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", resolvedTitle);
        if (StringUtils.hasText(summary)) {
            payload.put("summary", summary.trim());
        }
        payload.put("fields", fields);
        payload.put("confirmLabel", resolvedConfirm);
        payload.put("cancelLabel", resolvedCancel);

        ChatCard card = new ChatCard();
        card.setCardType(GenericConfirmCardProcessor.CARD_TYPE);
        card.setTitle(resolvedTitle);
        card.setUserId(userId);
        card.setConversationId(conversationId);
        card.setPayload(payload);
        card.setActions(List.of(
                action("confirm", resolvedConfirm),
                action("reject", resolvedCancel)
        ));

        ChatCard saved = chatCardService.saveCard(card);
        emit(ctx.get(ToolContextKeys.CARD_EMITTER), saved);
        log.info("propose_generic_confirm: cardId={}, title={}, fields={}",
                saved.getCardId(), resolvedTitle, fields.size());
        return "已向用户发出确认卡「" + resolvedTitle + "」，请等待用户确认后再执行后续操作。";
    }

    /** 解析 fieldsJson 为字段列表；失败返回空列表。 */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parseFields(String fieldsJson) {
        if (!StringUtils.hasText(fieldsJson)) {
            return List.of();
        }
        try {
            Object parsed = JSON.parse(fieldsJson.trim());
            List<Map<String, Object>> coerced = GenericConfirmCardProcessor.coerceFields(parsed);
            return coerced != null ? coerced : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 构造卡片动作项（type + label）。 */
    private static Map<String, Object> action(String type, String label) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("type", type);
        a.put("label", label);
        return a;
    }

    /** 通过 toolContext 中的 cardEmitter 推送卡片 SSE。 */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void emit(Object emitter, ChatCard card) {
        if (emitter instanceof Consumer c) {
            c.accept(card);
        }
    }

    /** 对象转字符串；null 转空串。 */
    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}
