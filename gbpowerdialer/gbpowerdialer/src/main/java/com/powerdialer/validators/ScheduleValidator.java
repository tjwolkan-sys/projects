package com.powerdialer.validators;

import java.util.Calendar;
import java.util.Collection;

import org.apache.commons.lang3.time.DateUtils;

import com.powerdialer.exceptions.DateMismatchException;
import com.powerdialer.exceptions.DuplicatedNameException;
import com.powerdialer.model.Schedule;

public class ScheduleValidator implements Validator<Schedule> {

  @Override
  public ValidationResult validate(Collection<Schedule> schedules) {

    if (schedules == null) {
      return new ValidationResult(true, new String[0]);
    }

    for (Schedule schedule : schedules) {

      if (DateUtils.truncatedCompareTo(schedule.getFrom(), schedule.getTill(), Calendar.MINUTE) > 0) {
        throw new DateMismatchException("schedule.date.from_time.bigger");
      } else if (DateUtils.truncatedCompareTo(schedule.getFrom(), schedule.getTill(), Calendar.MINUTE) == 0) {
        throw new DateMismatchException("schedule.date.equals");
      }
    }

    return new ValidationResult(true, new String[0]);
  }

  @Override
  public ValidationResult validate(Schedule t) throws DuplicatedNameException {
    return new ValidationResult(true, new String[0]);
  }

}
