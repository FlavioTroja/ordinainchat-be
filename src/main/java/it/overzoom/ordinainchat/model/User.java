package it.overzoom.ordinainchat.model;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import it.overzoom.ordinainchat.type.StepType;

@Document(collection = "users")
public class User extends BaseEntity {

    @Indexed(unique = true)
    private String telegramUserId;
    private StepType currentStep;

    // Getters and Setters
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
}
