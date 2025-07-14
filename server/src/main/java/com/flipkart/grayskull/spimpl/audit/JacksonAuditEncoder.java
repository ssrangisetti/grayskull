package com.flipkart.grayskull.spimpl.audit;

import ch.qos.logback.core.encoder.EncoderBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;

import java.nio.charset.StandardCharsets;

@AllArgsConstructor
public class JacksonAuditEncoder<E> extends EncoderBase<E> {

    private static final byte[] EMPTY_BYTES = new byte[0];
    private final ObjectMapper objectMapper;

    @Override
    public byte[] headerBytes() {
        return EMPTY_BYTES;
    }

    @Override
    @SneakyThrows
    public byte[] encode(E event) {
        return (objectMapper.writeValueAsString(event) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public byte[] footerBytes() {
        return EMPTY_BYTES;
    }
}
