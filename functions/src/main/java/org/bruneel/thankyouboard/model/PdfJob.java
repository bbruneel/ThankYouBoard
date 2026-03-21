package org.bruneel.thankyouboard.model;

import java.time.ZonedDateTime;
import java.util.UUID;

public class PdfJob {

    public enum Status { PENDING, RUNNING, SUCCEEDED, FAILED }

    private UUID jobId;
    private UUID boardId;
    private String ownerId;
    private Status status;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
    private String downloadKey;
    private String errorCode;
    private String errorMessage;
    private long expiresAt;

    public PdfJob() {}

    public PdfJob(UUID jobId, UUID boardId, String ownerId, Status status,
                  ZonedDateTime createdAt, ZonedDateTime updatedAt) {
        this.jobId = jobId;
        this.boardId = boardId;
        this.ownerId = ownerId;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getJobId() { return jobId; }
    public void setJobId(UUID jobId) { this.jobId = jobId; }
    public UUID getBoardId() { return boardId; }
    public void setBoardId(UUID boardId) { this.boardId = boardId; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }
    public ZonedDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(ZonedDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getDownloadKey() { return downloadKey; }
    public void setDownloadKey(String downloadKey) { this.downloadKey = downloadKey; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }
}
