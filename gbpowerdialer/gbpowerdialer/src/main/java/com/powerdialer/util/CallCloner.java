package com.powerdialer.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerdialer.model.Call;

public class CallCloner {
    private static final ObjectMapper objectMapper = new ObjectMapper();
 
    public static Call clone(Call original) {
        try {
            // Nesneyi JSON string'e çevir
            String json = objectMapper.writeValueAsString(original);
 
            // JSON'dan yeni Call nesnesi oluştur
            return objectMapper.readValue(json, Call.class);
        } catch (Exception e) {
            throw new RuntimeException("Call nesnesi klonlanırken hata oluştu", e);
        }
    }
}