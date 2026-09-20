package com.deliveryweb.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * 随机盐 + SHA-256 密码摘要工具。
 * 存储格式：盐:摘要，摘要 = SHA-256(盐 + 明文密码) 的小写十六进制。
 * 算法必须与 sql/init.sql 中种子账号的哈希保持一致。
 */
public final class PasswordUtil {

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private PasswordUtil() {
    }

    /**
     * 生成密码摘要：随机 16 位十六进制盐 + “盐:摘要”。
     */
    public static String encode(String rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("明文密码不能为空");
        }
        // P3-7 修复（2026-09-13）：8 字节经 toHex 后恰为 16 个十六进制字符，
        // 即注释所述“随机 16 位十六进制盐”；原未使用的 SALT_LENGTH 常量已删除，避免误读为应改 new byte[16]
        byte[] saltBytes = new byte[8];
        new SecureRandom().nextBytes(saltBytes);
        String salt = toHex(saltBytes);
        return salt + ":" + sha256Hex(salt + rawPassword);
    }

    /**
     * 校验明文密码与存储摘要是否匹配。
     */
    public static boolean verify(String rawPassword, String stored) {
        if (rawPassword == null || stored == null) {
            return false;
        }
        int separator = stored.indexOf(':');
        if (separator <= 0 || separator >= stored.length() - 1) {
            return false;
        }
        String salt = stored.substring(0, separator);
        String expected = stored.substring(separator + 1);
        String actual = sha256Hex(salt + rawPassword);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return toHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            // JDK 8 必然提供 SHA-256，此处仅做防御
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(HEX_CHARS[(b >>> 4) & 0x0F]);
            sb.append(HEX_CHARS[b & 0x0F]);
        }
        return sb.toString();
    }
}
