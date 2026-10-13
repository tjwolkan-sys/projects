package com.powerdialer.engine.acd;

import com.powerdialer.model.User;

import java.util.Comparator;
import java.util.Date;

/**
 * Compares two agents by how long they have been in their current status.
 *
 * <p>Sorting contract (ascending): the user whose {@code statusSetDate} is older
 * (i.e. has been in the current status longer) comes first. When ACD pools
 * eligible agents (already filtered to {@code AVAILABLE} status), this orders
 * them by real avail wait time — the agent waiting longest is at the head of
 * the list.
 *
 * <p>Rationale: {@link UserLastCallDateComparator} sorts by the last call end
 * timestamp, which does not account for wrapup/break/other non-available time
 * spent after a call ends. An agent who finished a call, took a long break,
 * and just returned to {@code AVAILABLE} would falsely appear "older" and be
 * picked first. {@code statusSetDate} reflects the moment the agent entered
 * the current status, so for {@code AVAILABLE} agents it is the true start of
 * the avail wait.
 *
 * <p>Stateless and side-effect free — no {@code ACDEngine} reference needed.
 * Null {@code statusSetDate} is treated as "highest priority" (newly created
 * user with no recorded status timestamp comes first), with a deterministic
 * {@code id} tie-break to keep ordering stable.
 */
public class UserAvailTimeComparator implements Comparator<User> {

  @Override
  public int compare(User o1, User o2) {
    Date statusSetDate1 = o1 == null ? null : o1.getStatusSetDate();
    Date statusSetDate2 = o2 == null ? null : o2.getStatusSetDate();
    if (statusSetDate1 == null && statusSetDate2 == null) {
      Long id1 = o1 == null ? null : o1.getId();
      Long id2 = o2 == null ? null : o2.getId();
      if (id1 == null && id2 == null) return 0;
      if (id1 == null) return -1;
      if (id2 == null) return 1;
      return id1.compareTo(id2);
    } else if (statusSetDate1 == null) {
      return -1;
    } else if (statusSetDate2 == null) {
      return 1;
    } else {
      return statusSetDate1.compareTo(statusSetDate2);
    }
  }

}
