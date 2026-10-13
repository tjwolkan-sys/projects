package com.powerdialer.model;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import com.powerdialer.model.base.BaseEntity;
import com.powerdialer.util.Constants;

@Entity
@Table(name = "pd_predefined_value")
public class PredefinedValue extends BaseEntity implements CampaignHolder {

  private static final long serialVersionUID = -2194383361297934242L;
  private Long id;
  private String value;
  private Campaign campaign;
  private Constants.INPUT_VALUE_TYPE type;

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "baseEntity_seq")
  @SequenceGenerator(name = "baseEntity_seq", sequenceName = "PD_CAMPAIGN_PREDEFINED_VALUE_FCSEQ", allocationSize = 1)
  @Column(name = "id")
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  @Column(name = "value")
  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  @ManyToOne(cascade = { CascadeType.PERSIST }, fetch = FetchType.LAZY)
  @JoinColumn(name = "campaign_id", nullable = true)
  public Campaign getCampaign() {
    return campaign;
  }

  public void setCampaign(Campaign campaign) {
    this.campaign = campaign;
  }

  @Column(name = "type")
  public Constants.INPUT_VALUE_TYPE getType() {
    return type;
  }

  public void setType(Constants.INPUT_VALUE_TYPE type) {
    this.type = type;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((id == null) ? 0 : id.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (!(obj instanceof PredefinedValue))
      return false;
    PredefinedValue other = (PredefinedValue) obj;
    if (id == null) {
      return other.id == null;
    } else return id.equals(other.id);
  }

  @Override
  public String toString() {
    return "PredefinedValue [id=" + id + ", value=" + value + ", type=" + type + "]";
  }

}
