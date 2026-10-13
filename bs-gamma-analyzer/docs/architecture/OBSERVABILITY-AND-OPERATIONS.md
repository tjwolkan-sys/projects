# Observability ve Operasyon

bs-gamma-analyzer dar bir observability yüzeyine sahiptir. Üretim dialer'ının kendisi değil, dialer'ın olay verisini analiz eden destek aracı olduğu için merkezi log/metric altyapısı yoktur.

## Log

- Streamlit kendi log'unu stdout'a yazar; ek logger yapılandırması bulunmaz.
- Hatalar UI üzerinde Streamlit hata kutusu ile gösterilir.
- Yapılandırılmış (structured) log eklemek isteniyorsa plan dosyasında format ve hedef belirtilir.

## Metrik ve Sağlık Sondası

- Sağlık endpoint'i veya Prometheus exporter'ı yoktur.
- Streamlit süreç sağlığı işletim sistemi seviyesinde izlenir; uygulama içi metric publish edilmez.

## Hata Akışı

- Veri yükleme: Streamlit `st.error` veya bilgi kutusu ile kullanıcıya raporlanır.
- DB bağlantısı: sidebar'da bağlantı testi mesajı ile gösterilir.
- Anomaly detection: hata oluşursa sekme boş kalır; hata mesajı UI'da gösterilir.
- Claude API: `AnalysisReport.error` alanına yazılır; sekmede kullanıcıya gösterilir.

## Operasyonel Sorumluluklar

- Süreç başlatma/yeniden başlatma: geliştirici/operasyon kişisi `streamlit run app.py` komutunu yönetir.
- Cache temizleme: `st.cache_data.clear()` veya süreç restart'ı ile yapılır.
- Veri tutarsızlığı şüphesi: `bsg-runtime-state-audit` skill'i ile session state ve cache izleri çıkarılır.

## Riskli Boşluklar

- Tek sürecin çökmesi durumunda otomatik kurtarma yoktur.
- Geçmişe dönük analiz için merkezi log saklanmaz; her geliştirici makinesi kendi state'ine sahiptir.
- Anthropic API çağrı sayısı izlenmez; aşırı kullanımı önlemek için kullanıcı manuel kontrol eder.

## Change Contract

- Log/metric/healthcheck eklemek isteniyorsa hedef sistem (örn. stdout JSON, Prometheus, OpenTelemetry) plan dosyasında belirtilir.
- Eklenecek observability yeni dış bağımlılık getirmemelidir; mevcut paketlerle çözülemiyorsa `bsg-offline-python-toolchain` skill'i ile pip artifact koordinasyonu yapılır.
- UI hata mesajları kullanıcının teknik bilgisine göre ölçeklenir; debug detayı yalnız geliştirici modunda gösterilir.
