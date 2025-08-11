package it.overzoom.ordinainchat.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "DTO base comune a tutte le entità (id, versione).")
public class BaseDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "ID univoco (UUID)", example = "3a3a1f0e-2f0a-4a62-9d33-1c1b9d3a7f7b")
    private UUID id;

    @Schema(description = "Versione per optimistic locking", example = "3")
    private Long version;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
