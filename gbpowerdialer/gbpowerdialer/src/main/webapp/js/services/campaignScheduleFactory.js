/**
 * Factory for CRUD operations on campaignSchedule
 */
app.factory('campaignScheduleFactory', [ '$http', '$log', 'baseFactory', function($http, $log, baseFactory) {
	
	return angular.extend(Object.create(baseFactory.prototype),{
		
	    _save : function(schedule) {
	        if (schedule != null && schedule.id != null){
	          var promise = $http.put('schedule/' + schedule.id, schedule).then(function(response) {
	            return response;
	          });
	          return promise;
	        }
	      },
	      
	      _create : function(schedule){
	        var promise = $http.post('schedule', schedule).then(function(response) {
	          return response;
	        });
	        return promise;
	      },
	
	      _getAll : function(page, sort, filters) {
	        var promise = $http.get('schedule/list', {params:{"page": page, "sort" : sort, "filters" : filters}}).then(function(response) {
	          return response.data;
	        });
	        return promise;
	      },
	      
	      _remove : function(id) {
	        var promise = $http.delete('schedule/' + id).then(function(response) {
	          return response.data;
	        });
	        return promise;
	      },
	      
	      _createNew : function(campaignSchedule) {
	    	  campaignSchedule.deleted = false;
	    	  campaignSchedule.from = new Date('10 Aug 1999 09:00:00');
	    	  campaignSchedule.till = new Date('10 Aug 1999 18:00:00');	  
	    	  return campaignSchedule;
	      },
	      
	      createDefaultSchedule : function(day) {
	    	schedule = {};
	    	schedule['day'] = day;
	    	schedule['from'] = new Date('10 Aug 1999 09:00:00');
	    	schedule['till'] = new Date('10 Aug 1999 18:00:00');
	    	schedule['enabled'] = true;
	    	schedule['deleted'] = false;
			schedule['restEnabled'] = false;
	    	return schedule;
	      }
		
	});
	
  }
]);
