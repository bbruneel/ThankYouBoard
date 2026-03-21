package org.bruneel.thankyouboard.domain;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.hibernate.annotations.UuidGenerator;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "post")
public class Post {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "board_id", nullable = false)
    private UUID boardId;

    @Column(name = "author_name", nullable = false)
    private String authorName;

    @Column(name = "message_text", columnDefinition = "TEXT")
    private String messageText;

    @Column(name = "giphy_url", length = 1000)
    private String giphyUrl;

    @Column(name = "uploaded_image_url", length = 1000)
    private String uploadedImageUrl;

    @Column(name = "created_at", insertable = false, updatable = false)
    private ZonedDateTime createdAt;

    @JsonIgnore
    @Column(name = "capability_token_hash", length = 64)
    private String capabilityTokenHash;

    @JsonIgnore
    @Column(name = "capability_token_expires_at")
    private ZonedDateTime capabilityTokenExpiresAt;

    /**
     * Returned only on anonymous creation so the browser can store it temporarily.
     * Not persisted to the database.
     */
    @Transient
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String editDeleteToken;

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getBoardId() {
        return boardId;
    }

    public void setBoardId(UUID boardId) {
        this.boardId = boardId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getMessageText() {
        return messageText;
    }

    public void setMessageText(String messageText) {
        this.messageText = messageText;
    }

    public String getGiphyUrl() {
        return giphyUrl;
    }

    public void setGiphyUrl(String giphyUrl) {
        this.giphyUrl = giphyUrl;
    }

    public String getUploadedImageUrl() {
        return uploadedImageUrl;
    }

    public void setUploadedImageUrl(String uploadedImageUrl) {
        this.uploadedImageUrl = uploadedImageUrl;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }

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
