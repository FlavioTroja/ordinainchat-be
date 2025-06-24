package it.overzoom.ordinainchat.dto;

public class BaseDTO implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private Integer version;

    // Getters e Setters
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