package com.suivia.lambda.shared;

import java.util.Arrays;

/**
 * Ratcliff-Obershelp similarity — the same algorithm Python's
 * {@code difflib.SequenceMatcher(None, a, b).ratio()} uses, so the
 * fuzzy item-matching scores port faithfully (RF08).
 */
public final class Similarity {

    private Similarity() {}

    public static double ratio(String a, String b) {
        if (a == null) {
            a = "";
        }
        if (b == null) {
            b = "";
        }
        int total = a.length() + b.length();
        if (total == 0) {
            return 1.0;
        }
        return 2.0 * matches(a, b) / total;
    }

    private static int matches(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        int bestLen = 0;
        int bestI = 0;
        int bestJ = 0;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    cur[j] = prev[j - 1] + 1;
                    if (cur[j] > bestLen) {
                        bestLen = cur[j];
                        bestI = i;
                        bestJ = j;
                    }
                } else {
                    cur[j] = 0;
                }
            }
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
            Arrays.fill(cur, 0);
        }
        if (bestLen == 0) {
            return 0;
        }
        String aLeft = a.substring(0, bestI - bestLen);
        String bLeft = b.substring(0, bestJ - bestLen);
        String aRight = a.substring(bestI);
        String bRight = b.substring(bestJ);
        return bestLen + matches(aLeft, bLeft) + matches(aRight, bRight);
    }
}
