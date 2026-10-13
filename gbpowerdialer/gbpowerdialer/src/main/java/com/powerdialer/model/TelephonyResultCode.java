package com.powerdialer.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import org.hibernate.validator.constraints.NotBlank;

import com.powerdialer.model.base.BaseObject;

@Entity
@Table(name = "pd_telephony_rc")
public class TelephonyResultCode extends BaseObject {

  private static final long serialVersionUID = 436047530144678770L;

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "baseEntity_seq")
  @SequenceGenerator(name = "baseEntity_seq", sequenceName = "PD_TELEPHONY_RC_FCSEQ", allocationSize = 1)
  @Column(name = "id")
  private Long id;

  @NotBlank(message = "{telephony_result_code.name.blank}")
  @Column(name = "name", nullable = false, columnDefinition = "NVARCHAR(255)")
  private String name;

  @Column(name = "description", columnDefinition = "NVARCHAR(255)")
  private String description;

  @Column(name = "code", nullable = false)
  private int code;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public int getCode() {
    return code;
  }

  public void setCode(int code) {
    this.code = code;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((getId() == null) ? 0 : getId().hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (!(obj instanceof TelephonyResultCode))
      return false;
    TelephonyResultCode other = (TelephonyResultCode) obj;
    if (getId() == null) {
      return other.getId() == null;
    } else return getId().equals(other.getId());
  }

  @Override
  public String toString() {
    return "TelephonyResultCode [id=" + id + ", name=" + name + ", description=" + description + "]";
  }

}
