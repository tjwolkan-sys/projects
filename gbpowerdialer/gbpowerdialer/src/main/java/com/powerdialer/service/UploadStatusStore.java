package com.powerdialer.service;

import com.powerdialer.dto.UploadStatusDTO;
import com.powerdialer.model.enums.UploadStatus;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class UploadStatusStore {

    private final ConcurrentMap<String, UploadStatusDTO> store = new ConcurrentHashMap<>();

    public void markQueued(String uploadId) {
        store.put(uploadId, new UploadStatusDTO(uploadId, UploadStatus.QUEUED, null, null));
    }

    public void markRunning(String uploadId) {
        store.computeIfPresent(uploadId, (id, dto) -> {
            dto.setStatus(UploadStatus.RUNNING);
            return dto;
        });
    }

    public void markSuccess(String uploadId, int insertedCount) {
        store.computeIfPresent(uploadId, (id, dto) -> {
            dto.setStatus(UploadStatus.SUCCESS);
            dto.setInsertedCount(insertedCount);
            return dto;
        });
    }

    public void markFailed(String uploadId, String errorMessage) {
        store.computeIfPresent(uploadId, (id, dto) -> {
            dto.setStatus(UploadStatus.FAILED);
            dto.setErrorMessage(errorMessage);
            return dto;
        });
    }

    public UploadStatusDTO get(String uploadId) {
        return store.get(uploadId);
    }
}
