(function() {
  angular.module('ui.grid').config([ '$provide', function($provide) {
    $provide.decorator('i18nService', [ '$delegate', function($delegate) {
      $delegate.add('tr', {
        headerCell : {
          aria : {
            defaultFilterLabel : 'Kolon filtresi',
            removeFilter : 'Filtreyi kaldır',
            columnMenuButtonLabel : 'Kolon Menüsü'
          },
          priority : 'Öncelik:',
          filterLabel : "Kolon filtresi: "
        },
        aggregate : {
          label : 'girdi'
        },
        groupPanel : {
          description : 'Kolona göre gruplamak için kolon başlığını buraya sürükleyip bırakın.'
        },
        search : {
          placeholder : 'Arama...',
          showingItems : 'Gösterilen Girdiler:',
          selectedItems : 'Seçili Girdiler:',
          totalItems : 'Toplam Girdi:',
          size : 'Sayfa Sayısı:',
          first : 'İlk Sayfa',
          next : 'Gelecek Sayfa',
          previous : 'Önceki Sayfa',
          last : 'Son Sayfa'
        },
        menu : {
          text : 'Kolon Seçiniz:'
        },
        sort : {
          ascending : 'Artan Sıralama',
          descending : 'Azalan Sıralama',
          none : 'Sıralama Yok',
          remove : 'Sıralamayı Kaldır'
        },
        column : {
          hide : 'Kolonu Sakla'
        },
        aggregation : {
          count : 'toplan dizi: ',
          sum : 'toplam: ',
          avg : 'avg: ',
          min : 'min: ',
          max : 'max: '
        },
        pinning : {
          pinLeft : 'Sola Bağla',
          pinRight : 'Sağa Bağla',
          unpin : 'Bağı Kopar'
        },
        columnMenu : {
          close : 'Kapat'
        },
        gridMenu : {
          aria : {
            buttonLabel : 'Grid Menüsü'
          },
          columns : 'Kolonlar:',
          importerTitle : 'Dosya al',
          exporterAllAsCsv : 'Datayı csv olarak çıkar',
          exporterVisibleAsCsv : 'Görünür datayı cvs olarak çıkar',
          exporterSelectedAsCsv : 'Seçili datayı cvs olarak çıkar',
          exporterAllAsPdf : 'Datayı pdf olarak çıkar',
          exporterVisibleAsPdf : 'Görünür datayı psf olarak çıkar',
          exporterSelectedAsPdf : 'Seçili datayı pdf olarak çıkar',
          clearAllFilters : 'Bütün filtreleri temizle'
        },
        importer : {
          noHeaders : 'Kolon isimleri çıkartılamıyor, dosyanin başlığı var mı?',
          noObjects : 'Objeler çıkartılamıyor, dosya içerisinde başlık haricinde data varmıydı?',
          invalidCsv : 'Dosya işlenemiyor, CSV geçerli mi?',
          invalidJson : 'Dosya işlenemiyor, Json geçerli mi?',
          jsonNotArray : 'Alınan json dosyası array içermeli, iptal ediliyor.'
        },
        pagination : {
          aria : {
            pageToFirst : 'İlk sayfaya',
            pageBack : 'Önceki sayfa',
            pageSelected : 'Seçili sayfa',
            pageForward : 'Sonraki sayfa',
            pageToLast : 'Son sayfaya'
          },
          sizes : 'sayfa başı girdi',
          totalItems : 'öğe',
          through : 'yoluyla',
          of : '\/'
        },
        grouping : {
          group : 'Grup',
          ungroup : 'Grup yok',
          aggregate_count : 'Agg: Sayı',
          aggregate_sum : 'Agg: Toplan',
          aggregate_max : 'Agg: Max',
          aggregate_min : 'Agg: Min',
          aggregate_avg : 'Agg: Avg',
          aggregate_remove : 'Agg: Kaldır'
        }
      });
      return $delegate;
    } ]);
  } ]);
})();
