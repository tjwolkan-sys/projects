package tests.redis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerdialer.model.CallResultCode;
import com.powerdialer.util.Constants;

/**
 * Redis contract geriye uyumluluğu: CallResultCode payload'ına eklenen
 * opsiyonel dncScope alanı additive'dir. Alanı göndermeyen eski gbwebphone
 * mesajları null scope ile bugünkü (PHONE) davranışa düşer; bilinmeyen
 * değerler exception üretmeden null'a düşer.
 */
public class CallResultCodeDeserializerDncScopeBackwardCompatTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  public void payloadWithoutDncScopeParsesWithNullScope() throws Exception {
    CallResultCode callResultCode = objectMapper.readValue(
        "{\"parentId\":\"dl-1\",\"userId\":1,\"callId\":\"c-1\",\"resultCode\":{\"id\":5}}",
        CallResultCode.class);

    assertNull("Eski mesajlarda dncScope null kalmalı (PHONE davranışı)", callResultCode.getDncScope());
    assertEquals("dl-1", callResultCode.getParentId());
    assertEquals(Long.valueOf(5L), callResultCode.getResultCode().getId());
  }

  @Test
  public void payloadWithPhoneScopeParsesAsPhone() throws Exception {
    CallResultCode callResultCode = objectMapper.readValue(
        "{\"parentId\":\"dl-1\",\"userId\":1,\"resultCode\":{\"id\":5},\"dncScope\":\"PHONE\"}",
        CallResultCode.class);

    assertEquals(Constants.DNC_SCOPE.PHONE, callResultCode.getDncScope());
  }

  @Test
  public void payloadWithCustomerScopeParsesAsCustomer() throws Exception {
    CallResultCode callResultCode = objectMapper.readValue(
        "{\"parentId\":\"dl-1\",\"userId\":1,\"resultCode\":{\"id\":5},\"dncScope\":\"CUSTOMER\"}",
        CallResultCode.class);

    assertEquals(Constants.DNC_SCOPE.CUSTOMER, callResultCode.getDncScope());
  }

  @Test
  public void payloadWithUnknownScopeValueParsesWithNullScopeWithoutException() throws Exception {
    CallResultCode callResultCode = objectMapper.readValue(
        "{\"parentId\":\"dl-1\",\"userId\":1,\"resultCode\":{\"id\":5},\"dncScope\":\"GARBAGE\"}",
        CallResultCode.class);

    assertNull("Bilinmeyen scope değeri null'a düşmeli, exception atmamalı", callResultCode.getDncScope());
  }

  @Test
  public void payloadWithNullScopeValueParsesWithNullScope() throws Exception {
    CallResultCode callResultCode = objectMapper.readValue(
        "{\"parentId\":\"dl-1\",\"userId\":1,\"resultCode\":{\"id\":5},\"dncScope\":null}",
        CallResultCode.class);

    assertNull(callResultCode.getDncScope());
  }
}
