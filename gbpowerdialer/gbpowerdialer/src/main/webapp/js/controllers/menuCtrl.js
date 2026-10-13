/**
 * Controller for menu
 */
app.controller('menuCtrl', [ '$scope', '$http', '$location', '$rootScope', 'localeFactory', '$log', '$translate', '$window', '$timeout', '$interval', '$modal', '$filter', 'uiGridExporterService',
    'uiGridExporterConstants', 'locMsg', 'csrfService',
    function($scope, $http, $location, $rootScope, localeFactory, $log, $translate, $window, $timeout, $interval, $modal, $filter, uiGridExporterService, uiGridExporterConstants, locMsg, csrfService) {

      var vm = this;

      vm.open = function(size, template, ctrl) {
        var modalInstance = $modal.open({
          animation : true,
          backdrop : 'static',
          templateUrl : template,
          controller : ctrl,
          size : size
        });
        modalInstance.result.then(function() {
          liveUpdates(true);
        }, function() {
          $log.info('Modal dismissed at: ' + new Date());
          liveUpdates(true);
        });

        liveUpdates(false);

      };
      
      $scope.isActive = function(viewLocation) {
        return viewLocation === $location.path();
      };

      $rootScope.hasAnyRole = function(rolesArray) {
        if ($rootScope.currentUser != null) {
          for (var i = 0; i < rolesArray.length; i++) {
            if ($rootScope.hasAuthority({authority : rolesArray[i]})) {
              return true;
            }
          }
        }
        return false;
      };

      $rootScope.hasAuthority = function(authority) {
        if ($rootScope.currentUser != null) {
          for (var i = 0; i < $rootScope.currentUser.authorities.length; i++) {
            var a = $rootScope.currentUser.authorities[i];
            if (a.authority == authority.authority) {
              if (authority.value) {
                if (a.value == authority.value)
                  return true;
              } else {
                return true;
              }
            }
          }
        }
        return false;
      };

      $rootScope.exporterPdfCustomFormatter = function(docDefinition) {
        docDefinition.content[0].table.widths = _.map(docDefinition.content[0].table.widths, function() {
          return 'auto';
        });
        var columnsCount = docDefinition.content[0].table.widths.length;
        console.log(columnsCount);

        if(columnsCount > 30) {
          docDefinition.pageSize = 'A0';
        } else if(columnsCount > 24){
          docDefinition.pageSize = 'A1';
        } else if(columnsCount > 18){
          docDefinition.pageSize = 'A2';
        } else if(columnsCount > 18){
          docDefinition.pageSize = 'A3';
        } else {
          docDefinition.pageSize = 'A4';
        }
        return docDefinition;
      };

      $rootScope.getExportFilename = function(name, type) {
        return {
          toString : function() {
            return name + ' ' + $filter('date')(new Date(), 'dd-MM-yyyy HH-mm') + '.' + type;
          }
        };
      };

      $rootScope.getExportMenuItemsXLSX = function(gridApi, fileHeader) {
        var grid = gridApi.grid;

        function exportToXLSX(grid, fileName, rowTypes, colTypes) {

          var exportService = uiGridExporterService;

          exportService.loadAllDataIfNeeded(grid, rowTypes, colTypes).then(function() {

            function sheet_from_array_of_arrays(data, opts) {
              var ws = {};
              var range = {
                s : {
                  c : 10000000,
                  r : 10000000
                },
                e : {
                  c : 0,
                  r : 0
                }
              };
              for (var R = 0; R != data.length; ++R) {
                for (var C = 0; C != data[R].length; ++C) {
                  if (range.s.r > R)
                    range.s.r = R;
                  if (range.s.c > C)
                    range.s.c = C;
                  if (range.e.r < R)
                    range.e.r = R;
                  if (range.e.c < C)
                    range.e.c = C;
                  var cell = {
                    v : data[R][C]
                  };
                  if (cell.v == null)
                    continue;
                  var cell_ref = XLSX.utils.encode_cell({
                    c : C,
                    r : R
                  });

                  if (typeof cell.v === 'number')
                    cell.t = 'n';
                  else if (typeof cell.v === 'boolean')
                    cell.t = 'b';
                  else if (cell.v instanceof Date) {
                    cell.t = 'n';
                    cell.z = XLSX.SSF._table[14];
                    cell.v = datenum(cell.v);
                  } else
                    cell.t = 's';

                  ws[cell_ref] = cell;
                }
              }
              if (range.s.c < 10000000)
                ws['!ref'] = XLSX.utils.encode_range(range);
              return ws;
            }

            function s2ab(s) {
              var buf = new ArrayBuffer(s.length);
              var view = new Uint8Array(buf);
              for (var i = 0; i != s.length; ++i)
                view[i] = s.charCodeAt(i) & 0xFF;
              return buf;
            }

            function workbook_blob_from_array_of_arrays(sheetName, sheetData) {
              var wb = {
                SheetNames : [],
                Sheets : []
              };
              var ws = sheet_from_array_of_arrays(sheetData);

              /* add worksheet to workbook */
              wb.SheetNames.push(sheetName);
              wb.Sheets[sheetName] = ws;

              var wopts = {
                bookType : 'xlsx',
                bookSST : false,
                type : 'binary'
              };
              var wbout = XLSX.write(wb, wopts);

              return new Blob([ s2ab(wbout) ], {
                type : ""
              });
            }

            // function body
            var exportColumnHeaders = exportService.getColumnHeaders(grid, colTypes);
            var exportData = exportService.getData(grid, rowTypes, colTypes);

            var gridArray = [];

            var columnHeadersArray = [];
            exportColumnHeaders.forEach(function(item) {
              columnHeadersArray.push(item.displayName);
            });
            gridArray.push(columnHeadersArray);

            exportData.forEach(function(row) {
              var columnRowArray = [];
              row.forEach(function(item) {
                columnRowArray.push(item.value);
              });
              gridArray.push(columnRowArray);
            });

            var wb_blob = workbook_blob_from_array_of_arrays("Sheet1", gridArray);

            /* the saveAs call downloads a file on the local machine */
            saveAs(wb_blob, fileName.toString());
          });
        }
        return [ {
          title : locMsg['export.xlsx.all'],
          action : function($event) {
            exportToXLSX(grid, $rootScope.getExportFilename(fileHeader, 'xlsx'), uiGridExporterConstants.ALL, uiGridExporterConstants.ALL);
          },
          order : 210
        }, {
          title : locMsg['export.xlsx.visible'],
          action : function($event) {
            exportToXLSX(grid, $rootScope.getExportFilename(fileHeader, 'xlsx'), uiGridExporterConstants.VISIBLE, uiGridExporterConstants.VISIBLE);
          },
          order : 215
        }, {
          title : locMsg['export.xlsx.selected'],
          action : function($event) {
            exportToXLSX(grid, $rootScope.getExportFilename(fileHeader, 'xlsx'), uiGridExporterConstants.SELECTED, uiGridExporterConstants.VISIBLE);
          },
          shown : function() {
            return (gridApi.selection && gridApi.selection.getSelectedRows().length > 0);
          },
          order : 220
        } ];
      };

      $scope.logoutHandler = function() {

        $http.post('auth/logout', {}).then(function(response) {
          $rootScope.authenticated = false;
          $rootScope.currentUser = undefined;
          csrfService.getTokenData();
          $location.path('/login');
        });

      };

      $scope.setLocale = function(locale) {
        localeFactory.setLocale(locale);
      };


      $rootScope.updateAuth = function() {
        $http.post('auth/check', {}).then(function(response) {
          if (response.data.authenticated) {
            $rootScope.currentUser = response.data;
          } else {
            $location.path('/login');
          }
          $rootScope.authenticated = response.data.authenticated;
        }, function(response) {
          $log.error(response);
        });
      };
      $rootScope.updateAuth();

      $scope.changeLanguage = function(locale) {
        $http.post('locale', locale).then(function(response) {
          $window.location.reload();
          return response;
        });
      };
      
      $rootScope.authCheck = function(){
        $http.post('auth/check', {}).then(function(response) {
          if (response.data.authenticated) {
            $("#loginModal").modal("hide").on('hidden.bs.modal', function() {
              $location.path('/#/campaignList');
              $scope.$apply();
            });
            $rootScope.currentUser = response.data;
          } else {
            $location.path('/login');
          }
          $rootScope.authenticated = response.data.authenticated;
        }, function(response) {
          $log.error(response);
       });
      };
      
      $rootScope.authCheck();
    } ]);
