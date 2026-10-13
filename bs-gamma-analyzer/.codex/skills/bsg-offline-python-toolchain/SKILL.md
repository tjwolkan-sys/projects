---
name: bsg-offline-python-toolchain
description: bs-gamma-analyzer'da Python/pip/virtualenv/Streamlit/pytest tool path'i kullanmak, offline pip ile çalıştırmak, eksik paketi raporlamak, VPN/Nexus cache koordinasyonu yapmak ve PostgreSQL/Anthropic erişiminin local doğrulamada nasıl ele alınacağını yönetmek için.
---

# bsg-offline-python-toolchain

## Amaç

Python tabanlı doğrulamaları geliştirici makinesinde tutarlı çalıştırmak; pip cache eksikliklerini açık raporlamak; ajan tarafında dependency indirme denemesi yapmamak.

## Kapsam

- `python`, `pip`, `streamlit`, `pytest`, `psycopg2` çalıştırma yolları.
- `.codex/TOOL-PATHS.local.properties` dosyasındaki anahtarlar.
- Offline pip ve VPN/Nexus cache handoff akışı.
- Streamlit ve PostgreSQL doğrulama notları.

## Akış

1. Komut çalıştırmadan önce `.codex/TOOL-PATHS.local.properties` dosyasını kontrol et.
2. `PYTHON_CMD` ve/veya `VENV_DIR` mevcut ise bu binary kullanılır.
3. Pip komutlarını varsayılan olarak `--no-index` veya `--no-deps --no-build-isolation` ile çalıştır.
4. Eksik paket nedeniyle test başlamazsa paket adı/versiyonu raporla; geliştiriciden VPN/Nexus güncellemesi yapmasını iste.
5. Geliştirici cache güncelledikten sonra aynı offline pip komutu tekrar denenir.
6. Komut sonucunu (stdout/stderr) kullanıcıya raporla; gizleme veya özetleme yapma.

## Standart Komutlar

- Python sürümü: `python --version`
- Hedefli pytest: `pytest tests/<domain>/test_<konu>.py`
- Domain paketi: `pytest tests/<domain>`
- Tam test: `pytest`
- Coverage: `pytest --cov=core --cov=db`
- Pip kurulum (offline): `pip install --no-index --find-links <dir> <paket>`
- Pip versiyon kontrolü: `pip show <paket>`
- Streamlit doğrulama (manuel, geliştirici): `streamlit run app.py`

## Hata Sınıflandırması

- **Cache eksik**: `Could not find a version that satisfies the requirement <paket>`. → VPN/Nexus güncellemesi iste.
- **Python sürümü uyumsuz**: ABI mismatch, `psycopg2` build hatası. → `psycopg2-binary` öner; sürüm uyumluluğu raporla.
- **Virtualenv aktif değil**: `ModuleNotFoundError`. → `VENV_DIR` aktivasyonu kontrol et.
- **Test discovery boş**: `pytest`'in kök yolu hatalı. → `pytest tests/` veya `pytest -c <pyproject>` dene.
- **Streamlit bağımlılığı eksik**: `streamlit` yüklenmemiş. → Sürüm uyumlu kurulum öner; ajan kurmaz.

## VPN ve Cache Handoff

- Eksik artifact tespit edildiğinde:
  1. Eksik paket adı, sürüm aralığı ve nereden gerektiği raporlanır.
  2. Geliştiriciden VPN üzerinden internal index'e bağlanıp paketi cache'lemesi istenir.
  3. Ajan VPN sırasında dependency indirme denemez.
  4. Geliştirici VPN'den çıktıktan sonra ajan aynı offline komutu yeniden çalıştırır.

## Streamlit Doğrulama Notları

- Streamlit'i ajan otomatik çalıştırmaz; gerçek tarayıcı doğrulaması geliştirici tarafındadır.
- Streamlit testlerinde `streamlit.testing.v1` kullanılarak headless doğrulama yapılır; UI çekimi ajan tarafından yapılmaz.
- `STREAMLIT_CMD` doluysa CLI doğrulamasında bu binary kullanılır.

## PostgreSQL Doğrulama Notları

- PostgreSQL'e bağlantı ajan tarafından gerçek üretim DB'sine yapılmaz.
- `POSTGRES_HOST/PORT/DB/USER` lokal test ortamı için doldurulur; şifre ajan tarafından girilmez.
- Test yazılırken in-memory SQLite veya mock SQLAlchemy engine kullanılır; gerçek DB'ye gitmek istenirse plan dosyasında gerekçe yazılır.

## Anthropic API Notları

- Anthropic API key ajan tarafından test sırasında çağrılmaz; mock yanıt ile çalışılır.
- Gerçek API doğrulaması istenirse geliştirici Streamlit üzerinden manuel yapar.

## Raporlama Kuralları

- Çalıştırılan komut, çalıştığı dizin ve dönüş kodu raporlanır.
- Eksik paket varsa paket adı/versiyon aralığı net yazılır.
- Çalıştırılamayan komutun nedeni gizlenmez.
