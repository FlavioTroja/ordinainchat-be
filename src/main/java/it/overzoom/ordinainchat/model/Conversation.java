package it.overzoom.ordinainchat.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "conversations", indexes = {
        @Index(name = "ix_conversations_user_chat", columnList = "user_id, telegram_chat_id"),
        @Index(name = "ix_conversations_last_activity", columnList = "last_activity_at")
})
public class Conversation extends BaseEntity {

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "telegram_chat_id", nullable = false)
    private String telegramChatId;

    @Column(name = "title")
    private String title;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private java.util.Map<String, Object> metadata;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt = OffsetDateTime.now();

    @Column(name = "last_activity_at", nullable = false)
    private OffsetDateTime lastActivityAt = OffsetDateTime.now();

    // getters/setters
    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getTelegramChatId() {
        return telegramChatId;
    }

    public void setTelegramChatId(String telegramChatId) {
        this.telegramChatId = telegramChatId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public java.util.Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(java.util.Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getLastActivityAt() {
        return lastActivityAt;
    }

    public void setLastActivityAt(OffsetDateTime lastActivityAt) {
        this.lastActivityAt = lastActivityAt;
    }
}
