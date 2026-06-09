package com.suivia.lambda.shared;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin schemaless DynamoDB helper: works with plain {@code Map<String,Object>} items
 * (like the Python lambdas' boto3 resource API) and converts to/from AttributeValue.
 */
public final class Dynamo {

    private static final DynamoDbClient C = Aws.DDB;

    private Dynamo() {}

    // ───────────────────────── conversion ─────────────────────────

    @SuppressWarnings("unchecked")
    public static AttributeValue av(Object o) {
        if (o == null) {
            return AttributeValue.fromNul(true);
        }
        if (o instanceof String s) {
            return AttributeValue.fromS(s);
        }
        if (o instanceof Boolean b) {
            return AttributeValue.fromBool(b);
        }
        if (o instanceof Number n) {
            return AttributeValue.fromN(n.toString());
        }
        if (o instanceof Map<?, ?> m) {
            Map<String, AttributeValue> mm = new HashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                mm.put(String.valueOf(e.getKey()), av(e.getValue()));
            }
            return AttributeValue.fromM(mm);
        }
        if (o instanceof Iterable<?> it) {
            List<AttributeValue> l = new ArrayList<>();
            for (Object x : it) {
                l.add(av(x));
            }
            return AttributeValue.fromL(l);
        }
        return AttributeValue.fromS(o.toString());
    }

    public static Object plain(AttributeValue v) {
        if (v == null || Boolean.TRUE.equals(v.nul())) {
            return null;
        }
        if (v.s() != null) {
            return v.s();
        }
        if (v.bool() != null) {
            return v.bool();
        }
        if (v.n() != null) {
            BigDecimal bd = new BigDecimal(v.n());
            if (bd.scale() <= 0) {
                try {
                    return bd.longValueExact();
                } catch (ArithmeticException e) {
                    return bd;
                }
            }
            return bd.doubleValue();
        }
        if (v.hasM()) {
            Map<String, Object> m = new HashMap<>();
            for (Map.Entry<String, AttributeValue> e : v.m().entrySet()) {
                m.put(e.getKey(), plain(e.getValue()));
            }
            return m;
        }
        if (v.hasL()) {
            List<Object> l = new ArrayList<>();
            for (AttributeValue x : v.l()) {
                l.add(plain(x));
            }
            return l;
        }
        return null;
    }

    public static Map<String, AttributeValue> item(Map<String, Object> m) {
        Map<String, AttributeValue> out = new HashMap<>();
        for (Map.Entry<String, Object> e : m.entrySet()) {
            out.put(e.getKey(), av(e.getValue()));
        }
        return out;
    }

    public static Map<String, Object> plainItem(Map<String, AttributeValue> m) {
        Map<String, Object> out = new HashMap<>();
        for (Map.Entry<String, AttributeValue> e : m.entrySet()) {
            out.put(e.getKey(), plain(e.getValue()));
        }
        return out;
    }

    // ───────────────────────── operations ─────────────────────────

    /** Returns the item as a plain map, or {@code null} when not found. */
    public static Map<String, Object> getItem(String table, String keyName, String keyVal) {
        var r = C.getItem(b -> b.tableName(table)
                .key(Map.of(keyName, AttributeValue.fromS(keyVal))));
        if (!r.hasItem() || r.item().isEmpty()) {
            return null;
        }
        return plainItem(r.item());
    }

    public static void putItem(String table, Map<String, Object> item) {
        C.putItem(b -> b.tableName(table).item(item(item)));
    }

    public static void delete(String table, String keyName, String keyVal) {
        C.deleteItem(b -> b.tableName(table).key(Map.of(keyName, AttributeValue.fromS(keyVal))));
    }

    /** Atomic counter increment; returns the new value. */
    public static long increment(String table, String keyName, String keyVal, String attr, long by) {
        var r = C.updateItem(b -> b.tableName(table)
                .key(Map.of(keyName, AttributeValue.fromS(keyVal)))
                .updateExpression("ADD #c :v")
                .expressionAttributeNames(Map.of("#c", attr))
                .expressionAttributeValues(Map.of(":v", AttributeValue.fromN(Long.toString(by))))
                .returnValues(ReturnValue.UPDATED_NEW));
        AttributeValue v = r.attributes().get(attr);
        return (v != null && v.n() != null) ? Long.parseLong(v.n()) : by;
    }

    public static List<Map<String, Object>> query(String table, String index, String keyName, String keyVal) {
        var r = C.query(b -> b.tableName(table).indexName(index)
                .keyConditionExpression("#k = :v")
                .expressionAttributeNames(Map.of("#k", keyName))
                .expressionAttributeValues(Map.of(":v", AttributeValue.fromS(keyVal))));
        List<Map<String, Object>> out = new ArrayList<>();
        for (var it : r.items()) {
            out.add(plainItem(it));
        }
        return out;
    }

    public static List<Map<String, Object>> scan(String table) {
        return scan(table, null);
    }

    public static List<Map<String, Object>> scan(String table, Integer limit) {
        var r = C.scan(b -> {
            b.tableName(table);
            if (limit != null) {
                b.limit(limit);
            }
        });
        List<Map<String, Object>> out = new ArrayList<>();
        for (var it : r.items()) {
            out.add(plainItem(it));
        }
        return out;
    }

    /** Generic {@code SET} update; aliases every attribute name to dodge reserved words (e.g. status). */
    public static void update(String table, String keyName, String keyVal, Map<String, Object> attrs) {
        Map<String, String> names = new HashMap<>();
        Map<String, AttributeValue> values = new HashMap<>();
        List<String> sets = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, Object> e : attrs.entrySet()) {
            String nk = "#k" + i;
            String vk = ":v" + i;
            names.put(nk, e.getKey());
            values.put(vk, av(e.getValue()));
            sets.add(nk + "=" + vk);
            i++;
        }
        String expr = "SET " + String.join(", ", sets);
        C.updateItem(b -> b.tableName(table)
                .key(Map.of(keyName, AttributeValue.fromS(keyVal)))
                .updateExpression(expr)
                .expressionAttributeNames(names)
                .expressionAttributeValues(values));
    }
}
