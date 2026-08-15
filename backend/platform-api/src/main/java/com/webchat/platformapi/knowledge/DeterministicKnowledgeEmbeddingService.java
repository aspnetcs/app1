package com.webchat.platformapi.knowledge;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class DeterministicKnowledgeEmbeddingService {
    public static final int DIMENSIONS = 1536;

    public float[] embed(String text) {
        float[] vector = new float[DIMENSIONS];
        if (text == null || text.isBlank()) return vector;
        for (String token : tokenize(text)) {
            if (token.isBlank()) continue;
            int idx = Math.floorMod(token.hashCode(), DIMENSIONS);
            vector[idx] += 1f;
            vector[Math.floorMod(idx * 31, DIMENSIONS)] += 0.5f;
        }
        normalize(vector);
        return vector;
    }

    public String encode(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(Float.toString(vector[i]));
        }
        return sb.append(']').toString();
    }

    public float[] decode(String encoded) {
        float[] vector = new float[DIMENSIONS];
        if (encoded == null || encoded.isBlank()) return vector;
        String body = encoded.trim();
        if (body.startsWith("[")) body = body.substring(1);
        if (body.endsWith("]")) body = body.substring(0, body.length() - 1);
        String[] parts = body.isBlank() ? new String[0] : body.split(",");
        int max = Math.min(parts.length, DIMENSIONS);
        for (int i = 0; i < max; i++) {
            try { vector[i] = Float.parseFloat(parts[i].trim()); } catch (NumberFormatException ignored) {}
        }
        return vector;
    }

    public double cosine(float[] left, float[] right) {
        double dot = 0d, ln = 0d, rn = 0d;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            ln += left[i] * left[i];
            rn += right[i] * right[i];
        }
        if (ln == 0d || rn == 0d) {
            return 0d;
        }
        return dot / (Math.sqrt(ln) * Math.sqrt(rn));
    }

    private static void normalize(float[] vector) {
        double norm = 0d;
        for (float v : vector) norm += v * v;
        if (norm == 0d) return;
        float scale = (float) (1d / Math.sqrt(norm));
        for (int i = 0; i < vector.length; i++) vector[i] *= scale;
    }

    private static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        String normalized = text.toLowerCase(Locale.ROOT);
        StringBuilder word = new StringBuilder();
        String previousCjk = null;
        for (int offset = 0; offset < normalized.length(); ) {
            int codePoint = normalized.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (isAsciiWord(codePoint)) {
                previousCjk = null;
                word.appendCodePoint(codePoint);
                continue;
            }
            if (!word.isEmpty()) {
                tokens.add(word.toString());
                word.setLength(0);
            }
            if (isCjk(codePoint)) {
                String current = new String(Character.toChars(codePoint));
                tokens.add(current);
                if (previousCjk != null) {
                    tokens.add(previousCjk + current);
                }
                previousCjk = current;
                continue;
            }
            previousCjk = null;
        }
        if (!word.isEmpty()) {
            tokens.add(word.toString());
        }
        return tokens;
    }

    private static boolean isAsciiWord(int codePoint) {
        return (codePoint >= 'a' && codePoint <= 'z') || (codePoint >= '0' && codePoint <= '9');
    }

    private static boolean isCjk(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }
}
