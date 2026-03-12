package xyz.ezsky.anilink.repository;

import xyz.ezsky.anilink.model.entity.ResourceDownloadTask;
import xyz.ezsky.anilink.repository.base.BaseRepository;

import java.util.List;

public interface ResourceDownloadTaskRepository extends BaseRepository<ResourceDownloadTask, Long> {
    List<ResourceDownloadTask> findTop100ByOrderByCreatedAtDesc();

    List<ResourceDownloadTask> findTop500ByStatusInOrderByCreatedAtDesc(List<ResourceDownloadTask.DownloadStatus> statuses);

    List<ResourceDownloadTask> findByStatusInOrderByCreatedAtAsc(List<ResourceDownloadTask.DownloadStatus> statuses);

    boolean existsByMagnet(String magnet);
}
