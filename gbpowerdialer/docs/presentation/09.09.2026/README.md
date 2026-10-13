# FCT Kanal Kapasite Gate — Sunum (09.09.2026)

## İçerik

- `FCT-KANAL-KAPASITE-GATE.pptx` — 25 slayt, 16:9. Problem → çözüm → ana akış → dayanıklılık → izleme/konfigürasyon → test.
- `build_pptx.py` — sunumu üreten script. Sunum elle düzenlenmez; **içerik bu script'te değiştirilip yeniden üretilir**.
- `verify_pptx.py` — üretilen paketi doğrular.

## Neden script ile üretiliyor

Depoya giren `.pptx` bir ikili dosyadır: diff'i okunamaz, review edilemez. Kaynak script yanında durduğu için sunumun içeriği metin olarak review edilebilir ve tekrar üretilebilir hâlde kalır.

`python-pptx` kurulu değil ve depo offline çalıştığı için OOXML paketi yalnız **standart kütüphane** (`zipfile` + string XML) ile üretilir. Ek bağımlılık yoktur, ağ erişimi gerekmez.

## Üretme ve doğrulama

```powershell
cd docs\presentation\09.09.2026
python build_pptx.py FCT-KANAL-KAPASITE-GATE.pptx
python verify_pptx.py FCT-KANAL-KAPASITE-GATE.pptx
```

`verify_pptx.py` şunları kontrol eder:

- her XML parçası well-formed mı,
- `.rels` içindeki tüm hedefler pakette var mı (sarkan ilişki yok),
- `[Content_Types].xml` tüm parçaları kapsıyor mu ve tekrarlayan `Override` var mı,
- `sldIdLst` ile paketteki slayt sayısı tutuyor mu,
- `p:sldId/@id` değerleri **256..2147483647** aralığında ve tekil mi,
- `sldIdLst`'teki her `r:id` gerçekten bir **slide** ilişkisini mi gösteriyor,
- bir slaytta tekrar eden şekil id'si veya tamamen boş slayt var mı,
- hiçbir şekil slayt sınırlarını aşıyor mu.

## İlk üretimde çıkan iki hata (tekrar etmemesi için)

PowerPoint dosyayı `E_FAIL` ile reddediyordu; XML'ler well-formed olduğu için sebep ilk bakışta görünmüyordu. Kök neden `ppt/presentation.xml`'deydi:

1. `p:sldId/@id` değeri **255**'ten başlıyordu. `ST_SlideId` geçerli aralığı 256..2147483647'dir; aralık dışı tek bir değer **dosyanın tamamının** reddedilmesine yol açar.
2. `sldIdLst` içindeki ilk `r:id` **rId1**'i gösteriyordu; oysa `rId1` slide master ilişkisidir (slaytlar rId2'den başlar).

İkisi de "well-formed XML" kontrolünden geçtiği için `verify_pptx.py`'ye özel kontrol olarak eklendi.

## İlgili dokümanlar

- Mimari: `docs/architecture/DIALER-RUNTIME.md` → "FCT Kanal Kapasite Gate'i"
- Test senaryoları: `docs/test-cases/FEAT-GLB90114534-FCT-KANAL-KAPASITE-GATE.md`
- Change-fix planı: `docs/change-fix-plan/08.09.2026/FIX-GLB90114534-FCT-CALLERID-CONTEXT-VE-LIST-RETRY.md`
- Analiz: `docs/analyse/07.08.2026/`
