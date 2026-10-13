/**
 * FCT Kanal Monitörü veri kaynağı. Registry snapshot'ını çeker.
 *
 * skipErrorRedirect: polling sırasında geçici bir hata /error route'una yönlendirmesin
 * (httpRequestInterceptor bu bayrağı dikkate alır).
 */
app.factory('fctChannelFactory', [ '$http', function($http) {

  return {

    getSnapshot : function() {
      return $http.get('fctChannel/snapshot', {
        skipErrorRedirect : true
      });
    }

  };

} ]);
