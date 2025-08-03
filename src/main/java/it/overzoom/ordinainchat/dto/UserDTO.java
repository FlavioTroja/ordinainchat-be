package it.overzoom.ordinainchat.dto;

public class UserDTO extends BaseDTO {

    private String telegramUserId;

    public String getTelegramUserId() {
        return telegramUserId;
    }

    public void setTelegramUserId(String telegramUserId) {
        this.telegramUserId = telegramUserId;
    }

}
