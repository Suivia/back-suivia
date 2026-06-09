package com.suivia.lambda.shared;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Writes an immutable audit record (RF14) — mirrors utils.write_audit. */
public final class Audit {

    private Audit() {}

    public static void write(String table, String entityId, String action, String user,
                             Object before, Object after, Object meta) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", UUID.randomUUID().toString());
        item.put("entity_id", entityId);
        item.put("action", action);
        item.put("user", user);
        item.put("before", Json.write(before == null ? Map.of() : before));
        item.put("after", Json.write(after == null ? Map.of() : after));
        item.put("meta", Json.write(meta == null ? Map.of() : meta));
        item.put("timestamp", Instant.now().toString());
        Dynamo.putItem(table, item);
    }
}
