package xyz.ezsky.anilink.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchProgressVO {
    private Long libraryId;
    private long totalFiles;
    private long matched;
    private long noMatch;
    private long pendingMatch;
    private int queuePending;
    private int activeBatches;
    private int batchSize;
    private int queueIntervalSeconds;
    private long totalEnqueued;
    private long totalProcessed;
    private long totalMatched;
    private long totalNoMatch;
    private long failedTasks;
}
