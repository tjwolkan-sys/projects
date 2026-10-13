# gbcdr-api

`pd_cdr` (SQL Server) tablosundan kayıt okuyan, Go ile yazılmış katmanlı bir REST API. Başlangıç olarak CDR kayıtlarını `id` ve `interaction_id` ile getirir. `asr_registrar_cdr` gibi yeni tabloların aynı desenle eklenebilmesi için tasarlandı.

## Mimari

Katmanlar arası bağımlılık tek yönlüdür (`handler → service → repository`). Her katman bir sonrakine **interface** üzerinden bağlanır, böylece test/mock kolaydır ve SQL Server bağımlılığı dış katmanlara sızmaz.

```
cmd/api/main.go            Composition root: config + DB + bağımlılık enjeksiyonu, graceful shutdown
internal/config            Ortam değişkenlerinden config + DSN üretimi
internal/database          SQL Server bağlantı havuzu (driver register + ping)
internal/httpx             Ortak JSON response / hata zarfı yardımcıları
internal/cdr
  ├── model.go             CDR domain modeli (pd_cdr satır eşleşmesi)
  ├── repository.go        Repository interface + mssql implementasyonu
  ├── service.go           İş kuralları / girdi doğrulama
  └── handler.go           HTTP uçları ve route kaydı
```

## Çalıştırma

```bash
cp .env.example .env      # değerleri doldur (şifreyi koda yazma)
export $(grep -v '^#' .env | xargs)

go mod tidy               # bağımlılıkları çek (go.sum üretir)
go run ./cmd/api
```

## Uçlar

| Method | Path                                   | Açıklama                  |
|--------|----------------------------------------|---------------------------|
| GET    | `/health`                              | Sağlık kontrolü           |
| GET    | `/api/v1/cdrs/{id}`                     | id ile tek kayıt          |
| GET    | `/api/v1/cdrs?interactionId={value}`   | interaction_id ile kayıt  |

Örnek:

```bash
curl localhost:8080/api/v1/cdrs/12345
curl "localhost:8080/api/v1/cdrs?interactionId=abc-123"
```

Bulunamayan kayıt `404`, geçersiz girdi `400`, beklenmeyen hata `500` döner (gövde: `{"error":"...","message":"..."}`).

## Yeni tablo ekleme (örn. asr_registrar_cdr)

`internal/cdr` paketini şablon al: yeni bir paket aç (`internal/asrregistrar`), aynı dört dosyayı (model/repository/service/handler) oluştur ve `main.go` içindeki composition root'a üç satırla bağla. Mevcut katmanlara dokunmaya gerek yok.

## Notlar

- Tüm sorgular parametrelidir (`@p1`) — SQL injection'a kapalı.
- Nullable kolonlar pointer ile modellenir; SQL `NULL` → JSON'da alan yokluğu (`omitempty`).
- Sırlar yalnızca ortam değişkeninden okunur; repoya gerçek şifre koymayın.
