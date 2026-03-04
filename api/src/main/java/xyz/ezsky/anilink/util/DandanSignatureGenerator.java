package xyz.ezsky.anilink.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Dandan 签名生成器
 * 算法：base64(sha256(AppId + Timestamp + Path + AppSecret))
 */
public class DandanSignatureGenerator {

    public static String generateSignature(String appId, long timestamp, String path, String appSecret) {
        if (appId == null || appSecret == null || path == null) return null;
        String data = appId + timestamp + path + appSecret;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            // 通常不会发生
            throw new RuntimeException("SHA-256 not supported", e);
        }
    }
}
