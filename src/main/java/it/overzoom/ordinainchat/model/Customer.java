package it.overzoom.ordinainchat.model;

import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "customers")
public class Customer extends BaseEntity {

    private String name;
    private String phoneNumber;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

}
