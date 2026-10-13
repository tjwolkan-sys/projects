package com.powerdialer.model;

import com.powerdialer.model.base.BaseEntity;
import com.powerdialer.util.Constants;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.*;

@Entity
@Table(name = "pd_campaign_priority_filter")
@DynamicUpdate
public class CampaignPriorityFilter extends BaseEntity {

  private static final long serialVersionUID = -6566246317275293133L;
  private Long id;
  private Constants.FILTER_TYPE type;
  private String value;
  private CampaignPriority campaignPriority;
  private Constants.FILTER_CONDITION condition;
  private String field;
  private String db_field_type;
  private Boolean technical;
  private Constants.FILTER_TARGET_TABLE targetTable;
  private Constants.FILTER_JOIN_TYPE logicOperator;

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "baseEntity_seq")
  @SequenceGenerator(name = "baseEntity_seq", sequenceName = "PD_CAMPAIGN_PRIORITY_FILTER_FCSEQ", allocationSize = 1)
  @Column(name = "id")
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  @Enumerated(EnumType.ORDINAL)
  @Column(name = "type")
  public Constants.FILTER_TYPE getType() {
    return type;
  }

  public void setType(Constants.FILTER_TYPE type) {
    this.type = type;
  }

  @Column(name = "value", columnDefinition = "NVARCHAR(MAX)")
  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  @ManyToOne(cascade = { CascadeType.PERSIST }, fetch = FetchType.LAZY)
  @JoinColumn(name = "campaign_priority_id", nullable = false)
  public CampaignPriority getCampaignPriority() {
    return campaignPriority;
  }

  public void setCampaignPriority(CampaignPriority campaignPriorityId) {
    this.campaignPriority = campaignPriorityId;
  }

  @Enumerated(EnumType.ORDINAL)
  @Column(name = "condition")
  public Constants.FILTER_CONDITION getCondition() {
    return condition;
  }

  public void setCondition(Constants.FILTER_CONDITION condition) {
    this.condition = condition;
  }

  @Column(name = "field", columnDefinition = "NVARCHAR(MAX)")
  public String getField() {
    return field;
  }

  public void setField(String field) {
    this.field = field;
  }

  @Column(name = "db_field_type", columnDefinition = "NVARCHAR(MAX)")
  public String getDb_field_type() {
    return db_field_type;
  }

  public void setDb_field_type(String db_field_type) {
    this.db_field_type = db_field_type;
  }

  public Boolean getTechnical() {
    return technical;
  }

  public void setTechnical(Boolean technical) {
    this.technical = technical;
  }

  @Enumerated(EnumType.ORDINAL)
  @Column(name = "target_table")
  public Constants.FILTER_TARGET_TABLE getTargetTable() {
    return targetTable;
  }

  public void setTargetTable(Constants.FILTER_TARGET_TABLE targetTable) {
    this.targetTable = targetTable;
  }

  public Constants.FILTER_JOIN_TYPE getLogicOperator() {
    return logicOperator;
  }

  public void setLogicOperator(Constants.FILTER_JOIN_TYPE logicOperator) {
    this.logicOperator = logicOperator;
  }

  @Override
  public String toString() {
    return "CampaignPriorityFilter{" +
            "id=" + id +
            ", type=" + type +
            ", value='" + value + '\'' +
            ", condition=" + condition +
            ", field='" + field + '\'' +
            ", db_field_type='" + db_field_type + '\'' +
            ", technical=" + technical +
            ", targetTable=" + targetTable +
            '}';
  }

  @Override
  public boolean equals(Object o) {
    return false;
  }

  @Override
  public int hashCode() {
    return 0;
  }
}
