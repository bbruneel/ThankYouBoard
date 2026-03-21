package org.bruneel.thankyouboard.model;

import java.time.ZonedDateTime;
import java.util.UUID;

public class Post {
    private UUID id;
    private UUID boardId;
    private String authorName;
    private String messageText;
    private String giphyUrl;
    private String uploadedImageUrl;
    private ZonedDateTime createdAt;

    // Anonymous per-post edit/delete capability.
    // transient: Gson skips transient fields, so these are never included in JSON responses.
    // The repository reads/writes them directly via the DynamoDB SDK (not through Gson).
    private transient String capabilityTokenHash;
    private transient ZonedDateTime capabilityTokenExpiresAt;

    // Returned to the browser only on anonymous post creation.
    // Not persisted in DynamoDB.
    private String editDeleteToken;

    public Post() {}

    public Post(
            UUID id,
            UUID boardId,
            String authorName,
            String messageText,
            String giphyUrl,
            String uploadedImageUrl,
            ZonedDateTime createdAt
    ) {
        this.id = id;
        this.boardId = boardId;
        this.authorName = authorName;
        this.messageText = messageText;
        this.giphyUrl = giphyUrl;
        this.uploadedImageUrl = uploadedImageUrl;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getBoardId() { return boardId; }
    public void setBoardId(UUID boardId) { this.boardId = boardId; }
    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }
    public String getMessageText() { return messageText; }
    public void setMessageText(String messageText) { this.messageText = messageText; }
    public String getGiphyUrl() { return giphyUrl; }
    public void setGiphyUrl(String giphyUrl) { this.giphyUrl = giphyUrl; }
    public String getUploadedImageUrl() { return uploadedImageUrl; }
    public void setUploadedImageUrl(String uploadedImageUrl) { this.uploadedImageUrl = uploadedImageUrl; }
    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }

    public String getCapabilityTokenHash() {
        return capabilityTokenHash;
    }

    public void setCapabilityTokenHash(String capabilityTokenHash) {
        this.capabilityTokenHash = capabilityTokenHash;
    }

    public ZonedDateTime getCapabilityTokenExpiresAt() {
        return capabilityTokenExpiresAt;
    }

    public void setCapabilityTokenExpiresAt(ZonedDateTime capabilityTokenExpiresAt) {
        this.capabilityTokenExpiresAt = capabilityTokenExpiresAt;
    }

    public String getEditDeleteToken() {
        return editDeleteToken;
    }

    public void setEditDeleteToken(String editDeleteToken) {
        this.editDeleteToken = editDeleteToken;
    }
}
