package tests.redis;

import com.powerdialer.redis.UserSubscriber;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

import static org.junit.Assert.assertEquals;

/**
 * SORUN 4 Fix-1 karakterizasyonu: serit yonlendirme anahtari (userId) ham mesajdan objectMapper
 * kullanilmadan cikarilir (Redisson thread'inde hafif kalmali). Ayni agent'in STATUS ve
 * LOGIN/LOGOUT mesajlari ayni key'i uretmeli ki hepsi ayni seritte FIFO islensin; taninmayan
 * payload 0'a duser (yine gecerli bir serite gider, sadece siralama garantisi o kullanici
 * ozelinde anlamsizdir).
 */
public class UserSubscriberRoutingKeyTest {

  private long extract(String rawMessage) throws Exception {
    return Whitebox.invokeMethod(new UserSubscriber(), "extractRoutingKeyFast", rawMessage);
  }

  @Test
  public void statusMessage_topLevelUserIdExtracted() throws Exception {
    long key = extract("{\"userId\": 123, \"status\": {\"id\": 12034, \"name\": \"Mola\"}, \"statusSetDate\": 1719999999000}");
    assertEquals(123L, key);
  }

  @Test
  public void sessionLogoutMessage_topLevelUserIdExtracted() throws Exception {
    long key = extract("{\"type\":\"LOGOUT\",\"userId\":987}");
    assertEquals(987L, key);
  }

  @Test
  public void loginMessage_nestedUserIdExtracted_evenWithNestedGroupObject() throws Exception {
    // Gercek user payload'i group/status gibi id'li ALT objeler icerir. Eski greedy regex ilk '}'
    // oncesindeki SON id'yi (group.id=7) yakalar, ayni agent'in LOGIN'i farkli serite duserdi
    // (code-review bulgusu). Yeni lazy+brace'siz desen user'in KENDI id'sini yakalamali.
    long key = extract("{\"type\":\"LOGIN\",\"user\":{\"id\":456,\"username\":\"81456\",\"group\":{\"id\":7,\"name\":\"g\"}}}");
    assertEquals(456L, key);
  }

  @Test
  public void loginMessage_topLevelUserIdWinsOverNestedIds() throws Exception {
    long key = extract("{\"type\":\"LOGIN\",\"userId\":456,\"user\":{\"group\":{\"id\":7},\"id\":456}}");
    assertEquals(456L, key);
  }

  @Test
  public void loginMessage_nestedObjectBeforeId_fallsBackToZeroStripe() throws Exception {
    // Degrade mod: user objesi kendi id'sinden ONCE alt obje iceriyorsa eslesme olmaz -> 0.
    // Ayni kullanicinin bu bicimdeki TUM mesajlari ayni 0 seridine dustugu icin sira korunur.
    long key = extract("{\"type\":\"LOGIN\",\"user\":{\"group\":{\"id\":7},\"id\":456}}");
    assertEquals(0L, key);
  }

  @Test
  public void unknownPayload_fallsBackToZero() throws Exception {
    assertEquals(0L, extract("{\"foo\":\"bar\"}"));
  }

}
