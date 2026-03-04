package xyz.ezsky.anilink.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 元数据提取队列状态VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueueStatusVO {
    private int pendingTasks;      // 队列中待处理的任务数
    private int activeThreads;     // 正在处理的线程数
    private int maxPoolSize;       // 线程池最大线程数
    private long totalProcessed;   // 已处理的总任务数（可选）
}
