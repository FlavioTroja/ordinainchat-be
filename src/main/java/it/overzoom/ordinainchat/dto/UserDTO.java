package it.overzoom.ordinainchat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "DTO che rappresenta un utente dell'applicazione collegato a Telegram.")
public class UserDTO extends BaseDTO {

    @Schema(description = "ID utente Telegram (chat id o user id Telegram associato all’account)", example = "123456789")
    private String telegramUserId;

    public String getTelegramUserId() {
        return telegramUserId;
    }

    public void setTelegramUserId(String telegramUserId) {
        this.telegramUserId = telegramUserId;
    }
}
