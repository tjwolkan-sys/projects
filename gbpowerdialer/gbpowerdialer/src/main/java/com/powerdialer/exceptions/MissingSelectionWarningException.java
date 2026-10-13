package com.powerdialer.exceptions;

/**
 * Operasyon ve Kampanya seçilmeden kayıt getirme denemesi
 */
public class MissingSelectionWarningException extends WarningException {

    private static final long serialVersionUID = 8726153036812645417L;

    public MissingSelectionWarningException() {
        super("Operasyon ve Kampanya alanları seçilmeden mevcut kayıtlar listelenemez.");
    }
}
