# 06.08.2026 Change/Fix Planları

- `FIX-GLB90060295-AWAITING-URC-AUTHORITATIVE-RECOVERY.md`: `telephony_rc = 10` olup `user_result_code_id` NULL kalan kayıtların iki kök nedeninin (agent result code'unun Redis mesajı dialer'a hiç ulaşmaması; durable fallback ile awaiting-timeout finalize'ının aynı `pd_cdr` satırında yazma yarışı) awaiting-timeout finalize'ında authoritative webphone result code lookup'ı ve `persistFinalCdr` null-ezme koruması ile kalıcı çözümü planı. FIX-317516 (07.05.2026) B2 kararının kapsamadığı iki boşluğu kapatır. Açık soru bekliyor.
