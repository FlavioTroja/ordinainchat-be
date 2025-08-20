package it.overzoom.ordinainchat.model;

import it.overzoom.ordinainchat.type.StepType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(name = "uk_users_telegram_user_id", columnNames = "telegram_user_id"), indexes = {
        @Index(name = "idx_users_telegram_user_id", columnList = "telegram_user_id")
})
public class User extends BaseEntity {

    @Column(name = "telegram_user_id", nullable = false, length = 64)
    private String telegramUserId;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_step", length = 50)
    private StepType currentStep = StepType.START;

    // getters/setters
    public String getTelegramUserId() {
        return telegramUserId;
    }

    public void setTelegramUserId(String telegramUserId) {
        this.telegramUserId = telegramUserId;
    }

    public StepType getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(StepType currentStep) {
        this.currentStep = currentStep;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
}
