/**
 * Factory for CRUD operations on campaignSchedule
 */
app.factory('campaignLeaveDayFactory', [
    '$http', '$log', 'baseFactory',
    function ($http, $log, baseFactory) {

        return angular.extend(Object.create(baseFactory.prototype), {

            _save: function (leaveDay) {
                // Update existing
                if (leaveDay!= null && leaveDay.id != null) {
                    var promise = $http.put('scheduleBlackoutAutomation/' + leaveDay.id, leaveDay)
                        .then(function (response) { return response; });
                    return promise;
                }
            },

            _create: function (leaveDay) {
                var promise = $http.post('scheduleBlackoutAutomation', leaveDay)
                    .then(function (response) { return response; });
                return promise;
            },

            _getAll: function (page, sort, filters) {
                var promise = $http.get('scheduleBlackoutAutomation/list', {
                    params: { page: page, sort: sort, filters: filters }
                })
                    .then(function (response) {
                        // Controller expects `then(data => data.data)` style payload
                        // i.e., API returns { data: [...] }
                        return response.data;
                    });
                return promise;
            },

            _remove: function (id) {
                var promise = $http.delete('scheduleBlackoutAutomation/' + id)
                    .then(function (response) { return response.data; });
                return promise;
            },

            _createNew: function (campaignLeaveDay) {
                var now = new Date();
                var start = new Date(now); start.setSeconds(0, 0);
                var end   = new Date(now); end.setSeconds(0, 0);

                var target = campaignLeaveDay || {};
                target.startDate = start;
                target.endDate   = end;
                target.description = "";
                return target;
            },

            createDefaultLeaveDay: function () {
                var now = new Date();
                var start = new Date(now); start.setSeconds(0, 0);
                var end   = new Date(now); end.setSeconds(0, 0);

                return {
                    startDate: start,
                    endDate:   end,
                    description: ""
                };
            },

        });

    }
]);
