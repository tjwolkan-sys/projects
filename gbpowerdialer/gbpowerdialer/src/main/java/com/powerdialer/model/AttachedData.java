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

import org.hibernate.annotations.DynamicUpdate;

import com.powerdialer.model.base.BaseEntity;

@Entity
@Table(name = "pd_attached_data")
@DynamicUpdate
public class AttachedData extends BaseEntity implements CampaignHolder {

  private static final long serialVersionUID = 4230418079830140666L;
  private Long id;
  private String field;
  private String fieldName;
  private Boolean popup;
  private String popupName;
  private Campaign campaign;
  private Boolean saveLongTerm;

  public AttachedData() {
    super();
  }

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "baseEntity_seq")
  @SequenceGenerator(name = "baseEntity_seq", sequenceName = "PD_ATTACHED_DATA_FCSEQ", allocationSize = 1)
  @Column(name = "id")
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  @Column(name = "field", nullable = false, columnDefinition = "NVARCHAR(255)")
  public String getField() {
    return field;
  }

  public void setField(String field) {
    this.field = field;
  }

  @Column(name = "field_name", nullable = false, columnDefinition = "NVARCHAR(255)")
  public String getFieldName() {
    return fieldName;
  }

  public void setFieldName(String fieldName) {
    this.fieldName = fieldName;
  }

  @Column(name = "popup")
  public Boolean getPopup() {
    return popup;
  }

  public void setPopup(Boolean popup) {
    this.popup = popup;
  }

  @Column(name = "popup_name", columnDefinition = "NVARCHAR(255)")
  public String getPopupName() {
    return popupName;
  }

  public void setPopupName(String popupName) {
    this.popupName = popupName;
  }

  @Column(name = "save_long_term")
  public Boolean getSaveLongTerm() {
    return saveLongTerm;
  }

  public void setSaveLongTerm(Boolean saveLongTerm) {
    this.saveLongTerm = saveLongTerm;
  }

  @ManyToOne(cascade = { CascadeType.PERSIST }, fetch = FetchType.LAZY)
  @JoinColumn(name = "campaign_id", nullable = false)
  public Campaign getCampaign() {
    return campaign;
  }

  @Override
  public void setCampaign(Campaign campaign) {
    this.campaign = campaign;
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
    if (getClass() != obj.getClass())
      return false;
    AttachedData other = (AttachedData) obj;
    if (id == null) {
      return other.id == null;
    } else
      return id.equals(other.id);
  }

  @Override
  public String toString() {
    return "AttachedData [id=" + id + ", field=" + field + ", fieldName=" + fieldName + ", campaign=" + campaign + "]";
  }

}
