package com.powerdialer.model;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import org.hibernate.annotations.DynamicUpdate;

import com.powerdialer.model.base.BaseEntity;
import com.powerdialer.util.Constants.FILTER_CONDITION;
import com.powerdialer.util.Constants.FILTER_TARGET_TABLE;
import com.powerdialer.util.Constants.FILTER_TYPE;

@Entity
@Table(name = "pd_filter")
@DynamicUpdate
public class Filter extends BaseEntity implements CampaignHolder {

  private static final long serialVersionUID = 5987298667844636322L;
  private Long id;
  private Field field;
  private FILTER_TYPE type;
  private FILTER_TARGET_TABLE targetTable;
  private FILTER_CONDITION condition;
  private String value;
  private Boolean technical;
  private boolean segmentation;
  private Integer ratio;
  private Campaign campaign;

  public Filter(String fieldName, FILTER_CONDITION condition, String value) {
    this.field = new Field();
    this.field.setFieldName(fieldName);
    this.type = FILTER_TYPE.PREDEFINED;
    this.condition = condition;
    this.value = value;
  }

  public Filter() {}

  @ManyToOne(cascade = { CascadeType.PERSIST }, fetch = FetchType.LAZY)
  @JoinColumn(name = "campaign_id", nullable = false)
  public Campaign getCampaign() {
    return campaign;
  }

  @Override
  public void setCampaign(Campaign campaign) {
    this.campaign = campaign;
  }

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "baseEntity_seq")
  @SequenceGenerator(name = "baseEntity_seq", sequenceName = "PD_FILTER_FCSEQ", allocationSize = 1)
  @Column(name = "id")
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  @Embedded
  public Field getField() {
    return field;
  }

  public void setField(Field field) {
    this.field = field;
  }

  @NotNull(message = "{filter.type.empty}")
  @Enumerated(EnumType.ORDINAL)
  @Column(name = "type")
  public FILTER_TYPE getType() {
    return type;
  }

  public void setType(FILTER_TYPE type) {
    this.type = type;
  }

  @Enumerated(EnumType.ORDINAL)
  @Column(name = "target_table")
  public FILTER_TARGET_TABLE getTargetTable() {
    return targetTable;
  }

  public void setTargetTable(FILTER_TARGET_TABLE targetTable) {
    this.targetTable = targetTable;
  }

  @Enumerated(EnumType.ORDINAL)
  @Column(name = "condition")
  public FILTER_CONDITION getCondition() {
    return condition;
  }

  public void setCondition(FILTER_CONDITION condition) {
    this.condition = condition;
  }

  @NotNull(message = "{filter.value.empty}")
  @Column(name = "value", columnDefinition = "NVARCHAR(MAX)")
  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((targetTable == null) ? 0 : targetTable.hashCode());
    result = prime * result + ((condition == null) ? 0 : condition.hashCode());
    result = prime * result + ((field == null) ? 0 : field.hashCode());
    result = prime * result + ((id == null) ? 0 : id.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    Filter other = (Filter) obj;
    if (targetTable != other.targetTable) {
      return false;
    }
    if (condition != other.condition) {
      return false;
    }
    if (field == null) {
      if (other.field != null) {
        return false;
      }
    } else if (!field.equals(other.field)) {
      return false;
    }
    if (id == null) {
      return other.id == null;
    } else return id.equals(other.id);
  }

  public Boolean getTechnical() {
    return technical;
  }

  public void setTechnical(Boolean technical) {
    this.technical = technical;
  }

  @Column(name = "segmentation", nullable = false, columnDefinition = "BIT DEFAULT 0")
  public boolean getSegmentation() {
    return segmentation;
  }

  public void setSegmentation(boolean segmentation) {
    this.segmentation = segmentation;
  }

  @Column(name = "ratio")
  public Integer getRatio() {
    return ratio;
  }

  public void setRatio(Integer ratio) {
    this.ratio = ratio;
  }

  @Override
  public String toString() {
    return "Filter [id=" + id + ", field=" + field + ", type=" + type + ", target=" + targetTable + ", condition=" + condition + ", value=" + value + ", campaign=" + campaign + "]";
  }

}
