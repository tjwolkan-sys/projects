---
name: bsg-no-workaround-root-cause-engineering
description: bs-gamma-analyzer'da bug fix, formül drift, anomaly false-positive, parse hatası, NaN/Inf veya veri tutarsızlığı durumunda case-specific patch yerine owner boundary'de kalıcı çözüm uygulamak için. Tek bir if/else patch yerine veri sahibinin contract'ına dokunan, eşit sınıftaki tüm hataları kapsayan kök neden çözümü gerektiğinde kullan.
---

# bsg-no-workaround-root-cause-engineering

## Amaç

Hata semptomunu lokal bir patch ile maskelemek yerine kök nedeni veri sahibinde çözmek. Aynı hata sınıfının tüm temsilcilerini ortadan kaldırmak.

## Ne Zaman Kullanılır

- Belirli bir kampanya/tarih aralığı için drift veya yanlış mode raporlandığında.
- Anomaly detector tek bir kategori için sürekli false-positive üretirken.
- Claude yanıtı tek bir alan için sürekli boş gelirken.
- DB sorgusu belirli bir filtre kombinasyonunda hata verirken.
- Veri kaynağı (Excel/CSV/JSON) belirli bir layout'ta düzgün parse edilmezken.

## Yasak Pattern'ler

- Tek bir kampanya için `if campaign_id == 'X'` patch.
- NaN/Inf'i sessizce 0 ile değiştiren `try/except: pass` blokları.
- `match_status` üzerinde `is_match = True` zorlaması.
- `AnalysisReport.error` olduğunda kullanıcıya sahte success raporu döndürmek.
- SQL filtresinde tek bir tarih için manuel patch.
- UI sekmesinde "bu sekme şu durumda gizlensin" görsel maskelemesi.

## Root Cause Akışı

1. Semptomu tek cümlede yaz.
2. Veri sahibini bul: Java tarafı mı, Python pipeline mı, Claude prompt mu, SQL şeması mı, veri yükleyici mi?
3. Yazıcı ve okuyucu tüm noktaları çıkar.
4. Aynı hata sınıfının başka temsilcilerini grep ile bul.
5. Düzeltmeyi owner boundary'de yap: contract dokümanını da aynı iş içinde güncelle.
6. Regression testi ekle; test fixture'ı kök neden senaryosunu kapsasın.
7. Aynı hata sınıfını kapsayan diğer noktalar varsa plan dosyasına yaz ve tek tek çöz.

## Repo Özel Kurallar

- Java parity'li kodda (`core/formula.py`) Python tarafında "geçici düzeltme" yapma; Java tarafı önce kontrol edilir.
- Anomaly detector'da false-positive'i severity 1'e indirgeyerek "saklamak" çözüm değildir; eşik veya kategori contract'ı yeniden değerlendirilir.
- Claude prompt'unda lokal bir alan eklemek yerine system prompt'ta veri kontratı netleştirilir.
- SQL filtresinde lokal patch yerine kolon kontratı veya nullable davranışı netleştirilir.

## Fail-Closed Yorumu

- Kök neden bulunamamışsa veriyi/contract'ı sessizce default'a indirgeme; kullanıcıya açıkça hata göster veya işlemden çık.
- Sessiz fallback yapılacaksa neden ve sonuç plan dosyasında ve UI'da kullanıcıya açıklanır.

## Done Kriteri

- Aynı hata sınıfının başka tüm temsilcileri envanter olarak çıkarılmış.
- Düzeltme owner boundary'de yapılmış.
- Regression test eklenmiş ve yeşil.
- Doküman güncellenmiş.
- Plan dosyasında kök neden tek cümlede özetlenmiş ve referans noktası verilmiş.

## Yararlı Arama Kalıpları

- `try:\n.*\n.*except.*pass` (sessiz fallback).
- `if.*campaign_id.*==` (lokal patch).
- `is_match.*=\s*True` (status zorlaması).
- `match_status.*=` (validator override).
- `replace.*np.nan` (NaN sessiz değiştirme).
