package tests.ami;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.asteriskjava.AsteriskVersion;
import org.asteriskjava.manager.action.ManagerAction;
import org.asteriskjava.manager.action.OriginateAction;
import org.asteriskjava.util.ReflectionUtil;
import org.json.JSONObject;
import org.junit.Test;

import com.powerdialer.engine.asterisk.actions.OutboundIVRAction;
import com.powerdialer.engine.asterisk.actions.PreviewFreeAction;
import com.powerdialer.engine.asterisk.actions.PreviewStraightAction;
import com.powerdialer.engine.asterisk.actions.ProgressiveReleasedAction;
import com.powerdialer.engine.asterisk.actions.ProgressiveReservedAction;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.Group;
import com.powerdialer.model.Tenant;
import com.powerdialer.model.User;
import com.powerdialer.util.Constants;

/**
 * FIX-GLB90060295: Originate action'ın AMI temsiline sızan {@code call:} başlığı.
 *
 * <p>asterisk-java action'ları sabit alan listesiyle değil refleksiyonla serileştirir:
 * {@code ReflectionUtil.getGetters} action sınıfının <b>public</b>, parametresiz, adı
 * {@code get}/{@code is} ile başlayan tüm metotlarını toplar; {@code ActionBuilderImpl.appendGetters}
 * bunlardan {@code {class, action, actionid, attributes}} dışında kalan her birini bir AMI başlığı
 * yapar ve String/Map olmayan değerleri {@code value.toString()} ile yazar. Bu yüzden
 * {@code AbstractOriginateAction.getCall()} public kaldığı sürece her Originate'e
 * {@code call: Call [ … retryHistory=[…] … ]} satırı eklenir.</p>
 *
 * <p>{@code retryHistory} kampanya result tablosundaki kolondan hidrate edilip geri yazıldığı için
 * günler boyunca birikir; satır Asterisk manager'ın satır tamponunu (1024 bayt) aşınca Asterisk
 * originate'i {@code 'Failed to parse message: line too long'} ile reddeder. Üretimde ölçülen değer
 * 1264 bayttı (37 retry kaydı) ve çağrı hiç kurulmadı (TRC id 79 / code 907).</p>
 *
 * <p>Testler mekanizmayı iki koldan kilitler: (1) hiçbir AMI satırı limiti aşmamalı,
 * (2) {@code call} refleksiyon haritasına hiç girmemeli. Üçüncü test over-removal'ı engeller:
 * dialplan/AGI'nin okuduğu kanal değişkenleri ve Originate alanları gönderilmeye devam etmeli.</p>
 */
public class OriginateActionAmiLineLengthRegressionTest {

  /**
   * Asterisk {@code main/manager.c} oturum giriş tamponu 1025 bayttır; satır sonu bulunmadan tampon
   * dolarsa {@code astman_send_error(... "Failed to parse message: line too long")} döner. Testte
   * güvenli üst sınır olarak 1023 bayt kullanılır.
   */
  private static final int ASTERISK_MAX_AMI_LINE_BYTES = 1023;

  private static final String INBOUND_CONTEXT = "out-dialer-attempt";

  /**
   * Fixture değerleri üretim vakasının <b>uzunluk</b> profilini taşır ama gerçek müşteri/agent
   * verisi içermez (AGENTS: yeni dosyalara gerçek numara, isim veya IP yazılmaz). Uzunluklar
   * bilinçli olarak korunmuştur; satır uzunluğu invariant'ı bu profile dayanır ve
   * {@link #fixtureShouldStayWorstCaseSoThatLineLimitAssertionIsNotVacuous()} bunu kilitler.
   */
  private static final String CALL_ID = "dl-00000000-0000-4000-8000-000000000001";
  private static final String CALLED_NUMBER = "05550000001";
  private static final String ALTERNATE_NUMBER = "05550000002";
  private static final String CONTACT_ID = "999999";
  private static final String AGENT_USERNAME = "99999";
  private static final long AGENT_ID = 1234L;
  private static final String CAMPAIGN_NAME = "CAMPAIGN_WITH_A_FAIRLY_LONG_NAME_X";

  /**
   * Üretimde reddedilen çağrının retry geçmişi 37 kayıttı. Test eşiği aşmayı garanti etmek için
   * 40 kayıt kullanır; fix sonrası bu liste AMI'ye hiç gitmediği için sayı invariant'ı bozmaz.
   */
  private static final int RETRY_HISTORY_SIZE = 40;

  @Test
  public void shouldKeepEveryAmiLineWithinAsteriskLimitWhenRetryHistoryIsLong() throws Exception {
    for (AsteriskVersion version : Arrays.asList(AsteriskVersion.ASTERISK_13, AsteriskVersion.ASTERISK_1_0)) {
      for (OriginateAction action : originateActionsUnderTest()) {
        String amiText = buildAmiText(action, version);
        for (String line : amiText.split("\r\n")) {
          int lengthInBytes = line.getBytes(StandardCharsets.UTF_8).length;
          assertTrue("AMI satırı Asterisk limitini aşıyor (" + lengthInBytes + " bayt, sınır "
              + ASTERISK_MAX_AMI_LINE_BYTES + "). Asterisk bu action'ı 'line too long' ile reddeder. "
              + "Action: " + action.getClass().getSimpleName() + ", version: " + version
              + ", satır: " + truncateForMessage(line),
              lengthInBytes <= ASTERISK_MAX_AMI_LINE_BYTES);
        }
      }
    }
  }

  @Test
  public void shouldNotExposeCallAsAmiSerializableGetter() {
    List<Class<?>> actionTypes = Arrays.asList(ProgressiveReleasedAction.class, ProgressiveReservedAction.class,
        PreviewFreeAction.class, PreviewStraightAction.class, OutboundIVRAction.class);

    for (Class<?> actionType : actionTypes) {
      Map<String, Method> getters = ReflectionUtil.getGetters(actionType);
      assertThat("Originate action'ın 'call' property'si AMI'ye serileştirilmemeli; getter public "
          + "kaldığı sürece asterisk-java onu 'call:' başlığı yapar. Action: " + actionType.getSimpleName(),
          getters.containsKey("call"), is(false));
    }
  }

  @Test
  public void shouldStillSendDialplanVariablesAndChannelFields() throws Exception {
    String amiText = buildAmiText(progressiveReleasedAction(), AsteriskVersion.ASTERISK_13);

    assertThat(amiText, containsString("ORIGINID=" + CALL_ID));
    assertThat(amiText, containsString("PARENTID=" + CALL_ID));
    assertThat(amiText, containsString("CONTACTID=" + CONTACT_ID));
    assertThat(amiText, containsString("CAMPAIGNID=85"));
    assertThat(amiText, containsString("DIALED_NUMBER=" + CALLED_NUMBER));
    assertThat(amiText, containsString("AGENT_ID=" + AGENT_ID));
    assertThat(amiText.toLowerCase(), containsString("action: originate"));
    assertThat(amiText.toLowerCase(), containsString("actionid: " + CALL_ID.toLowerCase()));
    assertThat(amiText.toLowerCase(), containsString("channel: local/" + CALLED_NUMBER));
    assertThat(amiText.toLowerCase(), containsString("exten: " + AGENT_USERNAME));
  }

  /**
   * Satır uzunluğu invariant'ının boşa dönmesini engeller: fixture, {@code Call} nesnesi AMI
   * başlığı olarak gönderilseydi limitin <b>aşılacağı</b> kadar büyük kalmalıdır. Biri fixture'ı
   * küçültürse (ör. {@code RETRY_HISTORY_SIZE} düşerse) invariant testi sessizce anlamsızlaşırdı;
   * bu test o durumu kırmızıya çevirir.
   */
  @Test
  public void fixtureShouldStayWorstCaseSoThatLineLimitAssertionIsNotVacuous() {
    String callHeaderIfItWereStillSent = "call: " + callWithLongRetryHistory().toString();
    int lengthInBytes = callHeaderIfItWereStillSent.getBytes(StandardCharsets.UTF_8).length;

    assertTrue("Fixture artık worst-case değil (" + lengthInBytes + " bayt). Satır uzunluğu "
        + "invariant'ı anlamlı kalsın diye 'call:' başlığı gönderilse limiti (" + ASTERISK_MAX_AMI_LINE_BYTES
        + " bayt) aşacak kadar büyük olmalı.",
        lengthInBytes > ASTERISK_MAX_AMI_LINE_BYTES);
  }

  private List<OriginateAction> originateActionsUnderTest() {
    List<OriginateAction> actions = new ArrayList<>();
    actions.add(progressiveReleasedAction());
    actions.add(new ProgressiveReservedAction(callWithLongRetryHistory()).build(INBOUND_CONTEXT));
    actions.add(new PreviewFreeAction(callWithLongRetryHistory()).build(INBOUND_CONTEXT));
    actions.add(new PreviewStraightAction(callWithLongRetryHistory()).build(INBOUND_CONTEXT));
    return actions;
  }

  private OriginateAction progressiveReleasedAction() {
    return new ProgressiveReleasedAction(callWithLongRetryHistory()).build(INBOUND_CONTEXT);
  }

  /**
   * Gerçek {@code ActionBuilderImpl} üzerinden AMI metni üretir. Sınıf paket-private olduğu için
   * ctor ve metot refleksiyonla erişilebilir kılınır; amaç kütüphanenin gerçek serileştirme yolunu
   * test etmek, kopyasını değil. {@code targetVersion} set edilmezse {@code appendMap} NPE atar.
   */
  private String buildAmiText(ManagerAction action, AsteriskVersion targetVersion) throws Exception {
    Class<?> builderType = Class.forName("org.asteriskjava.manager.internal.ActionBuilderImpl");
    Constructor<?> constructor = builderType.getDeclaredConstructor();
    constructor.setAccessible(true);
    Object builder = constructor.newInstance();

    Method setTargetVersion = builderType.getDeclaredMethod("setTargetVersion", AsteriskVersion.class);
    setTargetVersion.setAccessible(true);
    setTargetVersion.invoke(builder, targetVersion);

    Method buildAction = builderType.getDeclaredMethod("buildAction", ManagerAction.class);
    buildAction.setAccessible(true);
    return (String) buildAction.invoke(builder, action);
  }

  private Call callWithLongRetryHistory() {
    Call call = new Call();
    call.setId(CALL_ID);
    call.setUniqueKey(CONTACT_ID);
    call.setCalledNumber(CALLED_NUMBER);
    call.setCallerId("08500000000");
    call.setRawData(new JSONObject());
    call.setCampaign(campaign());
    call.setUser(user());
    call.setRetryHistory(retryHistory());
    return call;
  }

  /**
   * Üretim vakasının birebir kopyası: numara rotasyonu iki telefon arasında dönüyor ve her retry
   * listeye bir kayıt daha ekliyor.
   */
  private List<String> retryHistory() {
    List<String> retryHistory = new ArrayList<>();
    for (int i = 0; i < RETRY_HISTORY_SIZE; i++) {
      retryHistory.add(i % 2 == 0 ? CALLED_NUMBER : ALTERNATE_NUMBER);
    }
    return retryHistory;
  }

  private Campaign campaign() {
    Campaign campaign = new Campaign();
    campaign.setId(85L);
    campaign.setName(CAMPAIGN_NAME);
    campaign.setType(Constants.CAMPAIGN_TYPE.PREDICTIVE);
    campaign.setContext("out-dialer");
    campaign.setRingTimeout(30);
    campaign.setAllowCallback(Boolean.TRUE);
    campaign.setAllowCallbackCustom(Boolean.TRUE);
    campaign.setResultCodeTimer(60);
    campaign.setResultCodeTimerStatus(1L);
    campaign.setAmdEnabled(Boolean.FALSE);
    return campaign;
  }

  private User user() {
    Tenant tenant = new Tenant();
    tenant.setId(9012L);

    Group group = new Group();
    group.setId(5678L);

    User user = new User();
    user.setId(AGENT_ID);
    user.setUsername(AGENT_USERNAME);
    user.setFirstname("AGENTADI");
    user.setLastname("AGENTSOYADI");
    user.setSessionId(1000000000001L);
    user.setGroup(group);
    user.setTenant(tenant);
    return user;
  }

  private String truncateForMessage(String line) {
    return line.length() <= 160 ? line : line.substring(0, 160) + "…";
  }
}
