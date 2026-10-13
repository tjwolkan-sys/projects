package tests.ami;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.powerdialer.engine.asterisk.ami.ManagerConnectionComposition;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionDecorator;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.util.LoggerDecorator;

/**
 * FIX-333599 — Görüşmesi sürmekte olan (originate edilmiş, finalize edilmemiş) çağrıların
 * uniqueKey'leri bağlantı cache'lerinden toplanabilmeli; producer'ın mükerrer fetch guard'ı
 * (FIX-330538) bu set ile görüşmedeki satırı ikinci kez Call'a dönüştürmez.
 *
 * <p>Vaka (02.10.2026, contact 326361): ilk çağrı çalarken satır yeniden çekildi; guard yalnız
 * cache ve callQueue'ya baktığı için yeni Call üretildi. Dört cache de kapsanmalı: {@code calls}
 * (çalıyor/görüşmede), {@code awaitingUserResultCodeCalls} (wrap-up, URC bekleniyor),
 * {@code recoverableConnectionLossCalls} ve {@code orphanedCalls}. Hepsinin expiry'si olduğu için
 * takılan bir çağrı satırı kalıcı olarak kilitlemez.</p>
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class InFlightCallUniqueKeysRegressionTest {

  @Mock
  private LoggerDecorator loggerDecorator;
  @Mock
  private ManagerConnectionComposition connectionComposition;

  private ManagerConnectionDecorator decorator;

  @Before
  public void setUp() {
    when(loggerDecorator.getLogger(ManagerConnectionDecorator.class)).thenReturn(loggerDecorator);
    when(connectionComposition.getLoggerDecorator()).thenReturn(loggerDecorator);
    decorator = new ManagerConnectionDecorator("127.0.0.1", 5038, "user", "password", connectionComposition);
  }

  @Test
  public void collectsUniqueKeysOfCampaignCallsFromAllInFlightCaches() {
    decorator.getCalls().put("dl-1", call("dl-1", 6L, "326361"));
    decorator.getAwaitingUserResultCodeCalls().put("dl-2", call("dl-2", 6L, "326426"));
    decorator.getRecoverableConnectionLossCalls().put("dl-3", call("dl-3", 6L, "326546"));
    decorator.getOrphanedCalls().put("dl-4", call("dl-4", 6L, "326538"));

    Set<String> keys = new HashSet<>();
    decorator.collectInFlightUniqueKeys(6L, keys);

    assertEquals(new HashSet<>(Arrays.asList("326361", "326426", "326546", "326538")), keys);
  }

  /** Aynı uniqueKey başka kampanyanın tablosunda başka bir kayıttır; guard'ı tetiklememeli. */
  @Test
  public void ignoresCallsOfOtherCampaignsAndCallsWithoutKey() {
    decorator.getCalls().put("dl-1", call("dl-1", 64L, "326361"));
    decorator.getCalls().put("dl-2", call("dl-2", 6L, null));
    decorator.getCalls().put("dl-3", callWithoutCampaign("dl-3", "326999"));

    Set<String> keys = new HashSet<>();
    decorator.collectInFlightUniqueKeys(6L, keys);

    assertEquals(new HashSet<String>(), keys);
  }

  private static Call call(String id, Long campaignId, String uniqueKey) {
    Campaign campaign = new Campaign();
    campaign.setId(campaignId);
    Call call = callWithoutCampaign(id, uniqueKey);
    call.setCampaign(campaign);
    return call;
  }

  private static Call callWithoutCampaign(String id, String uniqueKey) {
    Call call = new Call();
    call.setId(id);
    call.setUniqueKey(uniqueKey);
    return call;
  }
}
