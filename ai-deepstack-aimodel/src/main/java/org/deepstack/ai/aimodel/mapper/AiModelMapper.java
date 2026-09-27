package org.deepstack.ai.aimodel.mapper;

import org.deepstack.ai.aimodel.model.entity.AiModel;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 模型配置 Mapper（CHAT + EMBEDDING）
 *
 * <p>v1.2 由 {@code ChatModelMapper} 重命名而来，对应表 {@code ai_model}。</p>
 *
 */
@Mapper
public interface AiModelMapper extends BaseMapper<AiModel> {
}
