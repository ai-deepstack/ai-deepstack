package org.deepstack.ai.agent.web;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deepstack.ai.agent.model.entity.AgentAlertEvent;
import org.deepstack.ai.agent.observability.AgentAlertService;
import org.deepstack.ai.kernel.common.PageInfoUtils;
import org.deepstack.ai.kernel.enums.common.CommonErrorCode;
import org.deepstack.ai.kernel.model.PageInfo;
import org.deepstack.ai.kernel.model.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 智能体告警 API：列表 / 确认。
 */
@Slf4j
@RestController
@RequestMapping("/api/agent-alerts")
@RequiredArgsConstructor
public class AgentAlertController {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final AgentAlertService agentAlertService;

    /**
     * 告警事件分页。
     *
     * @param pageNum  页码
     * @param pageSize 页大小
     * @return 分页结果
     */
    @GetMapping
    public Response<PageInfo<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int pageSize) {
        log.info("API agent-alerts page: pageNum={}, pageSize={}", pageNum, pageSize);
        Page<AgentAlertEvent> page = agentAlertService.page(pageNum, pageSize);
        return Response.success(PageInfoUtils.of(page, this::toView));
    }

    /**
     * 确认告警。
     *
     * @param id 告警 id
     * @return 成功或 404
     */
    @PostMapping("/{id}/ack")
    public Response<Void> ack(@PathVariable Long id) {
        log.info("API agent-alerts ack: id={}", id);
        boolean ok = agentAlertService.acknowledge(id);
        if (!ok) {
            log.warn("API agent-alerts ack not found: id={}", id);
            return Response.fail(CommonErrorCode.NOT_FOUND.getCode(), "告警不存在: " + id);
        }
        return Response.success(null);
    }

    /**
     * 实体转 API 视图。
     *
     * @param e 告警实体
     * @return 视图 map
     */
    private Map<String, Object> toView(AgentAlertEvent e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("ruleCode", e.getRuleCode());
        m.put("agentCode", e.getAgentCode());
        m.put("severity", e.getSeverity());
        m.put("title", e.getTitle());
        m.put("detail", e.getDetail());
        m.put("firedAt", e.getFiredAt());
        m.put("acknowledged", e.getAcknowledged());
        m.put("createTime", e.getCreateTime());
        return m;
    }
}
