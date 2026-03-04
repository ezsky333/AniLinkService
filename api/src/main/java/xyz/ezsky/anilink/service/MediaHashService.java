package xyz.ezsky.anilink.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;

/**
 * 媒体文件哈希计算服务
 * 
 * 使用 MD5 算法计算文件前 16MB 的哈希值，用于文件去重和快速识别。
 * 
 * 性能优化：
 * - 仅读取文件前 16MB，避免大文件完全加载到内存
 * - 使用缓冲流逐块读取，内存占用恒定
 * - 计算过程中流式处理，不保存中间数据
 */
@Log4j2
@Service
public class MediaHashService {

    // 前 16MB 用于哈希计算
    private static final long HASH_SIZE = 16 * 1024 * 1024;
    private static final int BUFFER_SIZE = 8192;

    /**
     * 计算文件前 16MB 的 MD5 哈希值
     * 
     * @param filePath 视频文件的路径
     * @return 32 字符的十六进制 MD5 哈希值；失败时返回 null
     */
    public String calculateHash(Path filePath) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");

            try (FileInputStream fis = new FileInputStream(filePath.toFile())) {
                byte[] buffer = new byte[BUFFER_SIZE];
                long bytesRead = 0;
                int len;

                while ((len = fis.read(buffer)) != -1 && bytesRead < HASH_SIZE) {
                    // 确保不超过 16MB
                    int bytesToProcess = (int) Math.min(len, HASH_SIZE - bytesRead);
                    md.update(buffer, 0, bytesToProcess);
                    bytesRead += bytesToProcess;
                }

                byte[] digest = md.digest();
                return bytesToHex(digest);
            }
        } catch (IOException e) {
            log.error("Error reading file for hash calculation: {}", filePath, e);
            return null;
        } catch (Exception e) {
            log.error("Error calculating hash for file: {}", filePath, e);
            return null;
        }
    }

    /**
     * 将字节数组转换为十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
