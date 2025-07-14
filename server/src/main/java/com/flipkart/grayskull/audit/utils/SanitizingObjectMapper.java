package com.flipkart.grayskull.audit.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * A factory for creating a pre-configured {@link ObjectMapper} for sanitizing audit data.
 * The created mapper is equipped with a {@link AuditMaskBeanSerializerModifier} to automatically
 * mask fields annotated with {@link com.flipkart.grayskull.models.audit.AuditMask}.
 * It also includes the {@link JavaTimeModule} to ensure correct serialization of Java 8 date/time types.
 */
@Slf4j
@UtilityClass
public class SanitizingObjectMapper {

    private static final ObjectMapper MASK_OBJECT_MAPPER = SanitizingObjectMapper.create();

    /**
     * Creates and configures an {@link ObjectMapper} with sanitization capabilities.
     *
     * @return A new, configured {@link ObjectMapper} instance.
     */
    public static ObjectMapper create() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        SimpleModule module = new SimpleModule();
        module.setSerializerModifier(new AuditMaskBeanSerializerModifier());
        mapper.registerModule(module);

        return mapper;
    }

    public static void addToMap(Map<String, String> map, String key, Object value) {
        try {
            map.put(key, MASK_OBJECT_MAPPER.writeValueAsString(value));
        } catch (JsonProcessingException e) {
            log.debug("Error serializing object: {}", e.getMessage(), e);
            map.put(key, "Error serializing object: " + e.getMessage());
        }
    }
} 