package com.powerdialer.model;

import java.io.Serializable;
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
import org.apache.commons.lang3.SerializationUtils;
import com.powerdialer.model.base.BaseEntity;

@Entity
@Table(name = "wbp_status_group")
public class GroupPropertyStatus extends BaseEntity implements Serializable {

  private static final long serialVersionUID = -7036810712414755847L;
  private Long id;
  private Integer timeout = 0;
  private GroupStatus status;
  private Group group;
  private Integer sort = 0;

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "baseEntity_seq")
  @SequenceGenerator(name = "baseEntity_seq", sequenceName = "WBP_STATUS_GROUP_FCSEQ", allocationSize = 1)
  @Column(name = "id")
  public Long getId() {
    return this.id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  @Column(name = "timeout")
  public Integer getTimeout() {
    return timeout;
  }

  public void setTimeout(Integer timeout) {
    this.timeout = timeout;
  }


  @ManyToOne(cascade = { CascadeType.PERSIST }, fetch = FetchType.EAGER)
  @JoinColumn(name = "status_id")
  public GroupStatus getStatus() {
    return status;
  }

  public void setStatus(GroupStatus status) {
    this.status = status;
  }

  @ManyToOne(cascade = { CascadeType.PERSIST }, fetch = FetchType.LAZY)
  @JoinColumn(name = "group_id")
  public Group getGroup() {
    return group;
  }

  public void setGroup(Group group) {
    this.group = group;
  }

  @Column(name = "sort")
  public Integer getSort() {
    return sort;
  }

  public void setSort(Integer sort) {
    this.sort = sort;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof GroupPropertyStatus)) {
      return false;
    }
    final GroupPropertyStatus obj = (GroupPropertyStatus) o;

    return generateEquals(getId(), obj.getId());
  }

  @Override
  public int hashCode() {
    return generateHash(31, 42, getId());
  }

  @Override
  public GroupPropertyStatus clone() throws CloneNotSupportedException {
    return SerializationUtils.clone(this);
  }

  @Override
  public String toString() {
    return "GroupPropertyStatus [id=" + id + ", status=" + status + "]";
  }

}

