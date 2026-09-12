package com.sulf.dyndb.persistence.domain;

import com.sulf.dyndb.exception.InvalidCursorException;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.thirdparty.jackson.core.JsonProcessingException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class CursorCodec {

    private final ObjectMapper objectMapper;

    public CursorCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encode(PaymentCursor cursor){
        String json = objectMapper.writeValueAsString(cursor);
        return Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));

    }

    public PaymentCursor decode(String cursor) {
        try {
            byte[] decoded =
                    Base64.getUrlDecoder()
                            .decode(cursor);

            String json =
                    new String(
                            decoded,
                            StandardCharsets.UTF_8
                    );

            return objectMapper.readValue(
                    json,
                    PaymentCursor.class
            );

        } catch (Exception exception) {
            throw new InvalidCursorException("Invalid pagination cursor");
        }
    }
}
