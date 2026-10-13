# 07.05.2026 Change/Fix Planları

- `FIX-317516-AWAITING-USER-RESULT-RACE-OVERWRITE.md`: Hangup sonrası `awaitingUserResultCodeCalls` cache'ine geçiş yarış penceresi, awaiting-timeout `processCallResult` overwrite davranışı ve `shouldSetResultCode=false` durumunda durable fallback boşluğunun birlikte ele alınması planı.
