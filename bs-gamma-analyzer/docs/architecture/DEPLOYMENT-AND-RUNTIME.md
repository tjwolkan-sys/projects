# Yayınlama ve Runtime

bs-gamma-analyzer üretim arkasında çalışan otomatik bir servis değildir. Lokal veya küçük ekip içinde elle ayağa kaldırılan bir Streamlit aracıdır. Runtime kararları sadelik ve veri tek yönlü okuma üzerine kuruludur.

## Çalıştırma Modeli

- Komut: `streamlit run app.py`.
- Streamlit varsayılan olarak `localhost:8501` üzerinde dinler. Port/ağ değişikliği `streamlit run app.py --server.port <port>` parametreleriyle yapılır.
- Tek süreçte tek kullanıcı senaryosu varsayılır. Aynı süreç birden çok kullanıcıya servis verirken `st.session_state` her kullanıcıya izole olur, ancak DB engine `cache_resource` ile paylaşılır.

## Bağımlılıklar

- Python 3.11+ ve repo dışı pip paketleri (bkz. `TECHNOLOGY-STACK.md`).
- PostgreSQL erişimi (opsiyonel): yalnızca DB modunda; Excel/CSV/JSON modunda gerekli değildir.
- Anthropic Claude API erişimi (opsiyonel): yalnızca AI analizi sekmesinde gerekir.

## Başlangıç Akışı

1. Geliştirici virtualenv aktive eder.
2. `pip install -r requirements.txt` (dosya eklendiğinde) ile bağımlılıklar kurulur.
3. `streamlit run app.py` ile uygulama açılır.
4. Sidebar'dan veri kaynağı seçilir; PostgreSQL seçilirse host/port/db/user/password alınır.
5. Veri yüklendiğinde otomatik olarak formül, validator ve detector çalıştırılır.
6. AI analizi isteniyorsa Anthropic API key sidebar'a girilir.

## Çevrim Dışı Davranış

- DB modu kullanılmadığında uygulama tamamen çevrim dışı çalışabilir.
- AI analizi sekmesi Anthropic API erişimi ister; erişim yoksa kullanıcı bilgilendirilir.
- Streamlit kendi başına çalışan bir web sunucusu olduğu için ek reverse proxy gerekmez.

## Loglama ve Süreç Yönetimi

- Streamlit varsayılan log'unun ötesinde merkezi bir log altyapısı yoktur.
- Hatalar Streamlit hata kutusu ile UI üzerinde gösterilir.
- Süreç izleme/yeniden başlatma için Streamlit'in kendi yeniden yükleme mekanizması yeterlidir.

## Yatay Ölçek

- Yatay ölçek hedeflenmez; tek süreç tek kullanıcı kullanım için tasarlanmıştır.
- Birden çok kullanıcı için ayrı port veya farklı geliştirici makineleri tercih edilir.
- DB tarafında okuma yükü çok kullanıcı için darboğaz olabilir; bu durumda kampanya filtresi veya tarih aralığı kısaltılır.

## Yeniden Başlatma Davranışı

- `st.session_state` Streamlit süreci yeniden başladığında temizlenir; veri yüklemesi ve API key tekrar girilir.
- `st.cache_resource` ile tutulan PostgreSQL engine yeniden başlatmada kaybolur.
- DataFrame önbelleği `st.cache_data` kullanıldığı yerlerde TTL/yeniden başlatma ile temizlenir.

## Change Contract

- Container, Docker veya CI/CD yayınlama eklemek `chore`/`build` etiketli plan ile yürür.
- Ortam değişkenine geçiş yapılırsa `CONFIGURATION-AND-SECRETS.md` aynı iş içinde güncellenir.
- Çoklu kullanıcı/ölçek senaryosu eklenmeden önce session state, cache ve secret yönetimi yeniden tasarlanır.
