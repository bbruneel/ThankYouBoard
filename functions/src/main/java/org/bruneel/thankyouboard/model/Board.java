package org.bruneel.thankyouboard.model;

import java.time.ZonedDateTime;
import java.util.UUID;

public class Board {
    private UUID id;
    private transient String ownerId;
    private String title;
    private String recipientName;
    private ZonedDateTime createdAt;

    public Board() {}

    public Board(UUID id, String ownerId, String title, String recipientName, ZonedDateTime createdAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.title = title;
        this.recipientName = recipientName;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }
}
