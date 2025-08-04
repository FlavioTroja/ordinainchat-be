package it.overzoom.ordinainchat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "DTO che rappresenta un cliente. Contiene le informazioni principali di un cliente (nome, telefono, indirizzo).")
public class CustomerDTO extends BaseDTO {

    @Schema(description = "Nome del cliente", example = "Mario Rossi")
    private String name;

    @Schema(description = "Numero di telefono del cliente", example = "+393491234567")
    private String phone;

    @Schema(description = "Indirizzo del cliente", example = "Via Roma 12, 70121 Bari")
    private String address;

    // Getter e Setter

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}
