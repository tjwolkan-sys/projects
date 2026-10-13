# VS Code Geliştirici Kurulumu

VS Code, bs-gamma-analyzer için tercih edilen IDE'dir. Ancak ekip standardı IDE'ye bağımlı değildir; aynı işler PyCharm veya başka editörle de yürütülebilir.

## Eklentiler

- **Python**: Microsoft (microsoft.python).
- **Pylance**: Microsoft (microsoft.vscode-pylance).
- **Streamlit Snippets** (opsiyonel): hızlı UI iskeleti için.

## Workspace Ayarları

`.vscode/settings.json` dosyasının önerilen içeriği:

```json
{
  "python.defaultInterpreterPath": ".venv/Scripts/python.exe",
  "python.testing.pytestEnabled": true,
  "python.testing.unittestEnabled": false,
  "python.testing.pytestArgs": ["tests"],
  "python.linting.enabled": false,
  "python.formatting.provider": "none",
  "files.encoding": "utf8",
  "files.eol": "\n"
}
```

Linux/macOS için interpreter yolu `.venv/bin/python` olur. Gerçek değerler local makineye göre değişir; commit yalnız ekip standardı varsayılanları içerir.

## Streamlit Çalıştırma Görevleri

`.vscode/tasks.json` üzerine eklenebilecek örnek görev:

```json
{
  "label": "streamlit: run app",
  "type": "shell",
  "command": "${workspaceFolder}/.venv/Scripts/streamlit.exe",
  "args": ["run", "app.py"],
  "problemMatcher": []
}
```

## Debug Konfigürasyonu

`.vscode/launch.json` üzerine eklenebilecek örnek konfigürasyon:

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "name": "Streamlit: app.py",
      "type": "debugpy",
      "request": "launch",
      "module": "streamlit",
      "args": ["run", "app.py"],
      "console": "integratedTerminal",
      "justMyCode": true
    },
    {
      "name": "Pytest: tests",
      "type": "debugpy",
      "request": "launch",
      "module": "pytest",
      "args": ["tests"],
      "console": "integratedTerminal",
      "justMyCode": true
    }
  ]
}
```

## Test Discovery

VS Code Python eklentisi `tests/` klasörünü otomatik tarar. Test dosya adı `test_*.py`, sınıf adı `Test*`, fonksiyon adı `test_*` standardına uymalıdır.

## Tool Path Tutarlılığı

VS Code task'ları `.codex/TOOL-PATHS.local.properties` dosyasına bağımlı değildir; ancak terminal komutları `bsg-offline-python-toolchain` skill'i kapsamı altında çalıştırılır.

## Ortak Sorunlar

- **Python yorumlayıcı bulunamadı**: `Python: Select Interpreter` komutu ile `.venv` seçilir.
- **Streamlit başlamıyor**: virtualenv aktive değilse task `command` yolu hatalı olabilir; tam path verilir.
- **pytest discovery boş**: `pytest.ini`/`pyproject.toml` yoksa `python.testing.pytestArgs` ayarındaki yol doğrulanır.

## Change Contract

- Workspace ayarı değişiyorsa bu dosya aynı iş içinde güncellenir.
- Eklenti zorunlu hâle getirildiğinde `LOCAL-SETUP.md` ile birlikte tazelenir.
- Debug konfigürasyonu değişiyorsa örnek `launch.json`/`tasks.json` parçası burada güncel kalır.
