package cn.herb.util;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** 本地生成的单次图片验证码，不发送网络请求，不保存到数据库。 */
public final class ImageCaptcha {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final Duration TTL = Duration.ofMinutes(5);
    private final Clock clock;
    private final Instant expiresAt;
    private final String answer;
    private final BufferedImage image;
    private boolean consumed;

    public ImageCaptcha() { this(new SecureRandom(), Clock.systemUTC()); }

    ImageCaptcha(SecureRandom random, Clock clock) {
        this.clock = clock;
        expiresAt = clock.instant().plus(TTL);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 4; i++) text.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        answer = text.toString();
        image = render(random);
    }

    public BufferedImage image() { return image; }

    /** 每次提交都消耗本次验证码，验证失败或登录失败后需要使用新图片。 */
    public synchronized void verify(String input) {
        if (consumed) throw new IllegalArgumentException("验证码已使用，请刷新图片");
        consumed = true;
        if (!clock.instant().isBefore(expiresAt)) throw new IllegalArgumentException("验证码已过期，请输入新图片中的字符");
        if (input == null || input.isBlank()) throw new IllegalArgumentException("请输入图片验证码");
        if (!answer.equalsIgnoreCase(input.trim())) throw new IllegalArgumentException("图片验证码不正确，请输入新图片中的字符");
    }

    private BufferedImage render(SecureRandom random) {
        BufferedImage result = new BufferedImage(140, 44, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = result.createGraphics();
        try {
            g.setColor(new Color(239, 245, 234));
            g.fillRect(0, 0, result.getWidth(), result.getHeight());
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (int i = 0; i < 6; i++) {
                g.setColor(new Color(160 + random.nextInt(60), 170 + random.nextInt(50), 160 + random.nextInt(60)));
                g.drawLine(random.nextInt(140), random.nextInt(44), random.nextInt(140), random.nextInt(44));
            }
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
            for (int i = 0; i < answer.length(); i++) {
                Graphics2D letter = (Graphics2D) g.create();
                try {
                    letter.setColor(new Color(30 + random.nextInt(60), 65 + random.nextInt(50), 35 + random.nextInt(60)));
                    letter.rotate((random.nextInt(21) - 10) * Math.PI / 180, 23 + i * 31, 24);
                    letter.drawString(String.valueOf(answer.charAt(i)), 10 + i * 31, 31 + random.nextInt(4));
                } finally { letter.dispose(); }
            }
            for (int i = 0; i < 45; i++) {
                g.setColor(new Color(120 + random.nextInt(100), 140 + random.nextInt(90), 120 + random.nextInt(100)));
                g.fillOval(random.nextInt(140), random.nextInt(44), 2, 2);
            }
        } finally { g.dispose(); }
        return result;
    }
}
