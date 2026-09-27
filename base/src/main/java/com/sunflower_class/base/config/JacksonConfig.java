package com.sunflower_class.base.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 统一服务响应的 JSON 日期、时区与空值输出规则。
 */
@Configuration
public class JacksonConfig {

    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final String TIMEZONE = "Asia/Shanghai";

    /**
     * 将属性值和容器内容的默认序列化策略设为忽略空值。
     */
    private JsonInclude.Value applyNonNullInclusion(JsonInclude.Value incl) {
        return incl
            .withValueInclusion(JsonInclude.Include.NON_NULL)
            .withContentInclusion(JsonInclude.Include.NON_NULL);
    }

    /**
     * 注册 JSON 序列化定制器，统一日期格式、上海时区和空值输出策略。
     */
    @Bean
    public JsonMapperBuilderCustomizer jacksonCustomizer() {
        // Spring 调用此回调，将统一日期、时区和空值规则应用到映射器构建器。
        return builder ->
            builder
                .defaultDateFormat(new SimpleDateFormat(DATE_TIME_PATTERN))
                .defaultTimeZone(TimeZone.getTimeZone(TIMEZONE))
                .changeDefaultPropertyInclusion(this::applyNonNullInclusion);
    }
}
