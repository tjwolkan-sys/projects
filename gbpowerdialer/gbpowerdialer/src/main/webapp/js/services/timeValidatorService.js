/**
 * Factory to validate, compare and format time
 */
app.factory('timeValidatorService', [ '$log', '$rootScope', 'alertService', 'locMsg', function($log, $rootScope, alertService, locMsg) {

  return {

    validate : function(from, till) {
      fromTime = this.formatTime(from);
      tillTime = this.formatTime(till);
      if (this.getComparableTime(fromTime) > this.getComparableTime(tillTime)) {
        return false;
      } else if (this.getComparableTime(fromTime).getTime() === this.getComparableTime(tillTime).getTime()) {
        return false;
      }
      return true;
    },

    formatTime : function(date) {
      if (date == null) {
        return new Date();
      }
      hours = date instanceof Date ? date.getHours() : new Date(date).getHours();
      minutes = date instanceof Date ? this.formatMinutes(date.getMinutes()) : this.formatMinutes(new Date(date).getMinutes());
      return hours + ":" + minutes;
    },

    formatMinutes : function(minutes) {
      return minutes < 10 ? "0" + minutes : minutes;
    },

    getComparableTime : function(time) {
      time = time instanceof Date ? new Date(time.setSeconds(0)) : time;
      timeValue = time instanceof Date ? this.formatTime(time) : time;
      return new Date(Date.parse('10 Aug 1999 ' + timeValue));
    },

    validateDate : function(startDate, endDate) {
      return startDate < endDate;
    },

    isValidDateFormat : function (data) {
      return moment(data, 'DD/MM/YYYY HH:mm', true).isValid();
    }
  };

} ]);
