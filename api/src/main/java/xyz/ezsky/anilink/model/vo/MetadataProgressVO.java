package xyz.ezsky.anilink.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetadataProgressVO {
    private Long libraryId;
    private long totalFiles;
    private long metadataFetched;
    private long pendingMetadata;
    private int queuePending;
    private int activeThreads;
    private int maxPoolSize;
    private long totalSubmitted;
    private long totalProcessed;
    private long failedTasks;
}
