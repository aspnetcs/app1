package com.webchat.platformapi.auth.verification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.common.util.RequestUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class CaptchaService {

    private static final Duration CAPTCHA_TTL = Duration.ofSeconds(120);
    private static final Duration CHALLENGE_TTL = Duration.ofSeconds(120);
    private static final Duration CAPTCHA_RATE_TTL = Duration.ofMinutes(1);
    private static final int CAPTCHA_GENERATE_LIMIT_PER_MINUTE = 20;
    private static final int CAPTCHA_VERIFY_LIMIT_PER_MINUTE = 30;

    // slider 容忍误差 (百分比)
    private static final double SLIDER_TOLERANCE = 0.02;
    private static final int SLIDER_MIN_TRACK_POINTS = 5;
    private static final long SLIDER_MIN_SLIDE_TIME_MS = 200;
    // text 点击容忍半径 (像素，基于 600x360 画布)
    private static final double TEXT_CLICK_RADIUS = 30;

    private static final int BG_WIDTH = 600;
    private static final int BG_HEIGHT = 360;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final List<String> backgroundImagePaths;

    public CaptchaService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.backgroundImagePaths = loadBackgroundImagePaths();
    }

    private List<String> loadBackgroundImagePaths() {
        List<String> paths = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            String name = "captchaImages/bg" + i + ".jpg";
            if (classpathResourceExists(name)) {
                paths.add(name);
            }
            String namePng = "captchaImages/bg" + i + ".png";
            if (classpathResourceExists(namePng)) {
                paths.add(namePng);
            }
        }
        return paths;
    }

    private static boolean classpathResourceExists(String path) {
        try (InputStream ignored = new ClassPathResource(path).getInputStream()) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private BufferedImage loadRandomBackground() throws IOException {
        if (backgroundImagePaths.isEmpty()) {
            throw new IOException("No background images available");
        }
        String path = backgroundImagePaths.get(ThreadLocalRandom.current().nextInt(backgroundImagePaths.size()));
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            return ImageIO.read(is);
        }
    }

    // ==================== 统一入口 ====================

    public Map<String, Object> generateCaptcha() {
        // 开发环境固定使用 math 类型（前端 admin-lite 可自动解题）
        try {
            return generateMathCaptcha();
        } catch (Exception e) {
            // 降级重试一次 math
            return generateMathCaptcha();
        }
    }

    /**
     * 统一验证入口：根据 Redis 中存储的 type 分发
     */
    public String verifyCaptchaAndIssueChallenge(String captchaId, Map<String, Object> answerData) {
        return verifyCaptchaAndIssueChallenge(captchaId, answerData, "");
    }

    public String verifyCaptchaAndIssueChallenge(String captchaId, Map<String, Object> answerData, String requesterIp) {
        if (captchaId == null || answerData == null) return null;

        String key = "captcha:" + captchaId;
        String json = redis.opsForValue().get(key);
        if (json == null || json.isBlank()) return null;
        redis.delete(key);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> cached = objectMapper.readValue(json, Map.class);
            String type = String.valueOf(cached.get("type"));

            boolean success = switch (type) {
                case "slider" -> verifySlider(cached, answerData);
                case "text" -> verifyText(cached, answerData);
                case "math" -> verifyMath(cached, answerData);
                default -> false;
            };

            if (!success) return null;
        } catch (Exception e) {
            return null;
        }

        // 签发 challenge token
        String token = "CT_" + UUID.randomUUID().toString().replace("-", "") + "_" + randomHex(8);
        setJson("challenge:" + token, Map.of(
                "purpose", "auth",
                "expires_at", System.currentTimeMillis() + CHALLENGE_TTL.toMillis(),
                "ip_hash", RequestUtils.sha256Hex(normalizeRequesterIp(requesterIp))
        ), CHALLENGE_TTL);

        return token;
    }

    public boolean allowGenerate(String requesterIp) {
        return tryConsumeRate("captcha:generate", requesterIp, CAPTCHA_GENERATE_LIMIT_PER_MINUTE);
    }

    public boolean allowVerify(String requesterIp) {
        return tryConsumeRate("captcha:verify", requesterIp, CAPTCHA_VERIFY_LIMIT_PER_MINUTE);
    }

    private static String normalizeRequesterIp(String requesterIp) {
        return requesterIp == null ? "" : requesterIp.trim();
    }

    private boolean tryConsumeRate(String prefix, String requesterIp, int limitPerMinute) {
        String ip = normalizeRequesterIp(requesterIp);
        String key = prefix + ":" + (ip.isEmpty() ? "unknown" : ip);
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, CAPTCHA_RATE_TTL);
            }
            return count == null || count <= limitPerMinute;
        } catch (Exception e) {
            return true;
        }
    }

    // ==================== Math 验证码 ====================

    public Map<String, Object> generateMathCaptcha() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        String[] ops = {"+", "-", "×"};
        String op = ops[r.nextInt(ops.length)];
        int a, b, answer;

        switch (op) {
            case "+" -> { a = r.nextInt(1, 50); b = r.nextInt(1, 50); answer = a + b; }
            case "-" -> { a = r.nextInt(10, 60); b = r.nextInt(1, a); answer = a - b; }
            default  -> { a = r.nextInt(2, 12); b = r.nextInt(2, 12); answer = a * b; }
        }

        String expression = a + " " + op + " " + b + " = ?";
        String captchaId = UUID.randomUUID().toString();
        setJson("captcha:" + captchaId, Map.of("type", "math", "answer", answer), CAPTCHA_TTL);

        String mathImage = renderMathImageBase64(expression);
        return Map.of(
                "captchaId", captchaId,
                "type", "math",
                "question", expression,
                "mathImage", mathImage
        );
    }



    private boolean verifyMath(Map<String, Object> cached, Map<String, Object> answerData) {
        Object expectedObj = cached.get("answer");
        Object providedObj = answerData.get("answer");
        if (expectedObj == null || providedObj == null) return false;
        int expected = expectedObj instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(expectedObj));
        int provided;
        try {
            provided = providedObj instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(providedObj));
        } catch (NumberFormatException e) {
            return false;
        }
        return expected == provided;
    }

    // ==================== Slider 验证码 ====================

    public Map<String, Object> generateSliderCaptcha() throws IOException {
        BufferedImage bgOrig = loadRandomBackground();

        // 缩放到标准尺寸
        BufferedImage bg = new BufferedImage(BG_WIDTH, BG_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D gBg = bg.createGraphics();
        gBg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        gBg.drawImage(bgOrig, 0, 0, BG_WIDTH, BG_HEIGHT, null);
        gBg.dispose();

        int pieceW = 80, pieceH = 80, tabR = 12, margin = tabR + 4;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int randomX = r.nextInt(pieceW + tabR + 60, BG_WIDTH - pieceW - tabR - 20);
        int randomY = r.nextInt(tabR + 30, BG_HEIGHT - pieceH - tabR - 20);

        // 创建拼图形状
        Shape puzzleShape = createPuzzleShape(0, 0, pieceW, pieceH, tabR);

        // 1. 背景 + 凹槽
        BufferedImage bgWithHole = new BufferedImage(BG_WIDTH, BG_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g1 = bgWithHole.createGraphics();
        g1.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g1.drawImage(bg, 0, 0, null);
        // 绘制凹槽
        AffineTransform at = AffineTransform.getTranslateInstance(randomX, randomY);
        Shape translated = at.createTransformedShape(puzzleShape);
        g1.setColor(new Color(0, 0, 0, 128));
        g1.fill(translated);
        g1.setColor(new Color(255, 255, 255, 150));
        g1.setStroke(new BasicStroke(1.5f));
        g1.draw(translated);
        g1.dispose();

        // 2. 裁切拼图块
        int sliderW = pieceW + margin * 2;
        int sliderH = pieceH + margin * 2;
        BufferedImage piece = new BufferedImage(sliderW, sliderH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = piece.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        AffineTransform atPiece = AffineTransform.getTranslateInstance(margin, margin);
        Shape clipShape = atPiece.createTransformedShape(puzzleShape);
        g2.setClip(clipShape);
        g2.drawImage(bg, -(randomX - margin), -(randomY - margin), null);
        g2.setClip(null);
        g2.setColor(new Color(255, 255, 255, 240));
        g2.setStroke(new BasicStroke(2f));
        g2.draw(clipShape);
        g2.dispose();

        String captchaId = UUID.randomUUID().toString();
        double percentX = (randomX + pieceW / 2.0) / BG_WIDTH;

        Map<String, Object> cacheData = new HashMap<>();
        cacheData.put("type", "slider");
        cacheData.put("percentX", percentX);
        cacheData.put("x", randomX);
        cacheData.put("y", randomY);
        setJson("captcha:" + captchaId, cacheData, CAPTCHA_TTL);

        Map<String, Object> result = new HashMap<>();
        result.put("captchaId", captchaId);
        result.put("type", "slider");
        result.put("backgroundImage", toBase64Jpeg(bgWithHole));
        result.put("sliderImage", toBase64Png(piece));
        result.put("pieceY", (randomY - margin) / (double) BG_HEIGHT);
        result.put("bgWidth", BG_WIDTH);
        result.put("bgHeight", BG_HEIGHT);
        result.put("sliderWidth", sliderW);
        result.put("sliderHeight", sliderH);
        return result;
    }

    private Shape createPuzzleShape(int x, int y, int w, int h, int r) {
        // 简化拼图形状：圆角矩形 + 右侧凸起 + 上方凸起
        Area area = new Area(new RoundRectangle2D.Double(x, y, w, h, 4, 4));
        // 右侧凸起
        area.add(new Area(new Ellipse2D.Double(x + w - r, y + h / 2.0 - r, r * 2, r * 2)));
        // 上方凸起
        area.add(new Area(new Ellipse2D.Double(x + w / 2.0 - r, y - r, r * 2, r * 2)));
        return area;
    }

    private boolean verifySlider(Map<String, Object> cached, Map<String, Object> answerData) {
        Object percentXObj = cached.get("percentX");
        Object movePercentObj = answerData.get("movePercent");
        if (percentXObj == null || movePercentObj == null) return false;

        double expected = percentXObj instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(percentXObj));
        double provided = movePercentObj instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(movePercentObj));

        if (Math.abs(expected - provided) > SLIDER_TOLERANCE) return false;

        return analyzeTracks(answerData.get("tracks"), answerData.get("duration"));
    }

    @SuppressWarnings("unchecked")
    private boolean analyzeTracks(Object tracksObj, Object durationObj) {
        long duration;
        try {
            duration = durationObj instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(durationObj));
        } catch (Exception e) {
            return false;
        }
        if (duration < SLIDER_MIN_SLIDE_TIME_MS) return false;

        if (!(tracksObj instanceof List<?> tracks) || tracks.size() < SLIDER_MIN_TRACK_POINTS) return false;

        Double firstY = null;
        double yChange = 0;
        for (Object tObj : tracks) {
            if (!(tObj instanceof Map<?, ?> t)) return false;
            Object yObj = t.get("y");
            if (yObj == null) return false;
            double y = yObj instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(yObj));
            if (firstY == null) {
                firstY = y;
                continue;
            }
            yChange += Math.abs(y - firstY);
        }

        // 无任何垂直变化且点数过多，疑似脚本
        if (yChange == 0 && tracks.size() > 10) return false;

        return true;
    }

    // ==================== Text 验证码 ====================

    public Map<String, Object> generateTextCaptcha() throws IOException {
        BufferedImage bgOrig = loadRandomBackground();

        BufferedImage canvas = new BufferedImage(BG_WIDTH, BG_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.drawImage(bgOrig, 0, 0, BG_WIDTH, BG_HEIGHT, null);
        // 半透明覆盖
        g.setColor(new Color(255, 255, 255, 100));
        g.fillRect(0, 0, BG_WIDTH, BG_HEIGHT);

        String allChars = "天地人和春夏秋冬山水风云日月星辰花草树木金银铜铁东西南北青红黄绿大小多少高低远近快慢";
        char[] chars = allChars.toCharArray();
        List<Character> shuffled = new ArrayList<>();
        for (char c : chars) shuffled.add(c);
        Collections.shuffle(shuffled);

        List<Character> selected = shuffled.subList(0, Math.min(9, shuffled.size()));
        List<Character> targets = new ArrayList<>(selected.subList(0, 3));

        ThreadLocalRandom r = ThreadLocalRandom.current();
        g.setFont(new Font("WenQuanYi Zen Hei", Font.BOLD, 40));

        List<int[]> positions = new ArrayList<>();
        List<Map<String, Object>> answerPoints = new ArrayList<>();

        for (int i = 0; i < selected.size(); i++) {
            char ch = selected.get(i);
            int px, py;
            boolean overlap;
            int attempts = 0;
            do {
                px = r.nextInt(40, BG_WIDTH - 40);
                py = r.nextInt(40, BG_HEIGHT - 40);
                overlap = false;
                for (int[] pos : positions) {
                    if (Math.hypot(pos[0] - px, pos[1] - py) < 80) {
                        overlap = true;
                        break;
                    }
                }
                attempts++;
            } while (overlap && attempts < 50);

            double angle = (r.nextDouble() - 0.5) * Math.PI / 4;
            AffineTransform old = g.getTransform();
            g.translate(px, py);
            g.rotate(angle);
            // 白色描边
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(4f));
            g.drawString(String.valueOf(ch), 0, 0);
            // 黑色填充
            g.setColor(new Color(51, 51, 51));
            g.drawString(String.valueOf(ch), 0, 0);
            g.setTransform(old);

            positions.add(new int[]{px, py});
            if (targets.contains(ch)) {
                int idx = targets.indexOf(ch);
                // 确保按目标顺序存储
                while (answerPoints.size() <= idx) answerPoints.add(null);
                Map<String, Object> point = new HashMap<>();
                point.put("x", px);
                point.put("y", py);
                answerPoints.set(idx, point);
            }
        }
        g.dispose();

        String captchaId = UUID.randomUUID().toString();
        Map<String, Object> cacheData = new HashMap<>();
        cacheData.put("type", "text");
        cacheData.put("points", answerPoints);
        cacheData.put("radius", TEXT_CLICK_RADIUS);
        setJson("captcha:" + captchaId, cacheData, CAPTCHA_TTL);

        List<String> targetStrings = targets.stream().map(String::valueOf).toList();
        Map<String, Object> result = new HashMap<>();
        result.put("captchaId", captchaId);
        result.put("type", "text");
        result.put("backgroundImage", toBase64Jpeg(canvas));
        result.put("targetChars", targetStrings);
        result.put("bgWidth", BG_WIDTH);
        result.put("bgHeight", BG_HEIGHT);
        return result;
    }

    @SuppressWarnings("unchecked")
    private boolean verifyText(Map<String, Object> cached, Map<String, Object> answerData) {
        Object pointsObj = answerData.get("points");
        Object expectedPointsObj = cached.get("points");
        Object radiusObj = cached.get("radius");
        if (!(pointsObj instanceof List<?> userPoints) || !(expectedPointsObj instanceof List<?> expectedPoints)) return false;

        double radius = radiusObj instanceof Number n ? n.doubleValue() : TEXT_CLICK_RADIUS;
        if (userPoints.size() != expectedPoints.size()) return false;

        for (int i = 0; i < expectedPoints.size(); i++) {
            Map<String, Object> expected = (Map<String, Object>) expectedPoints.get(i);
            Map<String, Object> provided = (Map<String, Object>) userPoints.get(i);
            if (expected == null || provided == null) return false;

            double ex = toDouble(expected.get("x"));
            double ey = toDouble(expected.get("y"));
            // 前端传百分比 px/py
            double ppx = toDouble(provided.get("px"));
            double ppy = toDouble(provided.get("py"));

            double userX = ppx * BG_WIDTH;
            double userY = ppy * BG_HEIGHT;

            if (Math.hypot(userX - ex, userY - ey) > radius * 1.5) return false;
        }
        return true;
    }

    // ==================== 工具方法 ====================

    private String toBase64Jpeg(BufferedImage img) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(img, "jpg", baos);
            return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            return "";
        }
    }

    private String toBase64Png(BufferedImage img) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", baos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            return "";
        }
    }

    private String renderMathImageBase64(String text) {
        int width = 600, height = 220;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(245, 246, 250));
        g.fillRect(0, 0, width, height);

        ThreadLocalRandom r = ThreadLocalRandom.current();
        // 干扰线
        for (int i = 0; i < 6; i++) {
            g.setColor(new Color(r.nextInt(180, 230), r.nextInt(180, 230), r.nextInt(180, 230)));
            g.drawLine(r.nextInt(width), r.nextInt(height), r.nextInt(width), r.nextInt(height));
        }
        // 干扰点
        for (int i = 0; i < 30; i++) {
            g.setColor(new Color(r.nextInt(150, 220), r.nextInt(150, 220), r.nextInt(150, 220)));
            g.fillOval(r.nextInt(width), r.nextInt(height), 4, 4);
        }

        g.setFont(new Font("WenQuanYi Zen Hei", Font.BOLD, 52));
        char[] chars = text.toCharArray();
        int startX = 30;
        int charSpacing = (width - 60) / chars.length;

        for (int i = 0; i < chars.length; i++) {
            int cx = startX + i * charSpacing + charSpacing / 2;
            int cy = height / 2 + r.nextInt(-5, 6);
            double angle = (r.nextDouble() - 0.5) * 0.3;
            AffineTransform old = g.getTransform();
            g.translate(cx, cy);
            g.rotate(angle);
            g.setColor(new Color(r.nextInt(30, 80), r.nextInt(50, 120), r.nextInt(100, 180)));
            g.drawString(String.valueOf(chars[i]), 0, 0);
            g.setTransform(old);
        }
        g.dispose();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "jpg", baos);
            return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            return "data:text/plain;charset=utf-8," + URLEncoder.encode(text, StandardCharsets.UTF_8);
        }
    }

    private void setJson(String key, Map<String, Object> value, Duration ttl) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize json", e);
        }
    }

    private static String randomHex(int bytes) {
        byte[] buf = new byte[bytes];
        ThreadLocalRandom.current().nextBytes(buf);
        StringBuilder sb = new StringBuilder(bytes * 2);
        for (byte b : buf) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static double toDouble(Object obj) {
        if (obj instanceof Number n) return n.doubleValue();
        if (obj != null) {
            try {
                return Double.parseDouble(String.valueOf(obj));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }
}
