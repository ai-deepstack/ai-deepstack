package org.deepstack.ai.tool.mcp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * 解包 MCP 工具返回值的 {@link ToolCallback} 装饰器。
 *
 * <p>Spring AI 的 {@code SyncMcpToolCallback#call} 返回的是
 * {@code MCP List&lt;Content&gt;} 序列化后的 JSON 数组字符串，形如：</p>
 * <pre>
 * [{"type":"text","text":"{\"ok\":true,...}"}]
 * </pre>
 *
 * <p>模型拿到的是「content 数组结构」而不是工具输出正文，会把它当成工具说明而非执行结果。
 * 这里把数组里的 text 字段取出并拼接，返回可直接进上下文的纯文本。</p>
 *
 * <p>多段 content（text/image 混排）按顺序拼接 text 段；非文本段用日志标记，
 * 避免静默丢内容。</p>
 */
@Slf4j
class UnwrappingMcpToolCallback implements ToolCallback {

	private final ToolCallback delegate;

	UnwrappingMcpToolCallback(ToolCallback delegate) {
		this.delegate = delegate;
	}

	@Override
	public ToolDefinition getToolDefinition() {
		return delegate.getToolDefinition();
	}

	@Override
	public String call(String toolCallInput) {
		return unwrap(delegate.call(toolCallInput));
	}

	@Override
	public String call(String toolCallInput, ToolContext toolContext) {
		return unwrap(delegate.call(toolCallInput, toolContext));
	}

	/** 把 MCP 的 content 数组 JSON 解成纯文本；无法解析时原样返回。 */
	private String unwrap(String raw) {
		if (raw == null || raw.isBlank()) {
			return raw;
		}
		String trimmed = raw.trim();
		if (!trimmed.startsWith("[")) {
			// 不是 content 数组（理论上是兼容路径），原样返回
			return raw;
		}

		try {
			JSONArray array = JSON.parseArray(trimmed);
			StringBuilder text = new StringBuilder();
			int nonText = 0;
			for (int i = 0; i < array.size(); i += 1) {
				Object item = array.get(i);
				if (!(item instanceof JSONObject object)) {
					continue;
				}
				String type = object.getString("type");
				if ("text".equals(type)) {
					String content = object.getString("text");
					if (content != null) {
						if (!text.isEmpty()) {
							text.append('\n');
						}
						text.append(content);
					}
				} else {
					nonText += 1;
				}
			}
			if (!text.isEmpty()) {
				if (nonText > 0) {
					log.warn("UnwrappingMcpToolCallback: 忽略 {} 个非文本 content 段", nonText);
				}
				return text.toString();
			}
		} catch (Exception e) {
			log.warn("UnwrappingMcpToolCallback: 解包失败，原样返回上游字符串: {}", e.getMessage());
		}
		return raw;
	}
}
