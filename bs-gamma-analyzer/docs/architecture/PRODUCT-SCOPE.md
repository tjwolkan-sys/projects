# Ürün Kapsamı

bs-gamma-analyzer, Bulut Santral Predictive Dialer'ının Gamma çağrı yoğunluğu algoritmasının davranışını analiz eden bir destek aracıdır. Üretimde çalışan dialer'a doğrudan müdahale etmez; dialer tarafından üretilen olay verisini okur, Python tarafındaki referans formülle karşılaştırır, anomali kategorize eder ve operasyon ekibine kök neden ipucu sunar.

## Repo İçi Kapsam

- Predictive dialer event verisinin Excel, CSV, JSON veya PostgreSQL kaynağından yüklenmesi.
- Kolon normalizasyonu ve veri tipini standartlaştırma.
- Gamma formülünün Python tarafında çalıştırılması ve gerçek `xn` ile karşılaştırılması.
- Flag bazlı temel anomali ve kategori tabanlı ileri anomali tespiti.
- Çoklu metrik ilişki analizleri (Xn/ringing yükü, agent kullanımı, CPS limit, Xn stabilite).
- Backtesting (Correction Factor ve Abandon Sensitivity parametre tuning'i).
- Anthropic Claude tabanlı kök neden raporu.
- Streamlit sekmeleri ile interaktif görselleştirme ve raw data export.

## Repo Dışı (External Boundary)

- Asterisk AMI/AGI, Redis ve dialer runtime: bu repo bunlara bağlanmaz, yalnız dialer'ın yazdığı `predictive_gamma_events` tablosunu veya dosya export'larını okur.
- Kampanya yönetimi, agent state, çağrı yönlendirme: dialer tarafında yapılır; bu uygulama yalnız analiz aracıdır.
- Anthropic Claude API: external SaaS'tır. Repo, API key'i runtime'da sidebar üzerinden alır ve hiçbir yere kalıcı kaydetmez.
- Kullanıcı yetkilendirme/SSO: yoktur. Streamlit lokal kullanım için tasarlanmıştır.

## Kullanıcı Profili

- Predictive dialer operasyon ekibi: kampanya stop/start ve abandon davranışlarını gözlemler.
- Geliştirici/SRE: formül parity'si veya counter drift araştırır.
- Veri analizi yapan ürün/destek ekibi: backtesting ile parametre etkisini öğrenir.

## Hedeflenmeyen Kullanım

- Çok kullanıcılı, kimliklendirilmiş, yüksek ölçekli web uygulaması olarak yayınlanmak.
- Dialer DB'sine yazma yapmak veya kampanya parametrelerini değiştirmek.
- Anthropic API key'i kalıcı olarak repo veya UI üzerinde tutmak.
- Gerçek zamanlı dashboard olarak production observability görevini üstlenmek.

## Change Contract

- Repo, dialer tarafındaki contract değişikliklerine pasif olarak uyum sağlar; yazma boundary'sine geçmez.
- Yeni veri kaynağı türü eklemek isteniyorsa `bsg-data-source-loader-contract` skill'i ile contract korunur.
- Anthropic dışında bir LLM eklemek mevcut prompt/JSON şemasını kırmamalıdır; eklenirse `bsg-llm-claude-contract` skill'i kapsamı genişletilir.
