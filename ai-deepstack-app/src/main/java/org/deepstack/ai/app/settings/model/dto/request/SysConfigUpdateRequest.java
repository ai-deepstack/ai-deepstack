package org.deepstack.ai.app.settings.model.dto.request;

import lombok.Data;

import java.util.Map;

/**
 * 批量更新系统配置。
 */
@Data
public class SysConfigUpdateRequest {

    /** configKey → configValue；是否类非空时仅允许 {@code 0}/{@code 1}；空串表示清除库覆盖。 */
    private Map<String, String> values;
}
