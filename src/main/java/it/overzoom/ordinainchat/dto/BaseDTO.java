package it.overzoom.ordinainchat.dto;

import java.io.Serial;

public class BaseDTO implements java.io.Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private Integer version;

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