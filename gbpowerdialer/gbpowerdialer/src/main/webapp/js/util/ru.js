/**
 * Created by Oleg on 16/08/14. Updated by Duanevsky 12/10/14.
 */
(function() {
  angular.module('ui.grid').config([ '$provide', function($provide) {
    $provide.decorator('i18nService', [ '$delegate', function($delegate) {
      $delegate.add('ru', {
        aggregate : {
          label : 'элементы'
        },
        groupPanel : {
          description : 'Для группировки по столбцу перетащите сюда его название.'
        },
        my : {
          description : 'Привет'
        },
        search : {
          placeholder : 'Поиск...',
          showingItems : 'Показать элементы:',
          selectedItems : 'Выбранные элементы:',
          totalItems : 'Всего элементов:',
          size : 'Размер страницы:',
          first : 'Первая страница',
          next : 'Следующая страница',
          previous : 'Предыдущая страница',
          last : 'Последняя страница'
        },
        menu : {
          text : 'Выбрать столбцы:'
        },
        sort : {
          ascending : 'По возрастанию',
          descending : 'По убыванию',
          remove : 'Убрать сортировку'
        },
        column : {
          hide : 'Спрятать столбец'
        },
        aggregation : {
          count : 'всего строк: ',
          sum : 'итого: ',
          avg : 'среднее: ',
          min : 'мин: ',
          max : 'макс: '
        },
        pinning : {
          pinLeft : 'Закрепить слева',
          pinRight : 'Закрепить справа',
          unpin : 'Открепить'
        },
        gridMenu : {
          columns : 'Столбцы:',
          importerTitle : 'Импортировать файл',
          exporterAllAsCsv : 'Экспортировать всё в CSV',
          exporterVisibleAsCsv : 'Экспортировать видимые данные в CSV',
          exporterSelectedAsCsv : 'Экспортировать выбранные данные в CSV',
          exporterAllAsPdf : 'Экспортировать всё в PDF',
          exporterVisibleAsPdf : 'Экспортировать видимые данные в PDF',
          exporterSelectedAsPdf : 'Экспортировать выбранные данные в PDF',
          clearAllFilters : 'Очистите все фильтры'
        },
        importer : {
          noHeaders: 'Не удалось получить имена столбцов, есть ли у файла заголовок?',
          noObjects: 'Объекты не могли быть получены, были ли в файле данные, кроме заголовков?',
          invalidCsv: 'Файл не удалось обработать, это правильный CSV?',
          invalidJson: 'Файл не удалось обработать, это действительный Json?',
          jsonNotArray: 'Импортированный файл json должен содержать массив, прерывание.'
        },
        pagination : {
          aria : {
            pageToFirst : 'К первой странице',
            pageBack : 'Пред. страница',
            pageSelected : 'Выбранная страница',
            pageForward : 'След. страница',
            pageToLast : 'К последней странице'
          },
          sizes : 'эл. на странице',
          totalItems : 'элементов',
          through : 'через',
          of : 'из'
        }
      });
      return $delegate;
    } ]);
  } ]);
})();
