# History İndeksi

Bu klasör, ajanların repo dosyalarında değişiklik yaptığı görevlerin günlük işlem kayıtlarını tutar. Soru-cevap, salt inceleme ve planlama gibi read-only işlerde history kaydı eklenmez; `README` yalnız yeni gün dosyası açıldığında güncellenir.

## Kayıt Standardı

- Günlük history dosyaları repo standardı gereği `GG.AA.YYYY.md` adıyla tutulur.
- Her kayıt sona eklenir; mevcut meşru kayıtlar silinmez veya yeniden numaralandırılmaz.
- Yalnız ajan kaynaklı dosya ekleme, silme veya güncelleme içeren görevler kaydedilir.
- Kayıt başlığı `### YYYY-MM-DD HH:mm:ss Z | KIMLIK | slug` formatındadır.
- `KIMLIK` varsa task id, yoksa developer username'idir.
- Her kayıtta `Kapsam`, `Değişiklikler` ve `Doğrulama` alanları zorunludur.
- Doğrulama çalıştırılmadıysa açıkça `Doğrulama: çalıştırılmadı` yazılır.

## Günlük Dosyalar

Yeni dosyalar gün formatında açılır ve aşağıda indekslenir.
