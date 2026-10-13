---
name: gbpd-elastic-devtools-log-query
description: gbpowerdialer hatalarında geliştiriciden Elastic DevTools logu istenirken geçerli Elasticsearch JSON sorguları hazırlamak için kullan. Campaign, call, AMI, Redis, scheduler, automation, task id veya interaction id bazlı log incelemesinde index seti, zaman aralığı, application_name ve arama ifadelerini güvenli DevTools formatına çevirir; index adının çalışan sete göre düzenlenmesi gerektiğini kullanıcıya mutlaka hatırlatır.
---

# gbpd-elastic-devtools-log-query

## Amaç

Geliştiriciden log istenmesi gereken durumlarda Elastic DevTools'a doğrudan yapıştırılabilecek, JSON parse hatası üretmeyen sorgu hazırla.

## Zorunlu Çıktı Kuralları

- Sorgunun başında `GET /<set-index-prefix>-*/_search` kullan.
- Geliştiriciye şu notu her zaman açık yaz: `Index adını çalışan sete göre değiştirin; örnek: nonturkcell-*, turkcell-* veya ilgili set prefix'i.`
- JSON içinde string değerlerine ham satır sonu koyma. `query_string.query` tek satır olmalı.
- Çok uzun veya okunması zor aramalar için tek dev `query_string` yerine `bool.should` içinde birden fazla kısa `query_string` kullan.
- Zaman aralığında Türkiye için varsayılan `time_zone: "+03:00"` kullan.
- Müşteri verisi, telefon, interaction id veya campaign id kullanıcıdan geldiyse aynen kullan; tahmin etme.
- Kampanya bilinmiyorsa campaign id zorunlu tutma; önce task id, interaction id, action kelimeleri, campaign state logları ve scheduler/automation loglarıyla geniş sorgu ver.
- `size` değerini amaca göre belirle: genel timeline için 10000, dar kontrol için 500-2000.
- Sorguya mümkünse `_source` filtresi ekle: `@timestamp`, `application_name`, `logger_name`, `thread_name`, `level`, `message`.

## Sorgu Hazırlama Akışı

1. Bilinen bilgileri çıkar: set adı/prefix, tarih-saat, task id, campaign id/name, interaction id, application_name, aksiyon, beklenen hatalı davranış.
2. Set prefix bilinmiyorsa placeholder bırak: `<set-index-prefix>`.
3. Önce geniş timeline sorgusu ver.
4. Ardından gerekiyorsa dar sorgular ver:
   - pause/start/stop state transition
   - scheduler/automation kararları
   - pause sonrası dial/publish/originate event'leri
   - belirli interaction id veya campaign id akışı
5. Sorgudan beklenen sonucu kısa yaz: örnek `pause timestamp'inden sonra DialerCallDialEvent var mı?`

## Güvenli DevTools Şablonu

```json
GET /<set-index-prefix>-*/_search
{
  "size": 10000,
  "_source": [
    "@timestamp",
    "application_name",
    "logger_name",
    "thread_name",
    "level",
    "message"
  ],
  "query": {
    "bool": {
      "must": [
        {
          "term": {
            "application_name": "Powerdialer"
          }
        },
        {
          "range": {
            "@timestamp": {
              "gte": "2026-05-14T09:00:00+03:00",
              "lt": "2026-05-14T10:00:00+03:00",
              "time_zone": "+03:00"
            }
          }
        },
        {
          "bool": {
            "should": [
              {
                "query_string": {
                  "query": "\"Campaign [id=34\""
                }
              },
              {
                "query_string": {
                  "query": "\"campaignId=34\""
                }
              },
              {
                "query_string": {
                  "query": "\"DialerCallDialEvent\" AND \"campaignId=34\""
                }
              }
            ],
            "minimum_should_match": 1
          }
        }
      ]
    }
  },
  "sort": [
    {
      "@timestamp": {
        "order": "asc"
      }
    }
  ]
}
```

## Hazır Arama Desenleri

- Campaign lifecycle: `"Campaign [id=ID"`, `"is started"`, `"is paused"`, `"is notified to stop"`, `"Call queue of Campaign [id=ID"`.
- Scheduler/automation: `"ScheduledCampaignRunner"`, `"Pausing campaign"`, `"Starting campaign"`, `"AutomationCheckHandler"`, `"ScheduleAutomationEngine"`, `"Evaluated expression"`.
- Dial sonrası kanıt: `"DialerCallDialEvent" AND "campaignId=ID"`, `"originate" AND "Campaign [id=ID"`.
- Interaction akışı: `"dl-..."`, `"interactionId='dl-...'"`, `"Call [object=" AND "id=dl-..."`.

## Kullanıcıya Verilecek Kısa Not

Sorgudan önce veya sonra şunu ekle:

```text
Index adını çalışan sete göre değiştirin; örnek nonturkcell-* yerine turkcell-* veya ilgili set prefix'i olabilir.
```
