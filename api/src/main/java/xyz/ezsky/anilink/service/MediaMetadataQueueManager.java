package xyz.ezsky.anilink.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import xyz.ezsky.anilink.model.entity.MediaFile;

import java.io.Closeable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * 媒体元数据异步提取队列管理器
 * 
 * 负责管理元数据提取的异步队列，实现：
 * - 高效的线程池管理（避免过多线程导致内存溢出）
 * - 队列化处理（FIFO）
 * - 优先级支持（可选，当前采用 FIFO）
 * - 优雅的队列关闭
 * 
 * 设计原则：
 * 1. 使用固定大小线程池（不超过可用处理器数）
 * 2. 阻塞队列，防止内存无限增长
 * 3. 提交任务时若队列满则调用者阻塞等待
 */
@Log4j2
@Service
public class MediaMetadataQueueManager implements Closeable {

    private final int threadPoolSize;
    private final BlockingQueue<Runnable> taskQueue;
    private final ThreadPoolExecutor executor;

    public MediaMetadataQueueManager() {
        // 线程数：CPU 核心数，最多不超过 4 个（避免过多线程）
        this.threadPoolSize = Math.min(Runtime.getRuntime().availableProcessors(), 4);
        
        // 任务队列最多 500 个任务，防止队列无限增长导致内存问题
        this.taskQueue = new LinkedBlockingQueue<>(500);

        // 创建线程池
        this.executor = new ThreadPoolExecutor(
                threadPoolSize,              // 核心线程数
                threadPoolSize,              // 最大线程数
                60,                          // 线程空闲超时时间
                TimeUnit.SECONDS,            // 超时单位
                taskQueue,                   // 任务队列（有界队列）
                new ThreadFactory() {
                    private final java.util.concurrent.atomic.AtomicInteger count = 
                        new java.util.concurrent.atomic.AtomicInteger(0);
                    
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r);
                        t.setName("MediaMetadataExtractor-" + count.incrementAndGet());
                        t.setDaemon(false);
                        return t;
                    }
                },
                new CallerWaitsRejectionPolicy()  // 队列满时，调用者线程阻塞等待
        );

        log.info("MediaMetadataQueueManager initialized with {} threads, queue capacity: 500", threadPoolSize);
    }

    /**
     * 提交元数据提取任务到队列
     * 
     * 如果队列满，调用者线程会阻塞等待。这确保不会有无限制的任务堆积。
     * 
     * @param mediaFile 待提取元数据的 MediaFile 实体
     * @param filePath  文件完整路径
     * @param callback  提取完成后的回调（接收更新后的 MediaFile）
     * @throws RejectedExecutionException 若线程池已关闭
     */
    public void submitMetadataExtraction(MediaFile mediaFile, Path filePath, Consumer<MediaFile> callback) {
        MetadataExtractionTask task = new MetadataExtractionTask(mediaFile, filePath, callback);
        try {
            executor.execute(task);
            log.debug("Submitted metadata extraction task for file: {}", filePath);
        } catch (RejectedExecutionException e) {
            log.error("Failed to submit metadata extraction task (executor shutdown?): {}", filePath, e);
        }
    }

    /**
     * 获取队列中待处理的任务数
     */
    public int getQueueSize() {
        return taskQueue.size();
    }

    /**
     * 获取活跃线程数
     */
    public int getActiveThreadCount() {
        return executor.getActiveCount();
    }

    /**
     * 获取线程池最大线程数
     */
    public int getMaxPoolSize() {
        return executor.getMaximumPoolSize();
    }

    /**
     * 优雅关闭线程池
     * 
     * - 不接受新任务
     * - 等待队列中的任务完成（最多 30 秒）
     */
    @Override
    public void close() {
        try {
            log.info("Shutting down MediaMetadataQueueManager...");
            executor.shutdown();
            
            // 等待已提交的任务完成，最多 30 秒
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("Executor did not terminate in 30 seconds, forcing shutdown...");
                executor.shutdownNow();
            }
            
            log.info("MediaMetadataQueueManager shut down successfully");
        } catch (InterruptedException e) {
            log.error("Interrupted while waiting for executor shutdown", e);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 内部任务类：封装单个文件的元数据提取任务
     */
    static class MetadataExtractionTask implements Runnable {
        private final MediaFile mediaFile;
        private final Path filePath;
        private final Consumer<MediaFile> callback;

        MetadataExtractionTask(MediaFile mediaFile, Path filePath, Consumer<MediaFile> callback) {
            this.mediaFile = mediaFile;
            this.filePath = filePath;
            this.callback = callback;
        }

        @Override
        public void run() {
            // 实际提取逻辑由调用者（MediaMetadataEnricher）负责实现
            // 此处仅定义任务结构
            if (callback != null) {
                callback.accept(mediaFile);
            }
        }
    }

    /**
     * 自定义拒绝策略：队列满时让调用者线程等待
     * 
     * 这种策略可以有效防止任务无限堆积，使得调用者在队列满时阻塞。
     */
    static class CallerWaitsRejectionPolicy implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            try {
                // 直接添加到队列，如果队列满则阻塞等待
                executor.getQueue().put(r);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RejectedExecutionException("Interrupted while waiting to put task in queue", e);
            }
        }
    }
}

