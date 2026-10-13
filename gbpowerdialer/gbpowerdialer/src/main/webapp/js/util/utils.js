function focusAtErrorTab(tabs) {
  var tabHasError = false;
  var errorTabIndex = 0;
  _.find(tabs, function(item, index, list) {
    tabHasError = _.size(angular.element("#" + item.id + " .has-error")) > 0;
    errorTabIndex = index;
    return tabHasError;
  });

  if (tabHasError) {
    _.each(tabs, function(item) {
      item.active = false;
    });
    tabs[errorTabIndex].active = true;
    return errorTabIndex;
  }
  return -1;
}

function focusAtErrorAccordionGroup(accordions) {
  var errorAccordionGroupIndex = 0;
  _.find(accordions, function(item, index, list) {
    errorAccordionGroupIndex = index;
    return _.size(angular.element("#" + item.id + " .has-error")) > 0
  });

  _.each(accordions, function(item) {
    item.active = false;
  });
  accordions[errorAccordionGroupIndex].active = true;
}

function saveEnabled(selectedRows, factory, gridData, listName, enabled, onSuccess) {
  var ids = [];
  _.each(selectedRows, function(item) {
    if (item.enabled !== enabled) {
      ids.push(item.id);
    }
  });
  if (_.size(ids) == 0) {
    return;
  }

  factory.setEnabled(ids, enabled, listName).then(function(data) {
    if (onSuccess)
      onSuccess();
    _.each(data, function(item) {
      var obj = _.findWhere(gridData, {
        id : item
      });
      if (obj) {
        obj.enabled = enabled
      }
    });

  }, function(response) {
    console.error(response);
  });
}

function setEnabledInGrid(selectedRows, enabled) {
  _.each(selectedRows, function(item) {
    if (item.enabled !== enabled) {
      item.enabled = enabled;
      item.changed = true;
    }
  });
}

function addCustomFilter(filters, term, field, condition) {
  if (!filters || !field || !condition) {
    return false;
  }
  if (term == '') {
    if (!filters[field]) {
      return false;
    }
    for (var i = filters[field].length - 1; i >= 0; i--) {
      if (filters[field][i]['condition'] == condition) {
        filters[field].splice(i, 1);
      }
    }
    return true;
  }
  if (filters[field]) {
    var alreadyAdded = false;
    $.each(filters[field], function(index, value) {
      if (value["condition"] == condition) {
        value["term"] = term;
        alreadyAdded = true;
      }
    });
    if (!alreadyAdded) {
      filters[field].push({
        "condition": condition,
        "term": term
      });
    }
  } else {
    filters[field] = [{
      "condition": condition,
      "term": term
    }];
  }
  return true;
}