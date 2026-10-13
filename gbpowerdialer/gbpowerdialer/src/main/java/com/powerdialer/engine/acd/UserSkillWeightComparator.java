package com.powerdialer.engine.acd;

import com.powerdialer.model.Skill;
import com.powerdialer.model.User;

import java.io.Serializable;
import java.util.Collection;
import java.util.Comparator;

/**
 * Comparator for sorting users by sum of weights in skills
 */
public class UserSkillWeightComparator implements Comparator<User>, Serializable {

  private final Collection<Skill> skills;

  public UserSkillWeightComparator(Collection<Skill> skills) {
    this.skills = skills;
  }

  @Override
  public int compare(User o1, User o2) {
    long o1WeightSum = o1.getSummarySkillWeight(skills);
    long o2WeightSum = o2.getSummarySkillWeight(skills);
    return Long.compare(o2WeightSum, o1WeightSum);
  }

}
