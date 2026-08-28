package com.healthkb.config;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.module.SimpleModule;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/**
 * 前端与 CSV 里普遍存在「2026-01-31 08:30」这类带空格的时间串（el-date-picker、
 * Excel 导出），而 spring.jackson.date-format 只作用于 java.util.Date，
 * JavaTimeModule 默认只认 ISO 的 'T' 分隔 —— 不在这里放开会到处冒 400。
 * 解析失败的错误信息保持可读，其余行为不变。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public SimpleModule flexibleJavaTimeModule() {
        SimpleModule module = new SimpleModule("flexible-java-time");
        module.addDeserializer(LocalDateTime.class, new FlexibleLocalDateTimeDeserializer());
        module.addDeserializer(LocalDate.class, new FlexibleLocalDateDeserializer());
        return module;
    }

    /** 先按标准 ISO 解析，失败再接受空格分隔；错误信息带原始值便于用户自查。 */
    static class FlexibleLocalDateTimeDeserializer extends ValueDeserializer<LocalDateTime> {
        @Override
        public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
            String text = p.getValueAsString();
            if (text == null || text.isBlank()) {
                return null;
            }
            try {
                return LocalDateTime.parse(text.trim());
            } catch (DateTimeParseException ignored) {
                // fall through 尝试空格分隔
            }
            try {
                return LocalDateTime.parse(text.trim().replace(' ', 'T'));
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("时间格式不正确：" + text + "（应为 2026-01-31 08:30:00）", e);
            }
        }
    }

    static class FlexibleLocalDateDeserializer extends ValueDeserializer<LocalDate> {
        @Override
        public LocalDate deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
            String text = p.getValueAsString();
            if (text == null || text.isBlank()) {
                return null;
            }
            try {
                return LocalDate.parse(text.trim());
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("日期格式不正确：" + text + "（应为 2026-01-31）", e);
            }
        }
    }
}
