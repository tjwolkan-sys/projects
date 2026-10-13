(function() {
  angular.module('ui.grid').config([ '$provide', function($provide) {
    $provide.decorator('i18nService', [ '$delegate', function($delegate) {
      $delegate.add('ua', {
        aggregate: {
          label: 'елементи'
        },
        groupPanel: {
          description: 'Для групування по стовпцю перетягніть сюди його назву.'
        },
        my: {
          description: 'Привіт'
        },
        search: {
          placeholder: 'Пошук ...',
          showingItems: 'Показати елементи:',
          selectedItems: 'Обрані елементи:',
          totalItems: 'Всього елементів:',
          size: 'Розмір сторінки:',
          first: 'Перша сторінка',
          next: 'Наступна сторінка',
          previous: 'попереднє',
          last: 'Остання сторінка'
        },
        menu: {
          text: 'Вибрати стовпці:'
        },
        sort: {
          ascending: 'По зростанню',
          descending: 'По спаданню',
          remove: 'Прибрати сортування'
        },
        column: {
          hide: 'Сховати стовпець'
        },
        aggregation: {
          count: 'всього рядків:',
          sum: 'разом:',
          avg: 'середнє:',
          min: 'хв:',
          max: 'макс:'
        },
        pinning: {
          pinLeft: 'Закріпити зліва',
          pinRight: 'Закріпити справа',
          unpin: 'Відмінити закріплення'
        },
        gridMenu: {
          columns: 'Стовпці:',
          importerTitle: 'Імпортувати файл',
          exporterAllAsCsv: 'Експортувати все в CSV',
          exporterVisibleAsCsv: 'Експортувати видимі дані в CSV',
          exporterSelectedAsCsv: 'Експортувати вибрані дані в CSV',
          exporterAllAsPdf: 'Експортувати все в PDF',
          exporterVisibleAsPdf: 'Експортувати видимі дані в PDF',
          exporterSelectedAsPdf: 'Експортувати вибрані дані в PDF',
          clearAllFilters: 'Очистити всі фільтри'
        },
        importer: {
          noHeaders: 'Не вдалося отримати імена стовпців, чи є у файла заголовок?',
          noObjects: 'Об\'єкти не могли бути отримані, чи були у файлі дані, крім заголовків?',
          invalidCsv: 'Файл не вдалося обробити, це правильний CSV?',
          invalidJson: 'Файл не вдалося обробити, це дійсний Json?',
          jsonNotArray: 'Імпортований файл json повинен містити масив, переривання.'
        },
        pagination: {
          aria: {
            pageToFirst: 'До першої сторінки',
            pageBack: 'Перед. сторінка ',
            pageSelected: 'Обрана сторінка',
            pageForward: 'Наст. сторінка ',
            pageToLast: 'До останньої сторінки'
          },
          sizes: 'ел. на сторінці',
          totalItems: 'елементів',
          through: 'через',
          of: 'з'
        }
      });
      return $delegate;
    } ]);
  } ]);
})();
