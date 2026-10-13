package com.powerdialer.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.powerdialer.model.base.BaseEntity;
import com.powerdialer.serialization.AnamolyReportResultDeserializer;

import java.util.Objects;

@JsonDeserialize(using = AnamolyReportResultDeserializer.class)
public class AnamolyReportResult extends BaseEntity {

    private static final long serialVersionUID = 8331439691233920071L;
    private Long id;
    private Customer customer;
    private Relation relation;
    private String parentId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public Relation getRelation() {
        return relation;
    }

    public void setRelation(Relation relation) {
        this.relation = relation;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        return result;
    }

    @Override
    public String toString() {
        return "AnamolyReportResult{" +
                "id=" + id +
                ", customer=" + Objects.toString(customer) +
                ", relation=" + Objects.toString(relation) +
                '}';
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        AnamolyReportResult other = (AnamolyReportResult) obj;
        if (id == null) {
            return other.id == null;
        } else return id.equals(other.id);
    }
}
