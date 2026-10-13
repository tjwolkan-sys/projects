package com.powerdialer.dto;

import com.powerdialer.model.enums.UploadStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadStatusDTO {
    private String uploadId;
    private UploadStatus status;
    private Integer insertedCount;
    private String errorMessage;
}
