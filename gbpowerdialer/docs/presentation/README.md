# Sunum Standardı

Bu klasör, geliştirmelerin ekip/PO ile paylaşılabilir sunum çıktılarını tutar. Klasör düzeni analyse ve execution-plan ile aynıdır: `docs/presentation/GG.AA.YYYY/`.

## Kurallar

- Sunum içeriği Türkçe tutulur.
- Depoya giren `.pptx` bir ikili dosyadır ve diff'i okunamaz; bu yüzden **her sunumun yanında onu üreten script bulunur**. Sunum elle düzenlenmez, script'te değiştirilip yeniden üretilir.
- Üretim offline çalışır: yalnız Python standart kütüphanesi kullanılır (`zipfile` + string XML). `python-pptx` gibi ek bağımlılık kurulmaz.
- Her sunum klasöründe üretilen paketi doğrulayan `verify_pptx.py` bulunur; sunum güncellendiğinde doğrulama da çalıştırılır.
- Gerçek IP, şifre, token, müşteri bilgisi veya credential sunuma yazılmaz.

## Sunum İndeksi

- `09.09.2026/README.md`: 09.09.2026 tarihli sunum klasörü.
- `09.09.2026/FCT-KANAL-KAPASITE-GATE.pptx`: FCT kanal kapasite gate'i — 25 slayt. Problem (cause 34 / TRC 25 / TRC 79), sorumluluk ayrımı (kapasite tanımı QSS'te, sayım dialer'da), bileşenler, originate akışı, headroom, öteleme, cause 34 davranışı, release noktaları ve TTL, kampanya yaşam döngüsü ve restart, QSS hata ayrımı, `RouteDetailsFilter` süzmesi, monitor ekranı, konfigürasyon tablosu, 10 kritik test senaryosu ve devreye alma sırası.
