package com.powerdialer.redis.event.interactionnotifier.dto;

import java.util.HashMap;
import java.util.Map;

/**
 * Enum representing the mapping from result_code_text to call_result_code_id.
 * Based on the SQL CASE statement in gbinteractionnotifier_get_call_info stored procedure.
 */
public enum CallResultCodeEnum {

    ARANMADI(1, "ARANMADI"),
    RANDEVU_BILGISI_VERILDI_IPTAL_EDILDI(2, "RANDEVU BILGISI VERILDI IPTAL EDILDI"),
    YENI_RANDEVU_VERILDI(3, "YENI RANDEVU VERILDI"),
    CEVAP_VERMEDI(4, "CEVAP VERMEDI"),
    YANLIS_NUMARA(5, "YANLIS NUMARA"),
    TEKNIK_ILETISIM(6, "TEKNIK ILETISIM"),
    MESGUL(7, "MESGUL"),
    TELEFON_KAPALI_CEKMIYOR(8, "TELEFON KAPALI/CEKMIYOR"),
    YAKININA_BILGI_VERILDI_IPTAL_EDEMEMEZ(9, "YAKININA BILGI VERILDI IPTAL EDILEMEDI"),
    VATANDAS_IPTALI_ONAYLAMADI(10, "VATANDAS IPTALI ONAYLAMADI"),
    VATANDAS_OPERATORLE_GORUSMEYI_SONLANDIRDI(11, "VATANDAS OPERATORLE GORUSMEYI SONLANDIRDI"),
    NUMARA_IPTAL_EDILMIS_KAYITLI_DEGIL(12, "NUMARA IPTAL EDILMIS/KAYITLI DEGIL"),
    MUAYENE_YERI_DEGISIKLIGI_SMS_GONDERILDI(13, "MUAYENE YERI DEGISIKLIGI SMS GONDERILDI"),
    CAKISMA_BILGILENDIRME_SMS_GONDERILDI(14, "CAKISMA BILGILENDIRME SMS GONDERILDI"),
    FAX_MODEM(15, "FAX-MODEM"),
    VATANDAS_GORUSMEYI_SONLANDIRDI_DIALER(16, "VATANDAS GORUSMEYI SONLANDIRDI(DIALER)"),
    MUAYENE_YERI_DEGISIKLIGI_ISTISNA_SMS_GONDERILDI(17, "MUAYENE YERI DEGISIKLIGI (ISTISNA) SMS GONDERILDI");

    private final int id;
    private final String text;

    private static final Map<String, CallResultCodeEnum> MAP_BY_TEXT = new HashMap<>();
    private static final Map<Integer, CallResultCodeEnum> MAP_BY_ID = new HashMap<>();

    static {
        for (CallResultCodeEnum code : values()) {
            MAP_BY_TEXT.put(code.text, code);
            MAP_BY_ID.put(code.id, code);
        }
    }

    CallResultCodeEnum(int id, String text) {
        this.id = id;
        this.text = text;
    }

    public int getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    /**
     * Gets the CallResultCodeEnum by its ID.
     *
     * @param id The integer ID of the result code.
     * @return The corresponding enum, or ARANMADI (default) if not found.
     */
    public static CallResultCodeEnum getById(int id) {
        return MAP_BY_ID.getOrDefault(id, ARANMADI);
    }

    /**
     * Converts a result_code_text string to its corresponding call_result_code_id.
     * Uses a map lookup for performance and readability.
     *
     * @param resultCodeText The text value from the database (e.g., "ARANMADI").
     * @return The corresponding integer ID (e.g., 1), or 1 (default) if not matched.
     */
    public static int getIdFromText(String resultCodeText) {
        if (resultCodeText == null) {
            return ARANMADI.getId();
        }
        
        CallResultCodeEnum code = MAP_BY_TEXT.get(resultCodeText.trim());
        if (code != null) {
            return code.getId();
        }
        
        // If not found, return default
        return ARANMADI.getId();
    }
}