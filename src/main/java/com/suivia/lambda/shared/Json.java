package com.suivia.lambda.shared;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Jackson helpers — mirrors the json.dumps / json.loads usage in the Python lambdas. */
public final class Json {

    public static final ObjectMapper MAPPER = new ObjectMapper();

    private Json() {}

    public static String write(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            throw new RuntimeException("JSON write failed", e);
        }
    }

    public static Map<String, Object> readMap(String s) {
        if (s == null || s.isBlank()) {
            return new HashMap<>();
        }
        try {
            return MAPPER.readValue(s, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException("JSON readMap failed", e);
        }
    }

    public static List<Object> readList(String s) {
        if (s == null || s.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return MAPPER.readValue(s, new TypeReference<List<Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException("JSON readList failed", e);
        }
    }
}
