package org.deepstack.ai.kernel.jackson;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import org.springframework.boot.jackson.JacksonComponent;

/**
 * 将 {@link Long} 序列化为 JSON 字符串，避免前端 JS 精度丢失（雪花 ID 等）。
 * <p>反序列化仍由 Jackson 默认处理：字符串数字可绑定回 {@code Long}。</p>
 */
@JacksonComponent
public class LongAsStringJacksonComponent {

    /** Long → JSON string。 */
    public static class Serializer extends ValueSerializer<Long> {

        @Override
        public void serialize(Long value, JsonGenerator gen, SerializationContext ctxt) {
            if (value == null) {
                gen.writeNull();
            } else {
                gen.writeString(Long.toString(value));
            }
        }
    }
}
