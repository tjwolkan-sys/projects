package com.powerdialer.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.powerdialer.serialization.RelationDeserializer;

@JsonDeserialize(using = RelationDeserializer.class)
public class Relation  {
    private Integer type;
    private String description;


    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
