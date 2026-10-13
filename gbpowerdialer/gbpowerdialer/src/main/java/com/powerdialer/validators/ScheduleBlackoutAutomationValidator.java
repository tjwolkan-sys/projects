package com.powerdialer.validators;

import com.powerdialer.exceptions.DateMismatchException;
import com.powerdialer.exceptions.DuplicatedNameException;
import com.powerdialer.model.Schedule;
import com.powerdialer.model.ScheduleBlackoutAutomation;
import org.apache.commons.lang3.time.DateUtils;

import java.util.Calendar;
import java.util.Collection;

public class ScheduleBlackoutAutomationValidator implements Validator<ScheduleBlackoutAutomation> {

  @Override
  public ValidationResult validate(Collection<ScheduleBlackoutAutomation> schedules) {

    if (schedules == null) {
      return new ValidationResult(true, new String[0]);
    }

    for (ScheduleBlackoutAutomation schedule : schedules) {

      if (DateUtils.truncatedCompareTo(schedule.getStartDate(), schedule.getEndDate(), Calendar.MINUTE) > 0) {
        throw new DateMismatchException("schedule.date.date.bigger");
      } else if (DateUtils.truncatedCompareTo(schedule.getStartDate(), schedule.getEndDate(), Calendar.MINUTE) == 0) {
        throw new DateMismatchException("schedule.date.equals");
      }
    }

    return new ValidationResult(true, new String[0]);
  }

  @Override
  public ValidationResult validate(ScheduleBlackoutAutomation t) throws DuplicatedNameException {
    return new ValidationResult(true, new String[0]);
  }

}
