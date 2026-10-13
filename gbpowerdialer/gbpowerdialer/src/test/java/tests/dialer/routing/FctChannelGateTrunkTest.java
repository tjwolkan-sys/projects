package tests.dialer.routing;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import java.util.Arrays;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.globalbilgi.gbqueueselectionservice.grpc.RouteDetailEntry;
import com.powerdialer.dto.FctChannelEntryDTO;
import com.powerdialer.engine.dialer.routing.FctChannelGate;
import com.powerdialer.engine.dialer.routing.FctChannelRegistry;
import com.powerdialer.engine.dialer.routing.RouteDetailsCursor;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.util.Constants;

/**
 * Seçili FCT numarasının trunk'ı ({@code RouteDetailEntry.trunk}) {@code Call.fctTrunk}'a taşınır.
 *
 * <p>Sözleşme: trunk caller ID ile <b>aynı noktada</b> set edilir. İkisi QSS'te aynı entry'nin
 * alanları olduğu için ayrışmaları (numara A, trunk B) çağrıyı yanlış trunk'tan çıkarır.
 *
 * <p>Değer AMI'de {@code Variable: DIALER_TRUNK=<deger>} başlığına yazıldığından format da burada
 * doğrulanır: satır sonu veya {@code :} içeren bir değer AMI protokolünü bozar ve aynı bağlantıdaki
 * sonraki action'ları da etkiler. Geçersiz değerde trunk düşürülür (çağrı varsayılan trunk'a düşer),
 * çağrı kaybedilmez.
 */
public class FctChannelGateTrunkTest {

  private static final String FCT_A = "05550000401";
  private static final String FCT_B = "05550000402";

  private FctChannelRegistry registry;
  private FctChannelGate gate;

  @Before
  public void setUp() {
    registry = newRegistry();
    gate = new FctChannelGate();
    gate.setRegistry(registry);
    gate.setDeferLimit(10);
    gate.setListRetryMax(1);
    gate.setFixedLineRetryMax(1);
  }

  @Test
  public void shouldCarryTrunkAlongsideCallerIdOnReservation() {
    Call call = fctCall(entry(FCT_A, "ININ"), entry(FCT_B, "SOL"));

    assertThat(gate.reserveForOrigination(call), is(true));

    assertThat(call.getCallerId(), is(FCT_A));
    assertThat(call.getFctTrunk(), is("ININ"));
  }

  /** QSS tanımsız trunk için boş string döner; PD bunu "varsayılan trunk" olarak null'a çevirir. */
  @Test
  public void shouldLeaveTrunkNullWhenQssReturnsEmpty() {
    Call call = fctCall(entry(FCT_A, ""));

    assertThat(gate.reserveForOrigination(call), is(true));

    assertThat(call.getCallerId(), is(FCT_A));
    assertThat(call.getFctTrunk(), is(nullValue()));
  }

  /**
   * AMI koruması: satır sonu, taşıma dönüşü, {@code :} veya boşluk içeren değer protokolü bozar.
   * QSS kendi tarafında aynı formatı doğruluyor; bu ikinci kapı bozuk bir kaydın AMI bağlantısını
   * düşürmesini engeller.
   */
  @Test
  public void shouldDropTrunkWhenValueIsNotProtocolSafe() {
    for (String unsafe : Arrays.asList("ININ\nSet: x", "ININ\r\nAction: Hangup", "IN IN", "ININ:1",
                                       "trunk;drop", repeat("A", 65))) {
      // Her vaka icin temiz registry: FCT_A kapasitesi 1, aksi halde ikinci iterasyon rezervasyon alamaz.
      setUp();
      Call call = fctCall(entry(FCT_A, unsafe));

      assertThat(gate.reserveForOrigination(call), is(true));

      assertThat("gecersiz trunk dusurulmeli: " + unsafe.replaceAll("[^A-Za-z0-9_.-]", "?"),
          call.getFctTrunk(), is(nullValue()));
      assertThat("trunk gecersiz olsa da cagri caller ID'siz kalmaz", call.getCallerId(), is(FCT_A));
    }
  }

  @Test
  public void shouldAcceptMaximumLengthAndAllowedCharacters() {
    Call call = fctCall(entry(FCT_A, "trunk_1.PRP-02"));

    gate.reserveForOrigination(call);

    assertThat(call.getFctTrunk(), is("trunk_1.PRP-02"));
  }

  /** cause 34 sonrası sıradaki FCT'ye geçilirken trunk da o entry'ninki olur. */
  @Test
  public void shouldSwitchTrunkWhenAdvancingAfterNoCircuit() {
    Call call = fctCall(entry(FCT_A, "ININ"), entry(FCT_B, "SOL"));
    gate.reserveForOrigination(call);
    assertThat(call.getFctTrunk(), is("ININ"));

    assertThat(gate.reserveNextAfterNoCircuit(call), is(true));

    assertThat(call.getCallerId(), is(FCT_B));
    assertThat("trunk caller ID ile birlikte degismeli", call.getFctTrunk(), is("SOL"));
  }

  /** Rezervasyon alınamazsa (kapasite dolu) ne caller ID ne trunk yazılır. */
  @Test
  public void shouldNotTouchTrunkWhenReservationFails() {
    Call first = fctCall(entry(FCT_A, "ININ"));
    gate.reserveForOrigination(first);

    Call second = fctCall(entry(FCT_A, "ININ"));
    second.setCallerId(null);
    second.setFctTrunk(null);

    assertThat("kapasite 1, ikinci cagri rezervasyon alamaz", gate.reserveForOrigination(second), is(false));
    assertThat(second.getFctTrunk(), is(nullValue()));
  }

  /**
   * Monitor ekranı ile Asterisk'e gönderilen değer ayrışamaz: snapshot'taki trunk, {@code Call}
   * üzerinden {@code DIALER_TRUNK} olarak gönderilen DOĞRULANMIŞ değerin aynısıdır. Ayrışırsa ekran
   * "ININ" gösterirken çağrı varsayılan trunk'tan çıkar ve teşhis yanlış yöne gider.
   */
  @Test
  public void shouldPublishToMonitorExactlyWhatIsSentToAsterisk() {
    Call call = fctCall(entry(FCT_A, "ININ"));

    gate.reserveForOrigination(call);

    assertThat(monitorTrunk(FCT_A), is(call.getFctTrunk()));
    assertThat(monitorTrunk(FCT_A), is("ININ"));
  }

  @Test
  public void shouldNotPublishRejectedTrunkToMonitor() {
    Call call = fctCall(entry(FCT_A, "ININ:1"));

    gate.reserveForOrigination(call);

    assertThat("AMI'ye gonderilmeyen deger ekranda da gorunmemeli",
        monitorTrunk(FCT_A), is(nullValue()));
    assertThat(monitorTrunk(FCT_A), is(call.getFctTrunk()));
  }

  // ---------------------------------------------------------------- fixture

  private String monitorTrunk(String phoneNumber) {
    for (FctChannelEntryDTO entry : registry.snapshot().getEntries()) {
      if (phoneNumber.equals(entry.getPhoneNumber())) {
        return entry.getTrunk();
      }
    }
    throw new AssertionError("Snapshot'ta numara bulunamadi: " + phoneNumber);
  }

  private Call fctCall(RouteDetailEntry... entries) {
    Campaign campaign = new Campaign();
    campaign.setId(96L);
    campaign.setType(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED);
    campaign.setCallerIdBehaviour(Constants.CALLER_ID_BEHAVIOUR.POOL);
    campaign.setFctRoutingEnabled(true);

    Call call = new Call();
    call.setId("dl-trunk-" + System.nanoTime());
    call.setCampaign(campaign);
    call.setCalledNumber("5551000");
    call.setRawData(new JSONObject().put("phone1", "5551000"));
    call.setRouteDetailsCursor(new RouteDetailsCursor(Arrays.asList(entries)));
    return call;
  }

  private RouteDetailEntry entry(String phoneNumber, String trunk) {
    return RouteDetailEntry.newBuilder()
        .setPhoneNumber(phoneNumber)
        .setType("MOBILE")
        .setAttempts("1")
        .setCapacity(1)
        .setTrunk(trunk)
        .build();
  }

  private String repeat(String value, int times) {
    StringBuilder builder = new StringBuilder(value.length() * times);
    for (int i = 0; i < times; i++) {
      builder.append(value);
    }
    return builder.toString();
  }

  private FctChannelRegistry newRegistry() {
    FctChannelRegistry created = new FctChannelRegistry();
    ReflectionTestUtils.setField(created, "gateEnabled", true);
    ReflectionTestUtils.setField(created, "ttlMs", 3900000L);
    ReflectionTestUtils.setField(created, "staleWarnMs", 600000L);
    ReflectionTestUtils.setField(created, "cooldownMs", 60000L);
    ReflectionTestUtils.setField(created, "headroomPercent", 0);
    ReflectionTestUtils.setField(created, "metaRetentionMs", 1800000L);
    ReflectionTestUtils.setField(created, "eventLogSize", 200);
    return created;
  }
}
