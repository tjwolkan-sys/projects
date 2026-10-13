package com.powerdialer.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.powerdialer.serialization.CustomerDeserializer;


@JsonDeserialize(using = CustomerDeserializer.class)
public class Customer {
    private String name;
    private String surname;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSurname() {
        return surname;
    }

    public void setSurname(String surname) {
        this.surname = surname;
    }
}
