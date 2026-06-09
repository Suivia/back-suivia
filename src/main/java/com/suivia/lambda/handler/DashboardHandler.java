package com.suivia.lambda.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.suivia.lambda.shared.Api;
import com.suivia.lambda.shared.Dynamo;
import com.suivia.lambda.shared.Val;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** GET /dashboard — RF12 indicators */
public class DashboardHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final String MATCH = System.getenv("MATCH_TABLE");
    private static final String DIVERG = System.getenv("DIVERG_TABLE");

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        List<Map<String, Object>> allMatch = Dynamo.scan(MATCH);
        int total = allMatch.size();
        int approved = 0;
        int divergent = 0;
        int rejected = 0;

        // Volume per day (last 7), keyed by yyyy-MM-dd
        TreeMap<String, int[]> volume = new TreeMap<>(); // [aprovadas, divergentes, rejeitadas]
        for (Map<String, Object> m : allMatch) {
            String s = Val.str(m.get("status"));
            boolean isApproved = s.equals("APROVADA") || s.equals("approved");
            boolean isDivergent = s.contains("DIVERG");
            boolean isRejected = s.contains("REJEIT");
            if (isApproved) {
                approved++;
            }
            if (isDivergent) {
                divergent++;
            }
            if (isRejected) {
                rejected++;
            }

            String created = Val.str(m.get("created_at"));
            String day = created.length() >= 10 ? created.substring(0, 10) : created;
            if (day.isEmpty()) {
                continue;
            }
            int[] v = volume.computeIfAbsent(day, k -> new int[3]);
            if (s.contains("APROV") || s.equals("approved")) {
                v[0]++;
            } else if (isDivergent) {
                v[1]++;
            } else if (isRejected) {
                v[2]++;
            }
        }

        long touchless = total > 0 ? Math.round(approved * 100.0 / total) : 0;

        List<Map<String, Object>> volumeList = new ArrayList<>();
        List<String> days = new ArrayList<>(volume.keySet());
        List<String> last7 = days.subList(Math.max(0, days.size() - 7), days.size());
        for (String day : last7) {
            int[] v = volume.get(day);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("day", day);
            row.put("aprovadas", v[0]);
            row.put("divergentes", v[1]);
            row.put("rejeitadas", v[2]);
            volumeList.add(row);
        }

        // Top divergence types
        List<Map<String, Object>> allDiv = Dynamo.scan(DIVERG);
        Map<String, Integer> typeCount = new LinkedHashMap<>();
        for (Map<String, Object> d : allDiv) {
            String t = Val.str(d.get("divergence_type"), "other");
            typeCount.merge(t, 1, Integer::sum);
        }
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(typeCount.entrySet());
        sorted.sort(Comparator.comparingInt((Map.Entry<String, Integer> e) -> e.getValue()).reversed());
        List<List<Object>> top = new ArrayList<>();
        for (Map.Entry<String, Integer> e : sorted.subList(0, Math.min(5, sorted.size()))) {
            top.add(List.of(e.getKey(), e.getValue()));
        }

        Map<String, Object> cards = new LinkedHashMap<>();
        cards.put("processed", total);
        cards.put("touchless", touchless);
        cards.put("divergent", divergent);
        cards.put("rejected", rejected);
        cards.put("avgTime", "estimado");

        List<Map<String, Object>> pie = List.of(
                Map.of("name", "Aprovadas", "value", approved, "color", "#22c55e"),
                Map.of("name", "Divergentes", "value", divergent, "color", "#eab308"),
                Map.of("name", "Rejeitadas", "value", rejected, "color", "#ef4444")
        );

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cards", cards);
        out.put("volume", volumeList);
        out.put("pie", pie);
        out.put("top", top);
        return Api.ok(out);
    }
}
