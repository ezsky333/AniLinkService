package xyz.ezsky.anilink.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import xyz.ezsky.anilink.repository.MediaFileRepository;
import xyz.ezsky.anilink.model.entity.MediaFile;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.io.Closeable;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Set;

/**
 * 媒体元数据后台处理管理器（DB 驱动）。
 *
 * 设计目标：新增文件只负责落库，不直接堆积内存任务；
 * 由后台按批次从数据库拉取 metadataFetched=false 的文件并并发处理。
 */
@Log4j2
@Service
public class MediaMetadataQueueManager implements Closeable {

    @Autowired
    private MediaFileRepository mediaFileRepository;

    @Autowired
    private MediaMetadataEnricher mediaMetadataEnricher;

    private final int threadPoolSize;
    private final BlockingQueue<Runnable> taskQueue;
    private final ThreadPoolExecutor executor;
    private final ScheduledExecutorService dispatcher = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean immediateDispatchScheduled = new AtomicBoolean(false);
    private final Set<Long> inFlightIds = ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.atomic.AtomicLong totalSubmitted = new java.util.concurrent.atomic.AtomicLong(0);
    private final java.util.concurrent.atomic.AtomicLong totalProcessed = new java.util.concurrent.atomic.AtomicLong(0);
    private final java.util.concurrent.atomic.AtomicLong totalFailed = new java.util.concurrent.atomic.AtomicLong(0);
    private final int queueCapacity;

    @Value("${anilink.metadata.dispatch-interval-seconds:5}")
    private int dispatchIntervalSeconds;

    @Value("${anilink.metadata.dispatch-batch-size:20}")
    private int dispatchBatchSize;

    public MediaMetadataQueueManager(
            @Value("${anilink.metadata.thread-pool-size:0}") int configuredThreadPoolSize,
            @Value("${anilink.metadata.max-thread-pool-size:4}") int maxThreadPoolSize,
            @Value("${anilink.metadata.queue-capacity:500}") int queueCapacity) {
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        int safeDefault = Math.min(Math.max(availableProcessors, 2), 4);
        int boundedMax = Math.max(1, maxThreadPoolSize);
        int desired = configuredThreadPoolSize > 0 ? configuredThreadPoolSize : safeDefault;

        // 可通过配置覆盖，默认至少 2 线程，避免容器核数过低导致长期单线程堆积。
        this.threadPoolSize = Math.max(1, Math.min(desired, boundedMax));

        this.queueCapacity = Math.max(50, queueCapacity);

        // 执行队列有界，保护内存。
        this.taskQueue = new LinkedBlockingQueue<>(this.queueCapacity);

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
                        t.setName("dataExtractor-" + count.incrementAndGet());
                        t.setDaemon(false);
                        return t;
                    }
                },
                new ThreadPoolExecutor.AbortPolicy()  // 调度线程感知饱和后稍后重试
        );

        log.info("MediaMetadataQueueManager initialized with {} threads, queue capacity: {}, cpu cores: {}",
                threadPoolSize, this.queueCapacity, availableProcessors);
    }

    @PostConstruct
    public void init() {
        dispatcher.scheduleAtFixedRate(
                this::dispatchFromDatabase,
                dispatchIntervalSeconds,
                dispatchIntervalSeconds,
                TimeUnit.SECONDS
        );
        log.info("Metadata dispatcher started with interval: {}s, batchSize: {}", dispatchIntervalSeconds, dispatchBatchSize);
    }

    @PreDestroy
    public void destroy() {
        close();
    }

    /**
     * 仅触发一次尽快调度，不在调用线程里做重活。
     */
    public void triggerProcessing() {
        scheduleImmediateDispatch();
    }

    private void scheduleImmediateDispatch() {
        if (!immediateDispatchScheduled.compareAndSet(false, true)) {
            return;
        }

        dispatcher.execute(() -> {
            try {
                dispatchFromDatabase();
            } finally {
                immediateDispatchScheduled.set(false);
            }
        });
    }

    private void dispatchFromDatabase() {
        if (executor.isShutdown()) {
            return;
        }

        int availableSlots = threadPoolSize + queueCapacity - executor.getActiveCount() - taskQueue.size();
        if (availableSlots <= 0) {
            return;
        }

        int fetchSize = Math.max(1, Math.min(dispatchBatchSize, availableSlots));
        int querySize = Math.max(fetchSize * 3, fetchSize);
        var page = mediaFileRepository.findByMetadataFetchedFalse(
            PageRequest.of(0, querySize, Sort.by(Sort.Direction.ASC, "id"))
        );

        if (page.isEmpty()) {
            return;
        }

        int submitted = 0;
        for (MediaFile mediaFile : page.getContent()) {
            if (submitted >= fetchSize) {
                break;
            }

            Long mediaFileId = mediaFile.getId();
            if (mediaFileId == null || !inFlightIds.add(mediaFileId)) {
                continue;
            }

            try {
                executor.execute(new MetadataExtractionTask(mediaFile));
                totalSubmitted.incrementAndGet();
                submitted++;
            } catch (RejectedExecutionException ignored) {
                // 执行队列已满，等待下一轮调度。
                inFlightIds.remove(mediaFileId);
                break;
            }
        }

        // 如果本轮拿满，通常说明仍有积压，继续快速调度一次。
        if (submitted >= fetchSize) {
            scheduleImmediateDispatch();
        }
    }

    /**
     * 获取队列中待处理的任务数
     */
    public int getQueueSize() {
        return (int) mediaFileRepository.countByMetadataFetchedFalse();
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
        return threadPoolSize;
    }

    public long getTotalSubmitted() {
        return totalSubmitted.get();
    }

    public long getTotalProcessed() {
        return totalProcessed.get();
    }

    public long getTotalFailed() {
        return totalFailed.get();
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
            dispatcher.shutdownNow();
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
    class MetadataExtractionTask implements Runnable {
        private final MediaFile mediaFile;

        MetadataExtractionTask(MediaFile mediaFile) {
            this.mediaFile = mediaFile;
        }

        @Override
        public void run() {
            Long mediaFileId = mediaFile.getId();
            try {
                mediaMetadataEnricher.enrichMediaFileSync(mediaFile);
                if (mediaFileId != null) {
                    mediaFileRepository.findById(mediaFileId).ifPresent(latest -> {
                        mergeMetadataFields(mediaFile, latest);
                        mediaFileRepository.save(latest);
                    });
                }
                totalProcessed.incrementAndGet();
            } catch (Exception e) {
                totalFailed.incrementAndGet();
                log.error("Metadata extraction task failed for file: {}", mediaFile.getFilePath(), e);
            } finally {
                if (mediaFileId != null) {
                    inFlightIds.remove(mediaFileId);
                }
            }
        }
    }

    private void mergeMetadataFields(MediaFile source, MediaFile target) {
        target.setDuration(source.getDuration());
        target.setWidth(source.getWidth());
        target.setHeight(source.getHeight());
        target.setAspectRatio(source.getAspectRatio());
        target.setColorDepth(source.getColorDepth());
        target.setHdrType(source.getHdrType());
        target.setColorSpace(source.getColorSpace());
        target.setColorPrimaries(source.getColorPrimaries());
        target.setVideoBitrate(source.getVideoBitrate());
        target.setFps(source.getFps());
        target.setContainerFormat(source.getContainerFormat());
        target.setVideoCodec(source.getVideoCodec());
        target.setAudioCodec(source.getAudioCodec());
        target.setMetadataFetched(source.getMetadataFetched());
    }
}

