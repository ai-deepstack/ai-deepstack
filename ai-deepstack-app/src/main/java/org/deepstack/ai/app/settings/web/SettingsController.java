package org.deepstack.ai.app.settings.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deepstack.ai.app.settings.SettingsService;
import org.deepstack.ai.app.settings.model.dto.request.SysConfigUpdateRequest;
import org.deepstack.ai.app.settings.model.dto.response.SysConfigResponse;
import org.deepstack.ai.kernel.model.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 运营系统设置 API。
 */
@Slf4j
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    /**
     * 列出全部运营配置。
     * <p>
     * 每项含 id、configKey、configName、configValue、valueType、description、valueName、modelType、group、source。
     * valueType=model 时前端按 modelType 调用 /api/models/options。
     * </p>
     */
    @GetMapping
    public Response<List<SysConfigResponse>> list() {
        return Response.success(settingsService.listAll());
    }

    /**
     * 批量更新；是否类提交 0/1（空串清除库覆盖）；model 类提交 model_code（可空）。
     */
    @PutMapping
    public Response<Void> update(@RequestBody SysConfigUpdateRequest req) {
        log.info("API update settings: keys={}",
                req != null && req.getValues() != null ? req.getValues().keySet() : null);
        settingsService.updateAll(req != null ? req.getValues() : null);
        return Response.success();
    }
}
