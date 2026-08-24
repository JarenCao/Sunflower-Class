package com.sunflower_class.base.config;

import java.text.SimpleDateFormat;
import java.util.TimeZone;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.annotation.JsonInclude;

@Configuration
public class JacksonConfig {

    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final String TIMEZONE = "Asia/Shanghai";

    private JsonInclude.Value applyNonNullInclusion(JsonInclude.Value incl) {
        return incl.withValueInclusion(JsonInclude.Include.NON_NULL)
                .withContentInclusion(JsonInclude.Include.NON_NULL);
    }

    @Bean
    public JsonMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> builder
                .defaultDateFormat(new SimpleDateFormat(DATE_TIME_PATTERN))
                .defaultTimeZone(TimeZone.getTimeZone(TIMEZONE))
                .changeDefaultPropertyInclusion(this::applyNonNullInclusion);
    }
}