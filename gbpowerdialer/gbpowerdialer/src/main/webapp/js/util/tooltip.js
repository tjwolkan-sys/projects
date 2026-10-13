const delay = 500;

function addTooltips() {
  setTimeout(add, delay);
}

function add() {
  $("#available option").addClass("might-overflow");
  $("#selected option").addClass("might-overflow");
  $('.might-overflow').each(function () {
    var $el = $(this);
    if (this.offsetWidth < 0.95 * this.scrollWidth)
      $el.attr('title', $el.text());
  });
}