package org.deepstack.ai.app.settings;

import org.deepstack.ai.app.settings.model.dto.response.SysConfigResponse;

import java.util.List;
import java.util.Map;

/**
 * 系统设置管理：列表与批量更新。
 */
public interface SettingsService {

    /** 列出全部运营配置（含展示字段与 source）。 */
    List<SysConfigResponse> listAll();

    /**
     * 批量更新；写库后刷新 Redis 并 PUBLISH 失效频道。
     *
     * @param values key → value（是否类非空须为 {@code 0}/{@code 1}；空串清除覆盖）
     */
    void updateAll(Map<String, String> values);
}
