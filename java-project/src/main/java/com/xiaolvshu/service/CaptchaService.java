package com.xiaolvshu.service;

import cn.hutool.core.util.RandomUtil;
import com.xiaolvshu.dto.CaptchaResponse;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SVG验证码服务
 */
@Service
public class CaptchaService {

    private static final long EXPIRES_MILLIS = 30_000L;
    private static final int WIDTH = 130;
    private static final int HEIGHT = 48;
    private static final int CODE_COUNT = 4;

    private final Map<String, CaptchaItem> store = new ConcurrentHashMap<>();

    public CaptchaResponse generateCaptcha() {
        cleanExpired();
        String code = generateRandomCode();
        String captchaId = buildCaptchaId();
        store.put(captchaId, new CaptchaItem(code.toLowerCase(), System.currentTimeMillis() + EXPIRES_MILLIS));
        
        // 生成SVG验证码
        String svgContent = generateSvgCaptcha(code);
        return new CaptchaResponse(captchaId, svgContent);
    }

    private String generateRandomCode() {
        // 排除容易混淆的字符
        String chars = "ABCDEFGHJKLMNPQRSTUVWXY3456789";
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_COUNT; i++) {
            code.append(chars.charAt(RandomUtil.randomInt(0, chars.length())));
        }
        return code.toString();
    }

    private String generateSvgCaptcha(String code) {
        StringBuilder svg = new StringBuilder();
        
        // SVG头部
        svg.append("<svg xmlns='http://www.w3.org/2000/svg' ")
           .append("width='").append(WIDTH).append("' ")
           .append("height='").append(HEIGHT).append("' ")
           .append("viewBox='0 0 ").append(WIDTH).append(" ").append(HEIGHT).append("'>");
        
        // 随机背景色
        String bgColor = getRandomLightColor();
        svg.append("<rect width='100%' height='100%' fill='").append(bgColor).append("'/>");
        
        // 添加干扰线
        for (int i = 0; i < 3; i++) {
            svg.append("<line x1='").append(RandomUtil.randomInt(0, WIDTH))
               .append("' y1='").append(RandomUtil.randomInt(0, HEIGHT))
               .append("' x2='").append(RandomUtil.randomInt(0, WIDTH))
               .append("' y2='").append(RandomUtil.randomInt(0, HEIGHT))
               .append("' stroke='").append(getRandomDarkColor())
               .append("' stroke-width='1' opacity='0.3'/>");
        }
        
        // 添加字符
        int charWidth = WIDTH / CODE_COUNT;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            int x = charWidth * i + charWidth / 2 + RandomUtil.randomInt(-8, 8);
            int y = HEIGHT / 2 + RandomUtil.randomInt(-5, 5);
            int fontSize = 20 + RandomUtil.randomInt(-3, 3);
            int rotation = RandomUtil.randomInt(-15, 15);
            
            svg.append("<text x='").append(x)
               .append("' y='").append(y)
               .append("' font-family='Arial, sans-serif' font-size='").append(fontSize)
               .append("' font-weight='bold' fill='").append(getRandomDarkColor())
               .append("' text-anchor='middle' dominant-baseline='middle'");
            
            if (rotation != 0) {
                svg.append(" transform='rotate(").append(rotation)
                   .append(" ").append(x).append(" ").append(y).append(")'");
            }
            
            svg.append(">").append(c).append("</text>");
        }
        
        // 添加干扰点
        for (int i = 0; i < 30; i++) {
            svg.append("<circle cx='").append(RandomUtil.randomInt(0, WIDTH))
               .append("' cy='").append(RandomUtil.randomInt(0, HEIGHT))
               .append("' r='1' fill='").append(getRandomDarkColor())
               .append("' opacity='0.4'/>");
        }
        
        svg.append("</svg>");
        return svg.toString();
    }

    private String getRandomLightColor() {
        // 生成浅色背景
        int r = 200 + RandomUtil.randomInt(0, 55);
        int g = 200 + RandomUtil.randomInt(0, 55);
        int b = 200 + RandomUtil.randomInt(0, 55);
        return String.format("#%02x%02x%02x", r, g, b);
    }

    private String getRandomDarkColor() {
        // 生成深色前景
        int r = RandomUtil.randomInt(0, 100);
        int g = RandomUtil.randomInt(0, 100);
        int b = RandomUtil.randomInt(0, 100);
        return String.format("#%02x%02x%02x", r, g, b);
    }

    public boolean validateCaptcha(String captchaId, String userInput) {
        if (captchaId == null || userInput == null) {
            return false;
        }
        cleanExpired();
        CaptchaItem item = store.remove(captchaId);
        return item != null && userInput.toLowerCase().equals(item.code());
    }

    private void cleanExpired() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(entry -> now > entry.getValue().expiresAt());
    }

    private String buildCaptchaId() {
        return System.currentTimeMillis() + RandomUtil.randomString(9);
    }

    private record CaptchaItem(String code, long expiresAt) {}
}