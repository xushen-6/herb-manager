package cn.herb.util;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;

public final class Passwords {
    private Passwords() {}
    /** 按课程要求使用 MD5；真实生产系统应改用带盐的慢哈希。 */
    public static String md5(String raw) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(raw.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
