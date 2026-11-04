package cn.herb.util;

import cn.herb.dao.Database;
import cn.herb.service.Platform;
import org.junit.jupiter.api.Test;
import java.security.SecureRandom;
import java.time.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ImageCaptchaTest {
    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-03T00:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        public Instant instant() { return now; }
    }
    private ImageCaptcha captcha(Clock clock) {
        return new ImageCaptcha(new SecureRandom() {
            @Override public int nextInt(int bound) { return 0; }
        }, clock); // 确定性随机源生成 AAAA，生产环境使用 SecureRandom。
    }
    @Test void acceptsCaseInsensitiveInputOnlyOnce() {
        ImageCaptcha code = captcha(new MutableClock());
        assertDoesNotThrow(() -> code.verify(" aAaA "));
        assertThrows(IllegalArgumentException.class, () -> code.verify("AAAA"));
    }
    @Test void rejectsBlankWrongAndExpiredCodes() {
        MutableClock clock = new MutableClock();
        for (String input : new String[]{null, "", " ", "BBBB"}) {
            ImageCaptcha code = captcha(clock);
            assertThrows(IllegalArgumentException.class, () -> code.verify(input));
            assertThrows(IllegalArgumentException.class, () -> code.verify("AAAA"));
        }
        ImageCaptcha expired = captcha(clock);
        clock.now = clock.now.plus(Duration.ofMinutes(5));
        assertThrows(IllegalArgumentException.class, () -> expired.verify("AAAA"));
        assertDoesNotThrow(() -> captcha(clock).verify("AAAA"));
    }
    @Test void loginChecksCaptchaBeforeRecordingSuccessAndConsumesItOnPasswordFailure() {
        Platform platform = new Platform(new Database("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        platform.initialize();
        MutableClock clock = new MutableClock();
        var admin = platform.login("admin", "123456", captcha(clock), "aaaa");
        assertEquals(1, platform.loginLogs(admin).size());
        assertThrows(IllegalArgumentException.class, () -> platform.login("admin", "123456", captcha(clock), "BBBB"));
        assertThrows(IllegalArgumentException.class, () -> platform.login("admin", "123456", (ImageCaptcha) null, "AAAA"));
        ImageCaptcha used = captcha(clock);
        assertThrows(IllegalArgumentException.class, () -> platform.login("admin", "wrong", used, "AAAA"));
        assertThrows(IllegalArgumentException.class, () -> platform.login("admin", "123456", used, "AAAA"));
        assertThrows(IllegalArgumentException.class, () -> platform.login("admin", "123456", "WRONG", "RIGHT"));
        assertEquals(1, platform.loginLogs(admin).size());
        assertNotNull(platform.login("admin", "123456", captcha(clock), "AAAA"));
        assertEquals(2, platform.loginLogs(admin).size());
    }
}
