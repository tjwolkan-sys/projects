var messageListener = {

  attachMessageListener : function() {
    if (window.addEventListener) {
      addEventListener("message", this.messageHandler, false);
    } else {
      attachEvent("onmessage", this.messageHandler);
    }
  },

  messageHandler : function(event) {
    if (event.data == 'logout') {
      $('#signoutButton').click();
    } else if (event.data == 'ssoLogin')
      $('#ssoButton').click();
  }

};

messageListener.attachMessageListener();
