package com.powerdialer.redis.event.interactionnotifier.dto;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link CallResultCodeEnum}.
 *
 * <p>Covers every lookup path:
 * <ul>
 *   <li>getIdFromText — happy path, trimming, null, unknown</li>
 *   <li>getById — known id, unknown id, boundary</li>
 *   <li>Enum getters (getId / getText) for every constant</li>
 * </ul>
 * </p>
 */
public class CallResultCodeEnumTest {

    // -----------------------------------------------------------------------
    // getIdFromText
    // -----------------------------------------------------------------------

    /** Known text must return its corresponding id. */
    @Test
    public void getIdFromText_knownText_returnsCorrectId() {
        assertEquals(1,  CallResultCodeEnum.getIdFromText("ARANMADI"));
        assertEquals(2,  CallResultCodeEnum.getIdFromText("RANDEVU BILGISI VERILDI IPTAL EDILDI"));
        assertEquals(3,  CallResultCodeEnum.getIdFromText("YENI RANDEVU VERILDI"));
        assertEquals(4,  CallResultCodeEnum.getIdFromText("CEVAP VERMEDI"));
        assertEquals(5,  CallResultCodeEnum.getIdFromText("YANLIS NUMARA"));
        assertEquals(6,  CallResultCodeEnum.getIdFromText("TEKNIK ILETISIM"));
        assertEquals(7,  CallResultCodeEnum.getIdFromText("MESGUL"));
        assertEquals(8,  CallResultCodeEnum.getIdFromText("TELEFON KAPALI/CEKMIYOR"));
        assertEquals(9,  CallResultCodeEnum.getIdFromText("YAKININA BILGI VERILDI IPTAL EDILEMEDI"));
        assertEquals(10, CallResultCodeEnum.getIdFromText("VATANDAS IPTALI ONAYLAMADI"));
        assertEquals(11, CallResultCodeEnum.getIdFromText("VATANDAS OPERATORLE GORUSMEYI SONLANDIRDI"));
        assertEquals(12, CallResultCodeEnum.getIdFromText("NUMARA IPTAL EDILMIS/KAYITLI DEGIL"));
        assertEquals(13, CallResultCodeEnum.getIdFromText("MUAYENE YERI DEGISIKLIGI SMS GONDERILDI"));
        assertEquals(14, CallResultCodeEnum.getIdFromText("CAKISMA BILGILENDIRME SMS GONDERILDI"));
        assertEquals(15, CallResultCodeEnum.getIdFromText("FAX-MODEM"));
        assertEquals(16, CallResultCodeEnum.getIdFromText("VATANDAS GORUSMEYI SONLANDIRDI(DIALER)"));
        assertEquals(17, CallResultCodeEnum.getIdFromText("MUAYENE YERI DEGISIKLIGI (ISTISNA) SMS GONDERILDI"));
    }

    /** Null input must return default id (ARANMADI = 1). */
    @Test
    public void getIdFromText_nullInput_returnsDefault() {
        assertEquals(1, CallResultCodeEnum.getIdFromText(null));
    }

    /** Unknown text must return default id (ARANMADI = 1). */
    @Test
    public void getIdFromText_unknownText_returnsDefault() {
        assertEquals(1, CallResultCodeEnum.getIdFromText("TOTALLY_UNKNOWN"));
    }

    /** Leading/trailing whitespace must be trimmed before lookup. */
    @Test
    public void getIdFromText_textWithWhitespace_returnsTrimmedMatch() {
        assertEquals(1, CallResultCodeEnum.getIdFromText("  ARANMADI  "));
    }

    /** Empty string must return default (no match). */
    @Test
    public void getIdFromText_emptyString_returnsDefault() {
        assertEquals(1, CallResultCodeEnum.getIdFromText(""));
    }

    // -----------------------------------------------------------------------
    // getById
    // -----------------------------------------------------------------------

    /** Every valid id must resolve to the correct enum constant. */
    @Test
    public void getById_allValidIds_returnCorrectEnum() {
        assertEquals(CallResultCodeEnum.ARANMADI,                                        CallResultCodeEnum.getById(1));
        assertEquals(CallResultCodeEnum.RANDEVU_BILGISI_VERILDI_IPTAL_EDILDI,           CallResultCodeEnum.getById(2));
        assertEquals(CallResultCodeEnum.YENI_RANDEVU_VERILDI,                           CallResultCodeEnum.getById(3));
        assertEquals(CallResultCodeEnum.CEVAP_VERMEDI,                                  CallResultCodeEnum.getById(4));
        assertEquals(CallResultCodeEnum.YANLIS_NUMARA,                                  CallResultCodeEnum.getById(5));
        assertEquals(CallResultCodeEnum.TEKNIK_ILETISIM,                                CallResultCodeEnum.getById(6));
        assertEquals(CallResultCodeEnum.MESGUL,                                         CallResultCodeEnum.getById(7));
        assertEquals(CallResultCodeEnum.TELEFON_KAPALI_CEKMIYOR,                        CallResultCodeEnum.getById(8));
        assertEquals(CallResultCodeEnum.YAKININA_BILGI_VERILDI_IPTAL_EDEMEMEZ,         CallResultCodeEnum.getById(9));
        assertEquals(CallResultCodeEnum.VATANDAS_IPTALI_ONAYLAMADI,                     CallResultCodeEnum.getById(10));
        assertEquals(CallResultCodeEnum.VATANDAS_OPERATORLE_GORUSMEYI_SONLANDIRDI,      CallResultCodeEnum.getById(11));
        assertEquals(CallResultCodeEnum.NUMARA_IPTAL_EDILMIS_KAYITLI_DEGIL,            CallResultCodeEnum.getById(12));
        assertEquals(CallResultCodeEnum.MUAYENE_YERI_DEGISIKLIGI_SMS_GONDERILDI,       CallResultCodeEnum.getById(13));
        assertEquals(CallResultCodeEnum.CAKISMA_BILGILENDIRME_SMS_GONDERILDI,          CallResultCodeEnum.getById(14));
        assertEquals(CallResultCodeEnum.FAX_MODEM,                                      CallResultCodeEnum.getById(15));
        assertEquals(CallResultCodeEnum.VATANDAS_GORUSMEYI_SONLANDIRDI_DIALER,         CallResultCodeEnum.getById(16));
        assertEquals(CallResultCodeEnum.MUAYENE_YERI_DEGISIKLIGI_ISTISNA_SMS_GONDERILDI, CallResultCodeEnum.getById(17));
    }

    /** Unknown id must fall back to ARANMADI. */
    @Test
    public void getById_unknownId_returnsDefault() {
        assertEquals(CallResultCodeEnum.ARANMADI, CallResultCodeEnum.getById(999));
    }

    /** Zero id must fall back to ARANMADI. */
    @Test
    public void getById_zeroId_returnsDefault() {
        assertEquals(CallResultCodeEnum.ARANMADI, CallResultCodeEnum.getById(0));
    }

    /** Negative id must fall back to ARANMADI. */
    @Test
    public void getById_negativeId_returnsDefault() {
        assertEquals(CallResultCodeEnum.ARANMADI, CallResultCodeEnum.getById(-1));
    }

    // -----------------------------------------------------------------------
    // Enum getters
    // -----------------------------------------------------------------------

    /** ARANMADI id getter must return 1. */
    @Test
    public void enumGetters_aranmadi_correctValues() {
        assertEquals(1, CallResultCodeEnum.ARANMADI.getId());
        assertEquals("ARANMADI", CallResultCodeEnum.ARANMADI.getText());
    }

    /** YENI_RANDEVU_VERILDI id getter must return 3. */
    @Test
    public void enumGetters_yeniRandevu_correctValues() {
        assertEquals(3, CallResultCodeEnum.YENI_RANDEVU_VERILDI.getId());
        assertEquals("YENI RANDEVU VERILDI", CallResultCodeEnum.YENI_RANDEVU_VERILDI.getText());
    }

    /** FAX_MODEM id getter must return 15. */
    @Test
    public void enumGetters_faxModem_correctValues() {
        assertEquals(15, CallResultCodeEnum.FAX_MODEM.getId());
        assertEquals("FAX-MODEM", CallResultCodeEnum.FAX_MODEM.getText());
    }

    /** Last enum constant id getter must return 17. */
    @Test
    public void enumGetters_lastConstant_correctValues() {
        assertEquals(17, CallResultCodeEnum.MUAYENE_YERI_DEGISIKLIGI_ISTISNA_SMS_GONDERILDI.getId());
    }

    /** values() count must equal 17. */
    @Test
    public void values_count_isSeventeen() {
        assertEquals(17, CallResultCodeEnum.values().length);
    }
}