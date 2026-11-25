package io.retit.greensoftware.web.format;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Writes the JSON attributes the scripts read. */
public final class Json {

    /** Without nulls, which the scripts don't tell from missing values. */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);

    private Json() {
    }

    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Not serializable: " + value, e);
        }
    }
}
