package it.overzoom.ordinainchat.dto;

import java.io.Serial;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "DTO base comune a tutte le entità dell'applicazione. Include id e versione.")
public class BaseDTO implements java.io.Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "ID univoco dell'entità (MongoDB ObjectId o UUID)", example = "64efab20c4d65b2e82b7d09f")
    private String id;

    @Schema(description = "Numero di versione dell'entità, utile per il controllo ottimistico di concorrenza", example = "3")
    private Integer version;

    // Getter e Setter

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}
