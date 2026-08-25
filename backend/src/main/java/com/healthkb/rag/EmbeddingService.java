package com.healthkb.rag;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingService {

    private final int dim;

    public EmbeddingService(@Value("${app.rag.embedding-dim:256}") int dim) {
        this.dim = dim;
    }

    public int dimension() {
        return dim;
    }

    public float[] embed(String text) {
        float[] vec = new float[dim];
        if (text == null || text.isBlank()) {
            return vec;
        }
        String norm = normalize(text);
        int n = norm.length();
        for (int i = 0; i < n; i++) {
            addFeature(vec, norm.substring(i, i + 1), 1.0f);
            if (i + 1 < n) {
                addFeature(vec, norm.substring(i, i + 2), 1.6f);
            }
            if (i + 2 < n) {
                addFeature(vec, norm.substring(i, i + 3), 0.9f);
            }
        }
        // extra weight for ASCII tokens (drug names, units)
        for (String token : norm.split("[^a-z0-9\\u4e00-\\u9fff]+")) {
            if (token.length() >= 2) {
                addFeature(vec, "tok:" + token, 2.2f);
            }
        }
        l2Normalize(vec);
        return vec;
    }

    public static String normalize(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c) || isPunct(c)) {
                continue;
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    private static boolean isPunct(char c) {
        return "，。！？、；：,.!?;:()（）[]【】\"'“”‘’·…—-_/\\|*".indexOf(c) >= 0;
    }

    private void addFeature(float[] vec, String feature, float weight) {
        int h = feature.hashCode();
        int idx = Math.floorMod(h, dim);
        int sign = ((h >>> 16) & 1) == 0 ? 1 : -1;
        vec[idx] += sign * weight;
        int idx2 = Math.floorMod(Integer.rotateLeft(h, 13), dim);
        vec[idx2] += ((h >>> 8) & 1) == 0 ? weight * 0.35f : -weight * 0.35f;
    }

    private static void l2Normalize(float[] vec) {
        double sum = 0;
        for (float v : vec) {
            sum += (double) v * v;
        }
        if (sum <= 1e-12) {
            return;
        }
        float inv = (float) (1.0 / Math.sqrt(sum));
        for (int i = 0; i < vec.length; i++) {
            vec[i] *= inv;
        }
    }
}
