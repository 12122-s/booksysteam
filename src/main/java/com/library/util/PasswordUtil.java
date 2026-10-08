package com.library.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.bouncycastle.crypto.generators.SCrypt;

/**
 * 密码哈希工具，支持 scrypt 与 pbkdf2:sha256 两种哈希格式的生成与校验。
 *
 * <p>校验时要能识别数据库里已存的三种写法：
 * "scrypt:32768:8:1$盐$哈希"、"pbkdf2:sha256:600000$盐$哈希"，
 * 以及更早的 "sha1$盐$哈希"；无法识别的格式一律返回 false。
 *
 * <p>新生成的哈希统一用 pbkdf2:sha256（JDK 原生支持），
 * 格式：{@code pbkdf2:sha256:600000$盐$哈希hex}。
 */
public final class PasswordUtil {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] SALT_CHARS =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();

    private PasswordUtil() {
    }

    /** 生成哈希（pbkdf2:sha256 格式）。 */
    public static String hash(String password) {
        String salt = randomSalt(16);
        byte[] digest;
        try {
            digest = pbkdf2("sha256", salt, password, 600_000, 32);
        } catch (Exception e) {
            // JDK 不可能不支持 PBKDF2WithHmacSHA256；真发生就快速失败
            throw new IllegalStateException("密码哈希计算失败", e);
        }
        return "pbkdf2:sha256:600000$" + salt + "$" + HexFormat.of().formatHex(digest);
    }

    /** 校验密码是否与存储的哈希匹配。无法识别的格式一律返回 false。 */
    public static boolean check(String storedHash, String password) {
        if (storedHash == null || storedHash.isEmpty() || password == null) {
            return false;
        }
        String[] parts = storedHash.split("\\$", 3);
        if (parts.length != 3) {
            return false;
        }
        String method = parts[0];
        String salt = parts[1];
        String expected = parts[2];

        try {
            if (method.startsWith("scrypt:")) {
                // scrypt:32768:8:1
                String[] ps = method.split(":");
                if (ps.length != 4) {
                    return false;
                }
                int n = Integer.parseInt(ps[1]);
                int r = Integer.parseInt(ps[2]);
                int p = Integer.parseInt(ps[3]);
                byte[] got = SCrypt.generate(password.getBytes(StandardCharsets.UTF_8),
                        salt.getBytes(StandardCharsets.UTF_8), n, r, p, 64);
                return constantTimeEquals(HexFormat.of().parseHex(expected), got);
            }
            if (method.startsWith("pbkdf2:")) {
                // pbkdf2:sha256:600000 或老格式 pbkdf2:sha256（默认迭代次数）
                String[] ps = method.split(":");
                String digestName = ps[1];
                int iterations = ps.length >= 3 ? Integer.parseInt(ps[2]) : 260_000;
                int dklen = digestLength(digestName);
                byte[] got = pbkdf2(digestName, salt, password, iterations, dklen);
                return constantTimeEquals(HexFormat.of().parseHex(expected), got);
            }
            if (method.equals("sha1")) {
                // 最老的格式：sha1(盐 + 密码)
                MessageDigest md = MessageDigest.getInstance("SHA-1");
                md.update(salt.getBytes(StandardCharsets.UTF_8));
                byte[] got = md.digest(password.getBytes(StandardCharsets.UTF_8));
                return constantTimeEquals(HexFormat.of().parseHex(expected), got);
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    private static byte[] pbkdf2(String digestName, String salt, String password,
                                 int iterations, int dklen) throws Exception {
        String algorithm = "PBKDF2WithHmac" + digestName.toUpperCase();
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(),
                salt.getBytes(StandardCharsets.UTF_8), iterations, dklen * 8);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(algorithm);
        return factory.generateSecret(spec).getEncoded();
    }

    private static int digestLength(String name) {
        switch (name.toLowerCase()) {
            case "sha1": return 20;
            case "sha256": return 32;
            case "sha512": return 64;
            default: return 32;
        }
    }

    private static String randomSalt(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(SALT_CHARS[RANDOM.nextInt(SALT_CHARS.length)]);
        }
        return sb.toString();
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }
}
