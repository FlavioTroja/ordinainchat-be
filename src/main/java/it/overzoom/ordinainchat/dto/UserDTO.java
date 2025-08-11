package it.overzoom.ordinainchat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Utente Telegram.")
public class UserDTO extends BaseDTO {

    @Schema(description = "ID utente Telegram (chat/user id)", example = "123456789")
    private String telegramUserId;

    public String getTelegramUserId() {
        return telegramUserId;
    }

    public void setTelegramUserId(String telegramUserId) {
        this.telegramUserId = telegramUserId;
    }
}
