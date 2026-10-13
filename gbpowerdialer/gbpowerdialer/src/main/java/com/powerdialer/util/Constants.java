package com.powerdialer.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.Locale;

import java.text.ParseException;
import java.time.ZoneId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonValue;

public class Constants {

  public static final ZoneId ISTANBUL_ZONE = ZoneId.of("Europe/Istanbul");

  public static final String ROLE_USER = "ROLE_USER";
  public static final String ROLE_SUPERVISOR = "ROLE_SUPERVISOR";
  public static final String ROLE_ANONYMOUS = "ROLE_ANONYMOUS";
  public static final String ROLE_SUPERADMIN = "ROLE_SUPERADMIN";
  public static final String ROLE_TENANT_ADMIN = "ROLE_TENANT_ADMIN";
  public static final String ROLE_TEAMLEAD = "ROLE_TEAMLEAD";

  public static final String PERMISSION_VIEW_CAMPAIGN = "ViewCampaign";
  public static final String PERMISSION_MANAGE_CAMPAIGN = "ManageCampaign";
  public static final String PERMISSION_EDIT_CAMPAIGN = "EditCampaign";
  public static final String PERMISSION_EDIT_CAMPAIGN_TECH = "EditCampaignTech";
  public static final String PERMISSION_EDIT_CAMPAIGN_NON_TECH = "EditCampaignNonTech";
  public static final String PERMISSION_CREATE_CAMPAIGN = "CreateCampaign";
  public static final String PERMISSION_DIALER_ADMINISTRATION = "DialerAdministration";
  public static final String PERMISSION_VALUE_ALL = "ALL";

  public static final String ALLOW_PREVIEW_BEEP = "ALLOW_PREVIEW_BEEP";

  public static final int GRID_FILTER_STARTS_WITH = 2;
  public static final int GRID_FILTER_ENDS_WITH = 4;
  public static final int GRID_FILTER_EXACT = 8;
  public static final int GRID_FILTER_CONTAINS = 16;
  public static final int GRID_FILTER_GREATER_THAN = 32;
  public static final int GRID_FILTER_GREATER_THAN_OR_EQUAL = 64;
  public static final int GRID_FILTER_LESS_THAN = 128;
  public static final int GRID_FILTER_LESS_THAN_OR_EQUAL = 256;
  public static final int GRID_FILTER_NOT_EQUAL = 512;
  public static final int GRID_FILTER_IS_NULL = 1024;
  public static final int GRID_FILTER_IS_NOT_NULL = 2048;

  public static final String KEY = "key";
  public static final String VALUE = "value";

  public static final int FILTER_TEST_LIMIT = 10;

  public static final String APP_CONFIG = "appConfig";
  public static final String APP_CONFIG_APPLICATION_NAME = "application.name";
  public static final String APP_CONFIG_BUILD_DATE = "project.buildDate";
  public static final String APP_CONFIG_PROJECT_STAGE = "project.stage";
  public static final String APP_CONFIG_PROJECT_VERSION = "project.version";
  public static final String APP_LOCALE = "Europe/Kiev";
  public static final String APP_CONFIG_ONEACCESS_ENABLED = "oneaccess.enabled";
  public static final String APP_CONFIG_REST_FETCH_LIMIT = "rest.fetchLimit";

  public static final String DATA_FORMAT_WITH_DATE = "yyyy-MM-dd HH:mm:ss.SSS";
  public static final String DATA_FORMAT_WITHOUT_DATE = "HH:mm:ss.SSS";

  public static final String TELEPHONY_RESULT_CODES = "telephonyResultCodes";
  public static final int TELEPHONY_RC_NORMAL_CLEARING = 16;
  public static final int TELEPHONY_RC_ID_FILTERED_BY_DNC = 76;
  public static final int TELEPHONY_RC_ANSWERED_BUT_NO_MT_CONNECTION = 84;
  public static final int TELEPHONY_RC_BLOCKED_BY_LSC = 905;
  public static final int TELEPHONY_RC_ABANDONED_TIMEOUT = 906;
  public static final int TELEPHONY_RC_ORIGINATE_ACTION_ERROR = 907;
  public static final int TELEPHONY_RC_LSC_TIMEOUT = 908;
  public static final int TELEPHONE_RC_ORPHANED_CALL_TERMINATED = 909;
  public static final int TELEPHONE_RC_NO_CALLERID_AVAILABLE = 910;
  public static final int TELEPHONY_RC_CUSTOMER_HANGUP_BEFORE_AGENT = 911;
  public static final int TELEPHONY_RC_AMBIGUOUS_RECORD = 912;
  /**
   * FEAT-325470 — santralin ayrıştırdığı AMD (answering machine detection) sonuçlarının
   * {@code pd_telephony_rc.code} değerleri. Dialer haritası ve buradaki tüm kararlar
   * <b>code</b> ile verilir; kartta yazan 71/86/87/88 değerleri tablonun {@code id} kolonudur ve
   * ortamlar arası güvenilir değildir (IDENTITY). ITU cause 86/87/88 tabloda dolu olduğu için yeni
   * satırların code'u Q.850'de atanmamış 113/114/115 aralığından seçildi; bu değerler
   * {@link #ASTERISK_ERROR_CODES} ile kesişmez. İlk sürüm (16.09.2026) 913/914/915 kullanıyordu,
   * 05.10.2026 santral kararıyla değişti; migration script'i eski kodlu satırları yeni koda taşır.
   *
   * <p>AMD busy (code 17 → id 11) ve AMD no answer (code 19 → id 13) mevcut ITU satırlarıyla
   * gelir; onlar için sabit gerekmez.</p>
   */
  public static final int TELEPHONY_RC_AMD = 900;
  public static final int TELEPHONY_RC_AMD_BAD_NUMBER = 113;
  public static final int TELEPHONY_RC_AMD_NOT_REACHED = 114;
  public static final int TELEPHONY_RC_AMD_UNKNOWN_TONE = 115;
  public static final Integer TELEPHONE_RC_CAMPAIGN_IS_STOPPED_FORCEFULLY = 902;
  public static final int HANGUP_CAUSE_NO_CIRCUIT_AVAILABLE = 34;

  /**
   * Task 325559 — kaydın numara başına azami arama limiti nedeniyle kapatıldığını anlatan
   * {@code close_reason} değerleri. Kampanya result tablosundaki
   * {@code col_result_close_reason} kolonuna ham metin olarak yazılır; lokalize EDİLMEZ.
   *
   * <p>Bu değerler yalnız limit kaynaklı kapanışlarda yazılır. Müşteri yanıtı sonrası oluşan
   * başarılı kapanışta (bkz. {@code RetryStrategy.createRetry}) kapanış nedeni YAZILMAZ; aksi
   * halde rapor "limit doldu" gibi okunur.</p>
   */
  public static final String CLOSE_REASON_NUMBER_LIMIT_REACHED = "closing for reached number limit";
  public static final String CLOSE_REASON_MAX_ATTEMPT_ON_CALLBACK = "max attempt reached on callback";
  public static final String CLOSE_REASON_NO_CALLABLE_NUMBER = "no callable number available";
  /**
   * FEAT-325470 — aranan numara "kullanılmayan numara" (AMD bad number, code 113) sonucu aldı ve
   * kayıtta aranabilir başka numara kalmadı; kayıt retry kurulmadan kapatıldı. Yalnız
   * {@code col_result_close_reason} binding'i eşliyse yazılır (325559 deseni).
   */
  public static final String CLOSE_REASON_ALL_NUMBERS_UNREACHABLE = "all numbers unreachable";

  /**
   * Webphone'dan agent result code geldiğinde, PD-side bu TRC id'leriyle yanlış pozitif başarısız
   * sayılmış olabilen çağrılar için "başarılıya hizalama" adayıdır. Asterisk ve webphone tarafında
   * authoritative bridge kanıtı doğrulandığında {@code pd_cdr} ve campaign result tabloları
   * mevcut başarılı kapatma persist akışı ile yeniden yazılır.
   */
  public static final Set<Long> WEBPHONE_RESULT_CODE_RECOVERABLE_TRC_IDS =
      Collections.unmodifiableSet(new HashSet<>(Arrays.asList(75L, 78L, 84L)));

  public static final long USER_RC_CAMPAIGN_IS_STOPPED_FORCEFULLY_ID = -2L;

  public static final int CALL_EXPIRE_TIMEOUT = 3600000;
  public static final int ORPHANED_CALL_EXPIRE_TIMEOUT = 900000;
  public static final int AWAITING_USER_RESULT_EXPIRE_TIMEOUT = 900000;
  public static final int CALLBACK_EXPIRE_TIMEOUT = 60000;
  // FIX-GLB90048674 (Faz 1): AMI reader thread'inden devralınan event/callback işini
  // işleyen decorator başına tek-thread'li worker'ın bounded kuyruk kapasitesi. Dolarsa
  // blocking-put devreye girer (task düşmez, sıra korunur); kapasite worst-case backlog'u
  // soğurmak için büyük tutulur.
  public static final int AMI_EVENT_WORKER_QUEUE_CAPACITY = 50000;
  public static final String LSC_EXPRESSION_PATTERN = "[{].+?[}]";
  public static final String VALIDATOR_EXPRESSION_PATTERN = "[{].+?[}]";
  public static final String COMMA_SEPARATED_NUMBERS_PATTERN = "^\\d+(,\\d+)*$";
  public static final Pattern DOUBLE_BRACES_PATTERN = Pattern.compile("\\{\\{(.*?)\\}\\}");

  public static final String REDIS_PING_MESSAGE = "ping";
  public static final String REDIS_CONFIGURATION_TYPE_CLUSTER = "cluster";

  public static final String COL_RESULT_NUMBER_SEPERATOR = ";";

  /* Cipher Algorithm and Secret key algorithm */
  public static final String CIPHER_ALGORITHM = "AES/CBC/PKCS5PADDING";
  public static final String SECRET_KEY_ALGORITHM = "AES";

  public static final String SORT_BY_MULTIPLE = "{MULTIPLE_SORT}";
  public static final String PRIORITY_TABLE_PREFIX = "pd_multiple_sort_";
  public static final int MAX_PRIORITY_COLUMNS = 20;
  public static final int MIN_PRIORITY_COLUMN_INDEX = 1;
  public static final String API_TOKEN_HEADER = "X-Campaign-Token";
  public static final String FILTER_FIELD_NAME = "Filter";

  public static final String CAMPAIGNS_CAMPAIGN_ID_KEY = "campaigns.campaign.id";
  public static final String CAMPAIGN_ID_KEY = "campaign.id";

  public static final String RECORDS = "Records";
  public static final String UserRC = "UserRC";
  public static final String TelephonyRC = "TelephonyRC";
  public static final int DISABLED = 1;
  public enum REDIS_CHANNEL {
    channelGBWebPhone,
    channelGBPowerDialer,
    channelUserCurrentStatus,
    channelUserSession,
    channelUserPause,
    channelGBPowerDialerEntity,
    channelQssConfiguration,
    channelCallEvent,
    channelPredictiveEvent,
    channelGBInteractionNotifier
  }

  public enum MANAGER_ACTION_VARIABLE {
                                       ORIGIN_ID("ORIGINID"),
                                       PARENT_ID("PARENTID"),
                                       CALLER_ID("DIALER_CALLERID"),
                                       AMD_ENABLED("AMD_ENABLED"),
                                       CONTACT_ID("CONTACTID"),
                                       CAMPAIGN_ID("CAMPAIGNID"),
                                       CAMPAIGN_NAME("CAMPAIGN_NAME"),
                                       ATTACHED_DATA("ATTACHED_DATA"),
                                       USER_PEER("USER_PEER"),
                                       DIALED_NUMBER("DIALED_NUMBER"),
                                       AGI_SERVER_IP("AGI_SERVER_IP"),
                                       CLIENT_DIAL_TIMEOUT("CLIENT_DIAL_TIMEOUT"),
                                       AGENT_ID("AGENT_ID"),
                                       AGENT_CURRENT_GROUP_ID("AGENT_CURRENT_GROUP_ID"),
                                       AGENT_SESSION_ID("AGENT_SESSION_ID"),
                                       AGENT_TENANT_ID("AGENT_TENANT_ID"),
                                       CAMPAIGN_TYPE("CAMPAIGN_TYPE"),
                                       ALLOW_CALLBACK("ALLOW_CALLBACK"),
                                       ALLOW_CUSTOM_CALLBACK("ALLOW_CUSTOM_CALLBACK"),
                                       RESULT_CODE_TIMER_STATUS("RESULT_CODE_TIMER_STATUS"),
                                       RESULT_CODE_TIMER("RESULT_CODE_TIMER");

    private final String param;

    MANAGER_ACTION_VARIABLE(String param) {
      this.param = param;
    }

    public String getParam() {
      return param;
    }

  }

  public enum CDR_VARIABLE {

                            AGENT_ID("agentid"),
                            GROUP_ID("groupid"),
                            TEAM_ID("teamid"),
                            SESSION_ID("sessionid"),
                            TENANT_ID("tenantid"),
                            PARENT_ID("parentid");

    private final String param;

    CDR_VARIABLE(String param) {
      this.param = param;
    }

    public String getParam() {
      return param;
    }

  }

  public enum DNC_PN_BATCH_PROCESSING {

    DELETE("delete"),
    ENABLE("enable"),
    DISABLE("disable");

    private final String type;

    DNC_PN_BATCH_PROCESSING(String type) {
      this.type = type;
    }

    public String getParam() {
      return type;
    }

  }

  public enum UPDATE_STRATEGY {
                               INSERT,
                               UPDATE
  }

  public enum ERROR_RESULT_CODES {
                                  CALL_EXPIRED_CLEARING(901),
                                  CAMPAIGN_STOPPED_FORCEFULLY(902),
                                  AMI_CONNECTION_LOSS(903),
                                  ASTERISK_INTERNAL_ERROR(0),
                                  UNALLOCATED_NUMBER_ERROR(1);

    private int code;

    ERROR_RESULT_CODES(int code) {
      this.code = code;
    }

    public int getCode() {
      return code;
    }

  }

  public static final Set<Integer> TELEPHONY_RESULT_CODES_EXCLUDED_FROM_ATTEMPTS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
      TELEPHONY_RC_ORIGINATE_ACTION_ERROR
  )));

  public static boolean isTelephonyResultCodeExcludedFromAttempts(Integer code) {
    return code != null && TELEPHONY_RESULT_CODES_EXCLUDED_FROM_ATTEMPTS.contains(code);
  }

  /**
   * FEAT-325470 — "AMD ailesi": müşteri bacağı bir makine/anons/ton tarafından karşılanmış, insan
   * bağlantısı hiç kurulmamış sonuçlar. Hangup yolunda üçü de aynı muameleyi görür: agent'ın
   * result code'u beklenmez (awaiting-URC'ye düşmez), TRC 84 "customer hangup before agent"
   * override'ından muaftır ve predictive'de NO_CONNECTION sayılır. Eski kod bu kararı yalnız
   * id 71 (code 900) için ve id ile veriyordu; küme code tabanlıdır ve yeni kodları kapsar.
   *
   * <p>ITU code 71 (id 83 "AMD" adlı eski satır) bilinçli olarak bu kümede DEĞİLDİR; eski kod da
   * onu AMD saymıyordu.</p>
   */
  public static final Set<Integer> AMD_FAMILY_TELEPHONY_RC_CODES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
      TELEPHONY_RC_AMD,
      TELEPHONY_RC_AMD_BAD_NUMBER,
      TELEPHONY_RC_AMD_NOT_REACHED,
      TELEPHONY_RC_AMD_UNKNOWN_TONE
  )));

  public static boolean isAmdFamilyTelephonyResultCode(Integer code) {
    return code != null && AMD_FAMILY_TELEPHONY_RC_CODES.contains(code);
  }

  /**
   * Hangup'ın ÜZERİNE YAZABİLECEĞİ dialer iç hata kodları. {@link #ASTERISK_ERROR_CODES} yalnız
   * ITU hangup cause'larını (0..127) tutar; 907 orada bulunmadığı için {@code hasErrorCode} onu
   * hata saymıyor ve {@code shouldFinalizeOnHangup} hangup'ın gerçek cause ile düzeltmesine izin
   * vermiyordu. Bu küme yalnız o boşluğu kapatır.
   *
   * <p><b>Neden sadece 907:</b> Bu kod, kanalı KURULMUŞ bir çağrıya yazılabildiği için sonradan
   * gelen gerçek hangup ile düzeltilmesi gerekir. 905 (BLOCKED_BY_LSC) ve 908 (LSC_TIMEOUT)
   * {@code CallOriginator.applyLSCAndOriginate} içinde originate HİÇ YAPILMADAN
   * {@code cancelCall} ile set edilir; kanal açılmaz, dolayısıyla o çağrılara hangup event'i hiç
   * gelmez ve buraya eklenmeleri gereksizdir. 906 (ABANDONED_TIMEOUT) ise
   * {@code resolveHangupTelephonyResultCode} içinde hangup'ın KENDİSİ tarafından
   * {@code call.isAbandoned()} dalıyla üretilir; onu "hangup ile düzeltilebilir" saymak
   * abandoned sonucunu kendi üzerine yeniden yazma riski doğurur.</p>
   */
  public static final Set<Integer> HANGUP_OVERWRITABLE_DIALER_ERROR_CODES =
      Collections.unmodifiableSet(new HashSet<>(Arrays.asList(TELEPHONY_RC_ORIGINATE_ACTION_ERROR)));

  public static final Set<Integer> ASTERISK_ERROR_CODES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(0,
      1,
      2,
      3,
      6,
      25,
      27,
      28,
      29,
      34,
      38,
      41,
      42,
      44,
      46,
      47,
      50,
      57,
      58,
      65,
      66,
      69,
      82,
      88,
      91,
      95,
      111,
      127)));

    /**
     * Gamma {@code avgTalk} penceresine giren telephony result code'lar (pd_telephony_rc.id).
     *
     * <p>Tanim: "agent GERCEKTEN gorustu ve cagri normal kapandi". Bu kume yalniz Gamma'nin
     * basarili cagri penceresini ve onun startup karsiligini besler; baska hicbir yerde
     * kullanilmaz. {@link #RING_VALID_RESULT_CODES} ile karistirilmamalidir.
     *
     * <ul>
     *   <li><b>10</b> = Normal call clearing</li>
     *   <li><b>85</b> = Ambiguous / Belirsiz Kayit: gorusme gerceklesti, agent sonuc kodu
     *       (URC) girmedi ve bekleme penceresi doldu (bkz.
     *       {@code ManagerConnectionDecorator.applyAmbiguousTrcForMissingUserResultCode}).</li>
     * </ul>
     *
     * <p><b>85 neden dahil:</b> {@code avgTalk} bir TELEFON olcumudur — kaynagi
     * {@code call_handling_start}/{@code call_end_time} AMI damgalaridir ve URC'den bagimsizdir.
     * TRC 85'te eksik olan tek sey IS verisidir; agent yine de o sure boyunca mesguldu, yani
     * kapasite acisindan gecerli bir gozlemdir. Disarida birakmak pencereyi "agent'in kod
     * girmeye usenmedigi cagrilar" ile sinirlar; bu bir SECIM YANLILIGIDIR ve yonu veriye
     * gore degisir (olculen: camp 64'te 85'ler daha KISA 78 vs 172 sn, camp 113'te daha
     * UZUN 169 vs 106 sn). avgTalk paydada oldugu icin sapma dogrudan arza yansir.
     *
     * <p><b>Neden 78/83 yok:</b> onlarda agent gorusmesi HIC olmamistir (78 abandon,
     * 83 AMD); bu kumenin disinda kalmalari dogrudur.
     *
     * <p><b>ONEMLI — iki tuketici birlikte degismelidir:</b> canli pencere kapisi
     * ({@code PredictiveGammaCallManager}) ve dunun ortalamasini ureten SQL
     * ({@code CDRDao.findGammaStartupAverageTalkTime}). T2 kademeli gecisi bu ikisini ayni
     * formulde harmanladigi icin, yalniz biri degisirse gun icinde TANIM kayar ve model bunu
     * gercek bir dusus sanip arzi artirir. Ikisi de bu sabitten beslendigi surece ayrisamazlar.
     */
    public static final Set<Integer> GAMMA_TALK_VALID_RESULT_CODES =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(10, 85)));

    public static final Set<Integer> RING_VALID_RESULT_CODES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            7,  // No answer - Call awarded and being delivered in an established channel
            8,  // User Busy - Preemption
            9,  // User Busy - Preemption – circuit reserved for reuse
            10,	// Answered - Normal call clearing
            11, // User Busy - User busy
            12, // No answer - No user responding
            13, // No answer - No answer from user (user alerted)
            14, // No answer - Subscriber absent
            15, // User Busy - Call rejected
            17, // User Busy - Redirection to new destination
            19, // User Busy - Non-selected user clearing
            23, // User Busy - Response to STATUS ENQUIRY
            24	//Normal, unspecified
    )));

    /**
     * "Fiilen caldi" kumesinin olculmus (DB dogrulamali) hali. YALNIZ Gamma Plus yolunda,
     * yalnız AR_ring (ringingLoad carpani) hesabinda kullanilir.
     *
     * <p><b>Neden ayri liste:</b> {@link #RING_VALID_RESULT_CODES} ayni zamanda
     * {@code recentRingCalls} kapisini ve {@code CDRDao} startup sorgularini besliyor;
     * icerigi degistirilirse {@code avgRing} bazi kampanyalarda %30'a varan oynar ve
     * Gamma Plus bayragi KAPALI kampanyalar da etkilenirdi. Bu yuzden mevcut liste
     * aynen korunur, dogru tanim yanina ikinci sabit olarak eklenir.
     *
     * <p>Fark (Tcell pd_cdr, 01-16.06.2026, 1.474.891 cagri, fiziksel hat suresi olcumu):
     * <ul>
     *   <li>EKLENDI: 71 (AMD, hacmin %10,5'i, p50 7 sn), 25 (60 sn), 75 (37 sn),
     *       78 (34 sn), 84 (25 sn) — hepsi fiilen caliyor.</li>
     *   <li>CIKARILDI: 14 (Subscriber absent, p50 0 sn, 15 gunde 2 satir) — calmiyor.</li>
     *   <li>BIRAKILDI: 7, 8, 9, 12, 17, 19, 23, 24 — veride hic gorunmuyor; cikarmak
     *       gereksiz risk.</li>
     *   <li>GIRMEDI: 1 (1 sn), 26 (2 sn), 22 (6 sn, karisik), 76/77/80 (hic aranmadi).</li>
     * </ul>
     */
    public static final Set<Integer> RING_VALID_RESULT_CODES_V2 = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            7,  // No answer - Call awarded and being delivered in an established channel
            8,  // User Busy - Preemption
            9,  // User Busy - Preemption – circuit reserved for reuse
            10, // Answered - Normal call clearing
            11, // User Busy - User busy
            12, // No answer - No user responding
            13, // No answer - No answer from user (user alerted)
            15, // User Busy - Call rejected
            17, // User Busy - Redirection to new destination
            19, // User Busy - Non-selected user clearing
            23, // User Busy - Response to STATUS ENQUIRY
            24, // Normal, unspecified
            25, // No circuit available (p50 60 sn)
            71, // AMD (p50 7 sn)
            75, // AMI connection loss (p50 37 sn)
            78, // Abandon timeout exceed (p50 34 sn)
            84  // Customer hangup before agent (p50 25 sn)
    )));

  public enum FILTER_TYPE {
                           PREDEFINED,
                           FREE
  }

  public enum FILTER_TARGET_TABLE {
                                   SOURCE_TABLE,
                                   RESULT_TABLE
  }



  public enum FILTER_CONDITION {
                                EQUAL("="),
                                NOTEQUAL("!="),
                                GREATER(">"),
                                LESS("<"),
                                GREATEROREQUAL(">="),
                                LESSOREQUAL("<="),
                                LIKE("like"),
                                NOTLIKE("not like"),
                                IN("in"),
                                NOTIN("not in"),
                                ISNULL("is null"),
                                ISNOTNULL("is not null");



      private String condition;
    FILTER_CONDITION(String condition) {
      this.condition = condition;
    }
    public String condition() { return condition; }
    public static FILTER_CONDITION parse(String s) throws ParseException {
      if (s == null) {
        throw new ParseException("Can't parse FILTER_CONDITION from null", 0);
      }
      final String token = normalizeOperatorToken(s);
      switch (token) {
      case "=":
        return EQUAL;
      case "!=":
        return NOTEQUAL;
      case "<>":
        return NOTEQUAL;
      case ">":
        return GREATER;
      case "<":
        return LESS;
      case ">=":
        return GREATEROREQUAL;
      case "<=":
        return LESSOREQUAL;
      case "like":
        return LIKE;
      case "not like":
        return NOTLIKE;
      case "in":
        return IN;
      case "not in":
        return NOTIN;
      case "is null":
        return ISNULL;
      case "is not null":
        return ISNOTNULL;
      default:
        throw new ParseException("Can't parse FILTER_CONDITION from string: " + s, 0);
      }
    }
      private static String normalizeOperatorToken(String raw) {
          String t = raw.trim();
          t = t.replace("_", " ");
          // Collapse multiple spaces (e.g., "NOT    IN" -> "NOT IN")
          t = t.replaceAll("\\s+", " ");
          // Case-insensitive parsing
          t = t.toLowerCase(Locale.ROOT);
          // Synonyms / normalization
          if ("==".equals(t)) return "=";
          if ("<>".equals(t)) return "!=";
          if ("isnull".equals(t)) return "is null";
          if ("isnotnull".equals(t)) return "is not null";
          return t;
      }
      public boolean isOrderedCompare() {
          switch (this) {
              case GREATER:
              case LESS:
              case GREATEROREQUAL:
              case LESSOREQUAL:
                  return true;
              default:
                  return false;
          }
      }

      public boolean isDateAllowed() {
          switch (this) {
              case EQUAL:
              case NOTEQUAL:
              case GREATER:
              case LESS:
              case GREATEROREQUAL:
              case LESSOREQUAL:
                  return true;
              default:
                  return false;
          }

      }

      public boolean isNullCheck() {
          return this == ISNULL || this == ISNOTNULL;
      }

    @Override
    public String toString() {
      return condition;
    }
  }

  public enum FILTER_JOIN_TYPE {
                                AND("AND"),
                                OR("OR");

    private String joinType;

    FILTER_JOIN_TYPE(String joinType) {
      this.joinType = joinType;
    }

    @Override
    public String toString() {
      return joinType;
    }
  }

  public enum DB_SORT_DIRECTION {
                                 ASC("ASC"),
                                 DESC("DESC");

    private String sortDirection;

    DB_SORT_DIRECTION(String sortDirection) {
      this.sortDirection = sortDirection;
    }

    @Override
    public String toString() {
      return sortDirection;
    }
  }

  public enum CAMPAIGN_ACTION {
                               START,
                               STOP_REGULAR,
                               STOP_FORCEFUL,
                               PAUSE,
                               UNPAUSE,
                               RELOAD,
                               RESET,
                               RESTART
  }

  @JsonFormat(shape = JsonFormat.Shape.NUMBER_INT)
  public enum CAMPAIGN_STATE {
                              STOPPED,
                              STOPPING,
                              STARTING,
                              RUNNING,
                              PAUSING,
                              PAUSED,
                              RELOADING,
                              RESETTING
  }

  @JsonFormat(shape = JsonFormat.Shape.NUMBER_INT)
  public enum CAMPAIGN_TYPE {
                             PROGRESSIVE_RESERVED,
                             PROGRESSIVE_RELEASED,
                             PREVIEW_FREE,
                             PREVIEW_STRAIGHT,
                             OUTBOUND_IVR,
                             PREDICTIVE
  }

  @JsonFormat(shape = JsonFormat.Shape.NUMBER_INT)
  public enum END_LIST_BEHAVIOUR {
                                  CONTINUE,
                                  STOP_DISCARDING_RETRIES_AND_CALLBACKS,
                                  STOP_ACCELERATING_RETRIES_AND_CALLBACKS
  }

  @JsonFormat(shape = JsonFormat.Shape.NUMBER_INT)
  public enum RETRY_STRATEGY {
                              REACH_RETRY_LIMIT_FOR_SINGLE_NUMBER,
                              REACH_RETRY_LIMIT_BEFORE_NEXT_NUMBER,
                              ROUND_ROBIN_DIAL_WITH_OVERALL_RETRY_LIMIT_SCHEDULE_NEXT_NUMBER,
                              ROUND_ROBIN_DIAL_WITH_OVERALL_RETRY_LIMIT_SCHEDULE_NEXT_CYCLE,
                              ROUND_ROBIN_DIAL_WITH_PER_CYCLE_RETRY_LIMIT_SCHEDULE_NEXT_NUMBER,
                              RETRY_RULES_MANAGES_CYCLE
  }

  /**
   * Callback to Appointer sıfırlama stratejisi (task 321997).
   * TIMER: legacy davranış — appointerReset/appointerResetTimeout ile zamana dayalı sıfırlama.
   * ATTEMPT_COUNT: sahip müsait değilken reschedule + deneme sayacı; limitte campaign-wide'a devir.
   * Ordinal ile saklanır (TIMER=0, ATTEMPT_COUNT=1).
   */
  @JsonFormat(shape = JsonFormat.Shape.NUMBER_INT)
  public enum APPOINTER_RESET_STRATEGY {
                                        TIMER,
                                        ATTEMPT_COUNT
  }

  @JsonFormat(shape = JsonFormat.Shape.NUMBER_INT)
  public enum CALLER_ID_BEHAVIOUR {
                                   FIXED,
                                   DYNAMIC,
                                   DIAL_PLAN_DEFINED,
                                   POOL
  }

  public enum PREDICTIVE_STRATEGY {
        ALPHA(0, "ALPHA"),
        BETA(1, "BETA"),
        GAMMA(2, "GAMMA");

        private final int code;
        private final String value;

        PREDICTIVE_STRATEGY(int code, String value) {
            this.code = code;
            this.value = value;
        }

        @JsonValue
        public int getCode() {
            return code;
        }

        public String getValue() {
            return value;
        }

        @JsonCreator
        public static PREDICTIVE_STRATEGY fromValue(Object input) {
            if (input instanceof Integer) {
                for (PREDICTIVE_STRATEGY s : values()) {
                    if (s.code == (Integer) input) return s;
                }
            } else if (input instanceof String) {
                for (PREDICTIVE_STRATEGY s : values()) {
                    if (s.name().equalsIgnoreCase((String) input)) return s;
                }
            }
            throw new IllegalArgumentException("Invalid strategy: " + input);
        }
  }

  @JsonFormat(shape = JsonFormat.Shape.NUMBER_INT)
  public enum TIMEZONE_TYPE {
                             OFF,
                             SIMPLE,
                             ADVANCED
  }

  public enum FEEDBACK_TYPE {
                             ERROR,
                             SUGGESTION,
                             COMMENT,
                             LOGOUT
  }

  public enum FEEDBACK_SOURCE {
                               WBP,
                               REC,
                               DSH,
                               DLR;

    public static String getNameByCode(int code) {
      for (FEEDBACK_SOURCE e : FEEDBACK_SOURCE.values()) {
        if (code == e.ordinal()) {
          return e.name();
        }
      }
      return null;
    }
  }

  public enum SERVER_TYPE {
                           REGISTRAR,
                           MEDIA,
                           CLUSTER,
                           GATE
  }

  @JsonFormat(shape = JsonFormat.Shape.NUMBER_INT)
  public enum REPLY_TYPE {
                          JSON,
                          XML,
                          PLAIN_TEXT
  }

  @JsonFormat(shape = JsonFormat.Shape.STRING)
  public enum HTTP_METHOD {
                           POST,
                           GET
  }

  public enum DB_TYPE {
                       MS_SQL("com.microsoft.sqlserver.jdbc.SQLServerDriver", 128),
                       MYSQL("com.mysql.jdbc.Driver", 64),
                       ORACLE("oracle.jdbc.OracleDriver", 30),
                       POSTGRE_SQL("org.postgresql.Driver", 64);

    private final String driver;
    private final int maxKeyLength;

    DB_TYPE(String driver, int maxKeyLength) {
      this.driver = driver;
      this.maxKeyLength = maxKeyLength;
    }

    public static DB_TYPE getByConnectionString(String url) {
      if (url.contains("sqlserver")) {
        return MS_SQL;
      } else if (url.contains("oracle")) {
        return ORACLE;
      } else if (url.contains("mysql")) {
        return MYSQL;
      } else if (url.contains("postgresql")) {
        return POSTGRE_SQL;
      } else {
        return null;
      }
    }

    public String getDriver() {
      return driver;
    }

    public int getMaxKeyLength() {
      return maxKeyLength;
    }
  }

  @JsonFormat(shape = JsonFormat.Shape.NUMBER_INT)
  public enum LOG_LEVEL {
                         ERROR,
                         WARNING,
                         INFO
  }

  @JsonFormat(shape = JsonFormat.Shape.NUMBER_INT)
  public enum LOG_MODULE {
                          CAMPAIGN,
                          LSC,
                          DNC,
                          ASTERISK,
                          DB,
                          DATA_MANIPULATION,
                          PUZZLE
  }

  @JsonFormat(shape = JsonFormat.Shape.NUMBER_INT)
  public enum ACTION_TYPE {
                           CREATE,
                           UPDATE,
                           DELETE;

    public static String getNameByOrdinal(int code) {
      for (ACTION_TYPE e : ACTION_TYPE.values()) {
        if (code == e.ordinal()) {
          return e.name();
        }
      }
      return null;
    }
  }

  @JsonFormat(shape = JsonFormat.Shape.NUMBER_INT)
  public enum ACTION_TARGET {
                             CAMPAIGN,
                             LSC,
                             DNC;

    public static String getNameByOrdinal(int code) {
      for (ACTION_TARGET e : ACTION_TARGET.values()) {
        if (code == e.ordinal()) {
          return e.name();
        }
      }
      return null;
    }
  }

  @JsonFormat(shape = JsonFormat.Shape.NUMBER_INT)
  public enum REASON {
                      ADMINISTRATIVE,
                      WRAPUP_CODE
  }

  /**
   * DNC engelleme kapsamı (scope): agent/Puzzle'ın seçtiği engelleme tipi.
   * Redis contract'ında CallResultCode.dncScope alanının tipidir; wire değeri
   * sabit isimlerdir (PHONE / CUSTOMER) ve değişmez. PHONE: yalnız aranan
   * tekil numara engellenir. CUSTOMER: müşterinin tüm numaraları engellenir
   * (customer-scope; pd_dnc_customer ilişki tablosu + pd_dnc_pn genişletmesi).
   */
  public enum DNC_SCOPE {
                        PHONE,
                        CUSTOMER
  }

  @JsonFormat(shape = JsonFormat.Shape.NUMBER_INT)
  public enum APPLY_TO {
                        RETRIES,
                        CALLBACKS,
                        RETRIES_AND_CALLBACKS
  }

  @JsonFormat(shape = JsonFormat.Shape.NUMBER_INT)
  public enum NUMBER_TO_TRY {
                             PREVIOUS,
                             SAME,
                             NEXT
  }

  @JsonFormat(shape = JsonFormat.Shape.NUMBER_INT)
  public enum DAY_OF_WEEK {
                           MON,
                           TUE,
                           WED,
                           THU,
                           FRI,
                           SAT,
                           SUN;

    /**
     * Get DAY_OF_WEEK by day of Calendar.DAY_OF_WEEK
     * @param day
     * @return DAY_OF_WEEK
     */
    public static DAY_OF_WEEK intToDayOfWeek(int day) {
      if (day >= 1 && day <= 7)
        return day == 1 ? DAY_OF_WEEK.MON
                        : day == 2 ? DAY_OF_WEEK.TUE
                                   : day == 3 ? DAY_OF_WEEK.WED : day == 4 ? DAY_OF_WEEK.THU : day == 5 ? DAY_OF_WEEK.FRI : day == 6 ? DAY_OF_WEEK.SAT : DAY_OF_WEEK.SUN;
      else
        throw new IllegalArgumentException("Value of day must be in 1-7");
    }
  }

  @JsonFormat(shape = JsonFormat.Shape.NUMBER_INT)
  public enum NUMBER_TYPE {
                           INBOUND,
                           OUTBOUND
  }

  /**
   * Action types that are received from redis (Entity modifications)
   */
  public enum MSG_ACTION_TYPE {
                               ADD,
                               EDIT,
                               DELETE,
                               STATUS_UPDATE,
                               LOGIN,
                               LOGOUT,
                               ONLINE_USER_SYNCHRONIZATION,
                               ORIGINATE_CALL,
                               ORIGINATE_ACTION_ERROR,
                               PING
  }

  /**
   * Entity types that are being modified and as a redis message from webphone
   */
  public enum MSG_ENTITY_TYPE {
                               TENANT,
                               USER,
                               QUEUE,
                               IVR_APPLICATION,
                               GROUP,
                               RESULT_CODE,
                               SERVER,
                               ORIGINATE_ACTION_WRAPPER,
                               OTHER,
                               CAMPAIGN,
                               PREDICTIVE_ALPHA,
                               PREDICTIVE_BETA,
                               CONTACT_ANOMALY_REPORT
  }

  /**
   * System statuses
   */
  public enum SYSTEM_STATUS {
                             AVAILABLE(9999L, true),
                             RINGING(9998L, false),
                             INCALL(9997L, false),
                             ONHOLD(9996L, false),
                             WRAPUP(9995L, false),
                             FINISHED(9994L, false),
                             CONFERENCE(9993L, false),
                             MUTED(9992L, false),
                             LISTENING(9991L, false),
                             WHISPERING(9990L, false),
                             BARGED(9989L, false),
                             PREVIEW(9988L, false),
                             RELAX(9987L, false);

    private Long id;
    private boolean availableForDialerCalls;

    SYSTEM_STATUS(Long id, boolean availableForDialerCalls) {
      this.id = id;
      this.availableForDialerCalls = availableForDialerCalls;
    }

    public Long getId() {
      return id;
    }

    public boolean isAvailableForDialerCalls() {
      return availableForDialerCalls;
    }

  }

  public enum SYSTEM_RESULT_CODE {
                                  DONT_CALL_CAMPAIGN(9989L),
                                  DONT_CALL_TENANT(9990L);

    SYSTEM_RESULT_CODE(Long id) {
      this.id = id;
    }

    private final Long id;

    public Long getId() {
      return id;
    }
  }

  public enum DEFAULT_CAMPAIGN_COLUMN_NAMES {
                                             // source columns
                                             ID("id"),
                                             RANDOM_ID("random_id"),
                                             CALLER_ID("caller_id"),
                                             PHONE_NUMBER("phone_number"),
                                             CALL_FROM_TIME("call_from_time"),
                                             CALL_TILL_TIME("call_till_time"),
                                             TIMEZONE_OFFSET("timezone_offset"),
                                             SPECIFIC_SKILL_NAME("specific_skill_name"),
                                             // result table columns
                                             UNIQUE_KEY("unique_key"),
                                             START_DATE("start_date"),
                                             ANSWER_DATE("answer_date"),
                                             COMPLETE_DATE("complete_date"),
                                             CALLED_NUMBER("called_number"),
                                             TELEPHONY_RC("telephony_result_code"),
                                             USER_RC("user_result_code"),
                                             RC_SEQUENCE("rc_sequence"),
                                             USER_ID("user_id"),
                                             CALLBACK_NUMBER("callback_number"),
                                             CALLBACK_DATE("callback_date"),
                                             RETRY_NUMBER("retry_number"),
                                             RETRY_HISTORY("retry_history"),
                                             CB_HISTORY("cb_history"),
                                             CALL_PROCESS("call_process"),
                                             RETRY_DATE("retry_date"),
                                             FETCHED_DATA_FLAG("is_fetched"),
                                             DNC_NUMBERS("dnc_numbers"),
                                             PRIORITY("priority_"),
                                             ATTEMPTS("attempts"),
                                             CALLBACK_ATTEMPTS("callback_attempts"),
                                             UPDATE_DATE("update_date"),
                                             RESPONSE_VALUES("response_values"),
                                             CALLBACK_TYPE("callback_type"),
                                             CUSTOMER_NAME("Customer_Name"),
                                             CUSTOMER_SURNAME("Customer_Surname"),
                                             RELATION_TYPE("relation_type"),
                                             RELATION("relation_type_text"),
                                             CALLED_GROUP_ID("called_group_id"),
                                             DISABLED("is_disabled"),
                                             CUSTOMER_NO("customer_no"),
                                             CLOSE_REASON("close_reason"),
                                             DATE_ENTERED("date_entered");

    DEFAULT_CAMPAIGN_COLUMN_NAMES(String name) {
      this.value = name;
    }

    private final String value;

    public String getValue() {
      return value;
    }

    /**
     * Get value optionally escaped with quote symbols
     * @param escapeValue
     * @return
     */
    public String getValue(boolean escapeValue) {
      if (escapeValue) {
        return "\"" + getValue() + "\"";
      } else {
        return getValue();
      }
    }
  }

  public static final Set<String> ALLOWED_NON_TECH_FIELDS;
  static {
    Set<String> tempSet = new HashSet<>();
    tempSet.add("id");
    tempSet.add("version");
    tempSet.add("campaignGroups");
    tempSet.add("skills");
    tempSet.add("skillEnabled");
    tempSet.add("colSkill");
    tempSet.add("filters");
    tempSet.add("filterJoinType");
    tempSet.add("applyFilterTo");
    tempSet.add("concurrentLines");
    tempSet.add("ringTimeout");
    tempSet.add("abandonTimeout");
    tempSet.add("dialingBoost");
    ALLOWED_NON_TECH_FIELDS = Collections.unmodifiableSet(tempSet);
  }

  public static final Set<String> ALLOWED_TECH_FIELDS;
  static {
    Set<String> tempSet = new HashSet<>(ALLOWED_NON_TECH_FIELDS);
    tempSet.add("callsPerSecond");
    tempSet.add("dialingBoost");
    tempSet.add("filters");
    tempSet.add("timezoneType");
    tempSet.add("colCallFromTime");
    tempSet.add("colCallToTime");
    tempSet.add("colTimezoneOffset");
    tempSet.add("retryOveralAttempt");
    tempSet.add("retryDelay");
    tempSet.add("retryRatio");
    tempSet.add("macroInterval");
    tempSet.add("macroMaxAttempt");
    tempSet.add("maxAttemptPerNumber");
    tempSet.add("schedules");
    tempSet.add("blackoutSchedules");
    tempSet.add("scheduleEnabled");
    tempSet.add("scheduleAutomations");
    tempSet.add("blackoutAutomationSchedules");
    tempSet.add("scheduleAutomationEnabled");
    tempSet.add("predefinedValues");
    ALLOWED_TECH_FIELDS = Collections.unmodifiableSet(tempSet);
  }

  public enum SIMPLE_DB_TYPES {
                               INTEGER,
                               DECIMAL,
                               DATE,
                               STRING,
                               OTHER
  }

  public enum CALL_TYPES {
                          RETRY,
                          FRESH,
                          CALLBACK
  }

  @JsonFormat(shape = JsonFormat.Shape.NUMBER_INT)
  public enum INPUT_VALUE_TYPE {
                                CONCURRENT_LINES_MAX(0),
                                CONCURRENT_LINES_MIN(1),
                                ABANDON_TIMEOUT_MAX(2),
                                ABANDON_TIMEOUT_MIN(3),
                                RING_TIMEOUT_MAX(4),
                                RING_TIMEOUT_MIN(5),
                                DIALING_BOOST_MAX(6),
                                DIALING_BOOST_MIN(7);

    private final int type;

    INPUT_VALUE_TYPE(int type) {
      this.type = type;
    }

    public int getType() {
      return type;
    }
  }

  public enum DB_FIELD_TYPES {
                              // common types
                              FLOAT("FLOAT"),
                              DATE("DATE"),
                              CHAR("CHAR"),
                              NCHAR("NCHAR"),
                              BIGINT("BIGINT"),
                              SMALLINT("SMALLINT"),
                              DECIMAL("DECIMAL"),
                              INT("INT"),
                              TINYINT("TINYINT"),
                              DATETIME("DATETIME"),
                              TIME("TIME"),
                              VARCHAR("VARCHAR"),
                              TEXT("TEXT"),
                              TIMESTAMP("TIMESTAMP"),
                              BLOB("BLOB"),
                              // SQL Server types
                              NUMERIC("NUMERIC"),
                              BIT("BIT"),
                              VARBINARY("varbinary"),
                              SMALLMONEY("SMALLMONEY"),
                              MONEY("MONEY"),
                              REAL("REAL"),
                              DATETIMEOFFSET("DATETIMEOFFSET"),
                              DATETIME2("DATETIME2"),
                              SMALLDATETIME("SMALLDATETIME"),
                              NVARCHAR("NVARCHAR"),
                              NTEXT("NTEXT"),
                              BINARY("BINARY"),
                              IMAGE("IMAGE"),
                              // oracle types
                              VARCHAR2("VARCHAR2"),
                              NVARCHAR2("NVARCHAR2"),
                              NUMBER("NUMBER"),
                              LONG("LONG"),
                              INTEGER("INTEGER"),
                              BINARY_FLOAT("BINARY_FLOAT"),
                              BINARY_DOUBLE("BINARY_DOUBLE"),
                              RAW("RAW"),
                              LONG_RAW("LONG"),
                              ROWID("ROWID"),
                              UROWID("UROWID"),
                              CLOB("CLOB"),
                              NCLOB("NCLOB"),
                              BFILE("BFILE"),
                              // My SQL types
                              MEDIUMINT("MEDIUMINT"),
                              DOUBLE("DOUBLE"),
                              YEAR("YEAR"),
                              TINYBLOB("TINYBLOB"),
                              TINYTEXT("TINYTEXT"),
                              MEDIUMBLOB("MEDIUMBLOB"),
                              MEDIUMTEXT("MEDIUMTEXT"),
                              LONGBLOB("LONGBLOB"),
                              LONGTEXT("LONGTEXT"),
                              ENUM("ENUM"),
                              //PostgeSQL
                              CHARACTER_VARYING("character varying"),
                              TIMESTAMP_WITHOUT_TIME_ZONE("timestamp without time zone"),
                              DOUBLE_PRECISION("double precision"),
                              UNIQUEIDENTIFIER("uniqueidentifier");

    private final String value;

    DB_FIELD_TYPES(String value) {
      this.value = value;
    }

    public static DB_FIELD_TYPES parse(String s) throws ParseException {
      for (DB_FIELD_TYPES value : values()) {
        if (value.toString().equalsIgnoreCase(s))
          return value;
      }
      throw new ParseException("Can't parse DB_FIELD_TYPES from string: " + s, 0);
    }

    @Override
    public String toString() {
      return value;
    }
  }

  public static final Set<DB_FIELD_TYPES> INTEGER_FIELD_TYPES = EnumSet
      .of(DB_FIELD_TYPES.BIGINT, DB_FIELD_TYPES.SMALLINT, DB_FIELD_TYPES.INT, DB_FIELD_TYPES.LONG, DB_FIELD_TYPES.INTEGER, DB_FIELD_TYPES.TINYINT, DB_FIELD_TYPES.MEDIUMINT);

  public static final Set<DB_FIELD_TYPES> DECIMAL_FIELD_TYPES = EnumSet
      .of(DB_FIELD_TYPES.FLOAT,
          DB_FIELD_TYPES.DECIMAL,
          DB_FIELD_TYPES.NUMERIC,
          DB_FIELD_TYPES.REAL,
          DB_FIELD_TYPES.NUMBER,
          DB_FIELD_TYPES.BINARY_FLOAT,
          DB_FIELD_TYPES.BINARY_DOUBLE,
          DB_FIELD_TYPES.DOUBLE,
          DB_FIELD_TYPES.DOUBLE_PRECISION);

  public static final Set<DB_FIELD_TYPES> STRING_FIELD_TYPES = EnumSet
      .of(DB_FIELD_TYPES.CHAR,
          DB_FIELD_TYPES.VARCHAR,
          DB_FIELD_TYPES.TEXT,
          DB_FIELD_TYPES.NCHAR,
          DB_FIELD_TYPES.NVARCHAR,
          DB_FIELD_TYPES.NTEXT,
          DB_FIELD_TYPES.VARCHAR2,
          DB_FIELD_TYPES.NVARCHAR2,
          DB_FIELD_TYPES.ROWID,
          DB_FIELD_TYPES.UROWID,
          DB_FIELD_TYPES.CLOB,
          DB_FIELD_TYPES.NCLOB,
          DB_FIELD_TYPES.TINYBLOB,
          DB_FIELD_TYPES.TINYTEXT,
          DB_FIELD_TYPES.MEDIUMBLOB,
          DB_FIELD_TYPES.MEDIUMTEXT,
          DB_FIELD_TYPES.LONGBLOB,
          DB_FIELD_TYPES.LONGTEXT,
          DB_FIELD_TYPES.CHARACTER_VARYING,
          DB_FIELD_TYPES.UNIQUEIDENTIFIER);

  public static final Set<DB_FIELD_TYPES> DATE_FIELD_TYPES = EnumSet.of(DB_FIELD_TYPES.DATE, DB_FIELD_TYPES.DATETIME);

  public static final Set<DB_FIELD_TYPES> QUOTED_FIELD_TYPES = EnumSet.of(DB_FIELD_TYPES.DATE, DB_FIELD_TYPES.DATETIME);
  static {
    QUOTED_FIELD_TYPES.addAll(STRING_FIELD_TYPES);
  }

  public static final Map<DEFAULT_CAMPAIGN_COLUMN_NAMES, Set<DB_FIELD_TYPES>> RESULT_FIELDS_ALLOWED_TYPES;

  static {
    Map<DEFAULT_CAMPAIGN_COLUMN_NAMES, Set<DB_FIELD_TYPES>> tempMap = new HashMap<>();
    tempMap.put(DEFAULT_CAMPAIGN_COLUMN_NAMES.FETCHED_DATA_FLAG, EnumSet.of(DB_FIELD_TYPES.FLOAT,
                                                                            DB_FIELD_TYPES.DOUBLE_PRECISION));
    tempMap.put(DEFAULT_CAMPAIGN_COLUMN_NAMES.RETRY_DATE, EnumSet.of(DB_FIELD_TYPES.FLOAT,
                                                                     DB_FIELD_TYPES.DOUBLE_PRECISION));
    tempMap.put(DEFAULT_CAMPAIGN_COLUMN_NAMES.RETRY_NUMBER,
                EnumSet.of(DB_FIELD_TYPES.VARCHAR, DB_FIELD_TYPES.NVARCHAR, DB_FIELD_TYPES.NCHAR,
                           DB_FIELD_TYPES.NVARCHAR2, DB_FIELD_TYPES.VARCHAR2, DB_FIELD_TYPES.CHARACTER_VARYING));
    tempMap.put(DEFAULT_CAMPAIGN_COLUMN_NAMES.RETRY_HISTORY,
                EnumSet.of(DB_FIELD_TYPES.VARCHAR, DB_FIELD_TYPES.NVARCHAR, DB_FIELD_TYPES.NCHAR,
                           DB_FIELD_TYPES.NVARCHAR2, DB_FIELD_TYPES.VARCHAR2, DB_FIELD_TYPES.CHARACTER_VARYING));
    tempMap.put(DEFAULT_CAMPAIGN_COLUMN_NAMES.CB_HISTORY,
                EnumSet.of(DB_FIELD_TYPES.VARCHAR, DB_FIELD_TYPES.NVARCHAR, DB_FIELD_TYPES.NCHAR,
                           DB_FIELD_TYPES.NVARCHAR2, DB_FIELD_TYPES.VARCHAR2, DB_FIELD_TYPES.CHARACTER_VARYING));
    tempMap.put(DEFAULT_CAMPAIGN_COLUMN_NAMES.CALL_PROCESS,
                EnumSet.of(DB_FIELD_TYPES.VARCHAR, DB_FIELD_TYPES.NVARCHAR, DB_FIELD_TYPES.NCHAR,
                           DB_FIELD_TYPES.NVARCHAR2, DB_FIELD_TYPES.VARCHAR2, DB_FIELD_TYPES.CHARACTER_VARYING));
    tempMap.put(DEFAULT_CAMPAIGN_COLUMN_NAMES.CLOSE_REASON,
                EnumSet.of(DB_FIELD_TYPES.VARCHAR, DB_FIELD_TYPES.NVARCHAR, DB_FIELD_TYPES.NCHAR,
                           DB_FIELD_TYPES.NVARCHAR2, DB_FIELD_TYPES.VARCHAR2, DB_FIELD_TYPES.CHARACTER_VARYING));
    tempMap.put(DEFAULT_CAMPAIGN_COLUMN_NAMES.CALLBACK_DATE, EnumSet.of(DB_FIELD_TYPES.FLOAT,
                                                                        DB_FIELD_TYPES.DOUBLE_PRECISION));
    tempMap.put(DEFAULT_CAMPAIGN_COLUMN_NAMES.CALLBACK_NUMBER,
                EnumSet.of(DB_FIELD_TYPES.VARCHAR, DB_FIELD_TYPES.NVARCHAR, DB_FIELD_TYPES.NCHAR,
                           DB_FIELD_TYPES.NVARCHAR2, DB_FIELD_TYPES.VARCHAR2, DB_FIELD_TYPES.CHARACTER_VARYING));
    tempMap.put(DEFAULT_CAMPAIGN_COLUMN_NAMES.USER_ID, EnumSet.of(DB_FIELD_TYPES.BIGINT, DB_FIELD_TYPES.NUMBER));
    tempMap.put(DEFAULT_CAMPAIGN_COLUMN_NAMES.RC_SEQUENCE, EnumSet.of(DB_FIELD_TYPES.INT, DB_FIELD_TYPES.INTEGER,
                                                                      DB_FIELD_TYPES.BIGINT, DB_FIELD_TYPES.NUMBER));
    tempMap.put(DEFAULT_CAMPAIGN_COLUMN_NAMES.USER_RC, EnumSet.of(DB_FIELD_TYPES.BIGINT, DB_FIELD_TYPES.NUMBER));
    tempMap.put(DEFAULT_CAMPAIGN_COLUMN_NAMES.TELEPHONY_RC, EnumSet.of(DB_FIELD_TYPES.INT, DB_FIELD_TYPES.INTEGER,
                                                                       DB_FIELD_TYPES.BIGINT, DB_FIELD_TYPES.NUMBER));
    tempMap.put(DEFAULT_CAMPAIGN_COLUMN_NAMES.CALLED_NUMBER,
                EnumSet.of(DB_FIELD_TYPES.VARCHAR, DB_FIELD_TYPES.NVARCHAR, DB_FIELD_TYPES.NCHAR,
                           DB_FIELD_TYPES.NVARCHAR2, DB_FIELD_TYPES.VARCHAR2, DB_FIELD_TYPES.CHARACTER_VARYING));
    tempMap.put(DEFAULT_CAMPAIGN_COLUMN_NAMES.COMPLETE_DATE, EnumSet.of(DB_FIELD_TYPES.FLOAT,
                                                                        DB_FIELD_TYPES.DOUBLE_PRECISION));
    tempMap.put(DEFAULT_CAMPAIGN_COLUMN_NAMES.ANSWER_DATE, EnumSet.of(DB_FIELD_TYPES.FLOAT,
                                                                      DB_FIELD_TYPES.DOUBLE_PRECISION));
    tempMap.put(DEFAULT_CAMPAIGN_COLUMN_NAMES.START_DATE, EnumSet.of(DB_FIELD_TYPES.FLOAT,
                                                                     DB_FIELD_TYPES.DOUBLE_PRECISION));
    tempMap.put(DEFAULT_CAMPAIGN_COLUMN_NAMES.UNIQUE_KEY, EnumSet.of(DB_FIELD_TYPES.BIGINT, DB_FIELD_TYPES.NUMBER));
    tempMap.put(DEFAULT_CAMPAIGN_COLUMN_NAMES.TIMEZONE_OFFSET, EnumSet.of(DB_FIELD_TYPES.INT, DB_FIELD_TYPES.INTEGER,
                                                                          DB_FIELD_TYPES.BIGINT,
                                                                          DB_FIELD_TYPES.NUMBER));
    tempMap.put(DEFAULT_CAMPAIGN_COLUMN_NAMES.CALL_FROM_TIME, EnumSet.of(DB_FIELD_TYPES.FLOAT,
                                                                         DB_FIELD_TYPES.DOUBLE_PRECISION));
    tempMap.put(DEFAULT_CAMPAIGN_COLUMN_NAMES.CALL_TILL_TIME, EnumSet.of(DB_FIELD_TYPES.FLOAT,
                                                                         DB_FIELD_TYPES.DOUBLE_PRECISION));
    tempMap.put(DEFAULT_CAMPAIGN_COLUMN_NAMES.PHONE_NUMBER,
                EnumSet.of(DB_FIELD_TYPES.VARCHAR, DB_FIELD_TYPES.NVARCHAR, DB_FIELD_TYPES.NCHAR,
                           DB_FIELD_TYPES.NVARCHAR2, DB_FIELD_TYPES.VARCHAR2, DB_FIELD_TYPES.CHARACTER_VARYING));
    tempMap.put(DEFAULT_CAMPAIGN_COLUMN_NAMES.CALLER_ID,
                EnumSet.of(DB_FIELD_TYPES.VARCHAR, DB_FIELD_TYPES.NVARCHAR, DB_FIELD_TYPES.NCHAR,
                           DB_FIELD_TYPES.NVARCHAR2, DB_FIELD_TYPES.VARCHAR2, DB_FIELD_TYPES.CHARACTER_VARYING));
    tempMap.put(DEFAULT_CAMPAIGN_COLUMN_NAMES.DNC_NUMBERS,
                EnumSet.of(DB_FIELD_TYPES.VARCHAR, DB_FIELD_TYPES.NVARCHAR, DB_FIELD_TYPES.NCHAR,
                           DB_FIELD_TYPES.NVARCHAR2, DB_FIELD_TYPES.VARCHAR2, DB_FIELD_TYPES.CHARACTER_VARYING));
    tempMap.put(DEFAULT_CAMPAIGN_COLUMN_NAMES.ATTEMPTS, EnumSet.of(DB_FIELD_TYPES.INT, DB_FIELD_TYPES.INTEGER,
                DB_FIELD_TYPES.BIGINT,
                DB_FIELD_TYPES.NUMBER));
    tempMap.put(DEFAULT_CAMPAIGN_COLUMN_NAMES.CALLBACK_ATTEMPTS, EnumSet.of(DB_FIELD_TYPES.INT, DB_FIELD_TYPES.INTEGER,
                DB_FIELD_TYPES.BIGINT,
                DB_FIELD_TYPES.NUMBER));
    tempMap.put(DEFAULT_CAMPAIGN_COLUMN_NAMES.UPDATE_DATE, EnumSet.of(DB_FIELD_TYPES.DATETIME, DB_FIELD_TYPES.TIMESTAMP));
    tempMap.put(DEFAULT_CAMPAIGN_COLUMN_NAMES.CALLBACK_TYPE,
              EnumSet.of(DB_FIELD_TYPES.VARCHAR, DB_FIELD_TYPES.NVARCHAR, DB_FIELD_TYPES.NCHAR,
                      DB_FIELD_TYPES.NVARCHAR2, DB_FIELD_TYPES.VARCHAR2, DB_FIELD_TYPES.CHARACTER_VARYING));
    tempMap.put(DEFAULT_CAMPAIGN_COLUMN_NAMES.RESPONSE_VALUES,
              EnumSet.of(DB_FIELD_TYPES.VARCHAR, DB_FIELD_TYPES.NVARCHAR, DB_FIELD_TYPES.NCHAR,
                      DB_FIELD_TYPES.NVARCHAR2, DB_FIELD_TYPES.VARCHAR2, DB_FIELD_TYPES.CHARACTER_VARYING));
      tempMap.put(DEFAULT_CAMPAIGN_COLUMN_NAMES.CUSTOMER_NAME,
              EnumSet.of(DB_FIELD_TYPES.VARCHAR, DB_FIELD_TYPES.NVARCHAR, DB_FIELD_TYPES.NCHAR,
                      DB_FIELD_TYPES.NVARCHAR2, DB_FIELD_TYPES.VARCHAR2, DB_FIELD_TYPES.CHARACTER_VARYING));
      tempMap.put(DEFAULT_CAMPAIGN_COLUMN_NAMES.CUSTOMER_SURNAME,
              EnumSet.of(DB_FIELD_TYPES.VARCHAR, DB_FIELD_TYPES.NVARCHAR, DB_FIELD_TYPES.NCHAR,
                      DB_FIELD_TYPES.NVARCHAR2, DB_FIELD_TYPES.VARCHAR2, DB_FIELD_TYPES.CHARACTER_VARYING));
      tempMap.put(DEFAULT_CAMPAIGN_COLUMN_NAMES.RELATION,
              EnumSet.of(DB_FIELD_TYPES.VARCHAR, DB_FIELD_TYPES.NVARCHAR, DB_FIELD_TYPES.NCHAR,
                      DB_FIELD_TYPES.NVARCHAR2, DB_FIELD_TYPES.VARCHAR2, DB_FIELD_TYPES.CHARACTER_VARYING));
      tempMap.put(DEFAULT_CAMPAIGN_COLUMN_NAMES.RELATION_TYPE,
              EnumSet.of(DB_FIELD_TYPES.BIT, DB_FIELD_TYPES.TINYINT,
                      DB_FIELD_TYPES.SMALLINT, DB_FIELD_TYPES.INTEGER));
      tempMap.put(DEFAULT_CAMPAIGN_COLUMN_NAMES.CALLED_GROUP_ID,
              EnumSet.of(DB_FIELD_TYPES.INT, DB_FIELD_TYPES.INTEGER,
                      DB_FIELD_TYPES.BIGINT,
                      DB_FIELD_TYPES.NUMBER));
      tempMap.put(DEFAULT_CAMPAIGN_COLUMN_NAMES.DISABLED,
              EnumSet.of(DB_FIELD_TYPES.INT, DB_FIELD_TYPES.INTEGER,
                      DB_FIELD_TYPES.BIGINT,
                      DB_FIELD_TYPES.NUMBER));

    RESULT_FIELDS_ALLOWED_TYPES = Collections.unmodifiableMap(new HashMap<>(tempMap));
  }

  public static final String SERVICE_STATUS_HEADER_NAME = "X-STATUS-SERVICE";

  @JsonFormat(shape = JsonFormat.Shape.OBJECT)
  public enum SERVICE_STATUS {
                              SUCCESS_1000(1000, "Success"),
                              WARNING_1001(1001, "Filters are empty"),

                              ERROR_2000(2000, "Unreadable request body"),
                              ERROR_2001(2001, "JSON parse exception"),
                              ERROR_2002(2002, "Data object is empty"),
                              ERROR_2003(2003, "File with specified extension not supported"),
                              ERROR_2004(2004, "File content has wrong format"),
                              ERROR_2005(2005, "Filter has wrong \"condition\" field value"),
                              ERROR_2006(2006, "Campaign not found"),
                              ERROR_2007(2007, "DNC not found"),
                              ERROR_2008(2008, "Query fetch limit exceeded"),
                              ERROR_2009(2009, "Request has non campaign's table's columns."),
                              ERROR_2010(2010, "Request contains filter without required field"),
                              ERROR_2011(2011, "Illegal data value type"),
                              ERROR_2012(2012, "Failed to start campaign priorities recalculation"),
                              ERROR_2013(2013, "Invalid API token"),

                              INTERNAL_ERROR_3000(3000, "Internal error occurred"),
                              INTERNAL_ERROR_3001(3001, "Error while processing multipart file"),
                              INTERNAL_ERROR_3002(3002, "Error while executing SQL query");

    private final int code;
    private final String description;

    SERVICE_STATUS(int code, String description) {
      this.code = code;
      this.description = description;
    }

    public int getCode() {
      return code;
    }

    public String getDescription() {
      return description;
    }
  }

  public enum LSC_RESULT {
                          OK,
                          FAIL,
                          TIMEOUT
  }

  public static final Set<String> RESERVED_SQL_NAMES;
  static {
    Set<String> tempSet = new HashSet<>();
    tempSet.add("absolute");
    tempSet.add("exec");
    tempSet.add("overlaps");
    tempSet.add("action");
    tempSet.add("execute");
    tempSet.add("pad");
    tempSet.add("ada");
    tempSet.add("exists");
    tempSet.add("partial");
    tempSet.add("add");
    tempSet.add("external");
    tempSet.add("pascal");
    tempSet.add("all");
    tempSet.add("extract");
    tempSet.add("position");
    tempSet.add("allocate");
    tempSet.add("false");
    tempSet.add("precision");
    tempSet.add("alter");
    tempSet.add("fetch");
    tempSet.add("prepare");
    tempSet.add("and");
    tempSet.add("first");
    tempSet.add("preserve");
    tempSet.add("any");
    tempSet.add("float");
    tempSet.add("primary");
    tempSet.add("are");
    tempSet.add("for");
    tempSet.add("prior");
    tempSet.add("as");
    tempSet.add("foreign");
    tempSet.add("privileges");
    tempSet.add("asc");
    tempSet.add("fortran");
    tempSet.add("procedure");
    tempSet.add("assertion");
    tempSet.add("found");
    tempSet.add("public");
    tempSet.add("at");
    tempSet.add("from");
    tempSet.add("read");
    tempSet.add("authorization");
    tempSet.add("full");
    tempSet.add("real");
    tempSet.add("avg");
    tempSet.add("get");
    tempSet.add("references");
    tempSet.add("begin");
    tempSet.add("global");
    tempSet.add("relative");
    tempSet.add("between");
    tempSet.add("go");
    tempSet.add("restrict");
    tempSet.add("bit");
    tempSet.add("goto");
    tempSet.add("revoke");
    tempSet.add("bit_length");
    tempSet.add("grant");
    tempSet.add("right");
    tempSet.add("both");
    tempSet.add("group");
    tempSet.add("rollback");
    tempSet.add("by");
    tempSet.add("having");
    tempSet.add("rows");
    tempSet.add("cascade");
    tempSet.add("hour");
    tempSet.add("schema");
    tempSet.add("cascaded");
    tempSet.add("identity");
    tempSet.add("scroll");
    tempSet.add("case");
    tempSet.add("immediate");
    tempSet.add("second");
    tempSet.add("cast");
    tempSet.add("in");
    tempSet.add("section");
    tempSet.add("catalog");
    tempSet.add("include");
    tempSet.add("select");
    tempSet.add("char");
    tempSet.add("index");
    tempSet.add("session");
    tempSet.add("char_length");
    tempSet.add("indicator");
    tempSet.add("session_user");
    tempSet.add("character");
    tempSet.add("initially");
    tempSet.add("set");
    tempSet.add("character_length");
    tempSet.add("inner");
    tempSet.add("size");
    tempSet.add("check");
    tempSet.add("input");
    tempSet.add("smallint");
    tempSet.add("close");
    tempSet.add("insensitive");
    tempSet.add("some");
    tempSet.add("coalesce");
    tempSet.add("insert");
    tempSet.add("space");
    tempSet.add("collate");
    tempSet.add("int");
    tempSet.add("sql");
    tempSet.add("collation");
    tempSet.add("integer");
    tempSet.add("sqlca");
    tempSet.add("column");
    tempSet.add("intersect");
    tempSet.add("sqlcode");
    tempSet.add("commit");
    tempSet.add("interval");
    tempSet.add("sqlerror");
    tempSet.add("connect");
    tempSet.add("into");
    tempSet.add("sqlstate");
    tempSet.add("connection");
    tempSet.add("is");
    tempSet.add("sqlwarning");
    tempSet.add("constraint");
    tempSet.add("isolation");
    tempSet.add("substring");
    tempSet.add("constraints");
    tempSet.add("join");
    tempSet.add("sum");
    tempSet.add("continue");
    tempSet.add("key");
    tempSet.add("system_user");
    tempSet.add("convert");
    tempSet.add("language");
    tempSet.add("table");
    tempSet.add("corresponding");
    tempSet.add("last");
    tempSet.add("temporary");
    tempSet.add("count");
    tempSet.add("leading");
    tempSet.add("then");
    tempSet.add("create");
    tempSet.add("left");
    tempSet.add("time");
    tempSet.add("cross");
    tempSet.add("level");
    tempSet.add("timestamp");
    tempSet.add("current");
    tempSet.add("like");
    tempSet.add("timezone_hour");
    tempSet.add("current_date");
    tempSet.add("local");
    tempSet.add("timezone_minute");
    tempSet.add("current_time");
    tempSet.add("lower");
    tempSet.add("to");
    tempSet.add("current_timestamp");
    tempSet.add("match");
    tempSet.add("trailing");
    tempSet.add("current_user");
    tempSet.add("max");
    tempSet.add("transaction");
    tempSet.add("cursor");
    tempSet.add("min");
    tempSet.add("translate");
    tempSet.add("date");
    tempSet.add("minute");
    tempSet.add("translation");
    tempSet.add("day");
    tempSet.add("module");
    tempSet.add("trim");
    tempSet.add("deallocate");
    tempSet.add("month");
    tempSet.add("true");
    tempSet.add("dec");
    tempSet.add("names");
    tempSet.add("union");
    tempSet.add("decimal");
    tempSet.add("national");
    tempSet.add("unique");
    tempSet.add("declare");
    tempSet.add("natural");
    tempSet.add("unknown");
    tempSet.add("default");
    tempSet.add("nchar");
    tempSet.add("update");
    tempSet.add("deferrable");
    tempSet.add("next");
    tempSet.add("upper");
    tempSet.add("deferred");
    tempSet.add("no");
    tempSet.add("usage");
    tempSet.add("delete");
    tempSet.add("none");
    tempSet.add("user");
    tempSet.add("desc");
    tempSet.add("not");
    tempSet.add("using");
    tempSet.add("describe");
    tempSet.add("null");
    tempSet.add("value");
    tempSet.add("descriptor");
    tempSet.add("nullif");
    tempSet.add("values");
    tempSet.add("diagnostics");
    tempSet.add("numeric");
    tempSet.add("varchar");
    tempSet.add("disconnect");
    tempSet.add("octet_length");
    tempSet.add("varying");
    tempSet.add("distinct");
    tempSet.add("of");
    tempSet.add("view");
    tempSet.add("domain");
    tempSet.add("on");
    tempSet.add("when");
    tempSet.add("double");
    tempSet.add("only");
    tempSet.add("whenever");
    tempSet.add("drop");
    tempSet.add("open");
    tempSet.add("where");
    tempSet.add("else");
    tempSet.add("option");
    tempSet.add("with");
    tempSet.add("end");
    tempSet.add("or");
    tempSet.add("work");
    tempSet.add("end-EXEC");
    tempSet.add("order");
    tempSet.add("write");
    tempSet.add("escape");
    tempSet.add("outer");
    tempSet.add("year");
    tempSet.add("except");
    tempSet.add("output");
    tempSet.add("zone");
    tempSet.add("exception");
    RESERVED_SQL_NAMES = Collections.unmodifiableSet(tempSet);
  }

  public static final String CAMPAIGN_TABLE_NAME_PATTERN = "\\w+[\\w\\d_]+";

  public enum SERVER_PROPERTY {
                               AMI_PORT("ami.port"),
                               AMI_USERNAME("ami.username"),
                               AMI_PASSWORD("ami.password"),
                               AMI_CONNECTION_TIMEOUT("ami.connection.timeout"),
                               AMI_COMMAND_INTERVAL("ami.command.interval");
    private final String name;

    SERVER_PROPERTY(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }
  }

  public static final int DEFAULT_AMI_CONNECTION_TIMEOUT = 10000;

  public enum PHONE_NUMBERS_ANTI_DUPLICATION {
                                              DISABLED,
                                              IN_FILE_EXCLUDE_CONTACT,
                                              IN_FILE_EXCLUDE_NUMBER,
                                              IN_DATABASE_EXCLUDE_CONTACT,
                                              IN_DATABASE_EXCLUDE_NUMBER,
  }

  public static final int UPLOAD_ERROR_LOG_THRESHOLD = 30;

  public static final String CAMPAIGN_CONTROLLER_MAPPING = "/campaign";
  public static final String CAMPAIGN_CONTROLLER_FAILED_ROWS_MAPPING = "/upload/failedRows/{filename:.+}";

  public static final int DNC_PHONE_NUMBER_COUNT_FOR_BATCH_PROCESSING = 100;

  public enum REDIS_USER_FIELD {
    staticData,
    currentStatus,
    statusSetDate
  }
  
  public enum ATTACHED_DATA_OBJECT {
    VALUE("value"),
    POPUP("popup"),
    POPUP_NAME("popup_name");
    
    private String fieldName;
    
    ATTACHED_DATA_OBJECT(String fieldName){
      this.fieldName = fieldName;
    }

    public String getFieldName() {
      return fieldName;
    }
    
  }

  public enum STOP_CONDITION_PARAMETER {
                                        TOUCHED_CONTACTS_COUNT,
                                        COMPLETED_CONTACTS_COUNT,
                                        USER_RC_COUNT,
                                        TELEPHONY_RC_COUNT
  }

  @JsonFormat(shape = JsonFormat.Shape.NUMBER_INT)
  public enum SCHEDULE_ACTION_TYPE {
      START,
      STOP,
      PAUSE,
  }

  @JsonFormat(shape = JsonFormat.Shape.NUMBER_INT)
  public enum COMPARISON_STRATEGY_TYPE {
      AVERAGE_VALUE,
      ONE_BY_ONE
  }

  @JsonFormat(shape = JsonFormat.Shape.NUMBER_INT)
  public enum CAMPAIGN_STOP_TYPE {
    MANUAL,
    AUTOMATIC,
    STOP_CONDITION
  }

  @JsonFormat(shape = JsonFormat.Shape.NUMBER_INT)
  public enum PRIORITY_RECALCULATION_STATE {
    EXECUTING,
    EXPECTING,
    DECLINED
  }

  public enum ROUTING_GROUP_TYPE {
    CALL_QUEUE,
    CHAT_QUEUE,
    CALLERID
  }

  public enum RDS_MESSAGE_ACTION_TYPE {
    RELOAD,
    REMOVE,
    RELOAD_ALL
  }

  public enum MULTIPLE_SORT_METHOD {
    BOOLEAN,
    DICTIONARY,
    NATURAL_ORDER
  }

  @JsonFormat(shape = JsonFormat.Shape.NUMBER_INT)
  public enum LSC_TIMEOUT_BEHAVIOR {
    NOT_DIAL,
    DIAL
  }

  @JsonFormat(shape = JsonFormat.Shape.NUMBER_INT)
  public enum RETRY_CALLER_ID_BEHAVIOR {
      ANY,
      UNIQUE,
      SAME
  }

  public enum CDR_OUTDIALING_TYPE {

    NEW(1L, "new", "current dial attempt of current phone number of current contact is first dial attempt"),
    IMMEDIATE_RETRY(2L, "immediate_retry", "current dial attempt of current phone number of current contact is immediate retry according to current retry strategy (no scheduling)"),
    SCHEDULED_RETRY(3L, "scheduled_retry", "current dial attempt of current phone number of current contact is scheduled retry that has been taken from database using retry clause of database selection"),
    CALLBACK(4L, "callback", "current dial attempt of current phone number of current contact is scheduled callback that has been taken from database using callback clause of database selection");

    private final Long id;
    private final String name;
    private final String description;

    CDR_OUTDIALING_TYPE(Long id, String name, String description) {
      this.id = id;
      this.name = name;
      this.description = description;
    }

    public Long getId() {
      return id;
    }

    public String getName() {
      return name;
    }

    public String getDescription() {
      return description;
    }

    public static CDR_OUTDIALING_TYPE parse(Long id) {
      for (CDR_OUTDIALING_TYPE value : CDR_OUTDIALING_TYPE.values()) {
        if (value.getId().equals(id))
          return value;
      }
      return null;
    }

  }

  public enum APP_CONFIG_PROPERTY {
    APP_CONFIG_APPLICATION_NAME("application.name"),
    APP_CONFIG_BUILD_DATE("project.buildDate"),
    APP_CONFIG_PROJECT_STAGE("project.stage"),
    APP_CONFIG_PROJECT_VERSION("project.version"),
    APP_CONFIG_ONEACCESS_ENABLED("oneaccess.enabled"),
    APP_CONFIG_REST_FETCH_LIMIT("rest.fetchLimit");

    private String propertyName;

    APP_CONFIG_PROPERTY(String propertyName) {
      this.propertyName = propertyName;
    }

    public String getPropertyName() {
      return propertyName;
    }
  }

    public enum RequestBodyType {
        FORM_URLENCODED,
        JSON,
        XML,
        RAW
    }

    public enum CampaignRecordStatus {
        COMPLETED("status.completed"),
        ATTEMPTS_LIMIT_REACHED("status.attempts_limit_reached"),
        CALLABLE("status.callable"),
        ELIMINATED("status.eliminated"),
        IN_PROCESS("status.in_process"),
        RETRY("status.retry"),
        CALLBACK("status.callback"),
        EXCLUDED("status.excluded"),
        DNC("status.dnc"),
        UNPROCESSED("status.unprocessed");

        private final String messageKey;

        CampaignRecordStatus(String messageKey) {
            this.messageKey = messageKey;
        }

        public String getMessageKey() {
            return messageKey;
        }
    }


    public enum CAMPAIGN_RECORD_OPERATION {
        ACTIVATE_RECORD,
        ADD_CALL_AS_PRIORITY,
        REMOVE_CALL_FROM_PRIORITY,
        CONTACT_LIST_BULK_UPDATE,
        DELETE_RECORD
    }

    public enum RECORD_UPDATE_OPERATION_STATUS {
        UPDATE_FAILED,
        UPDATE_SUCCESSFUL,
        CAMPAIGN_IS_RUNNING_CANT_UPDATE,
        CAMPAIGN_IS_ALREADY_PRIORITY
    }

    public enum CAMPAIGN_JOB_RESULT_STATUS {
        JOB_RESULT_FAILED,
        JOB_RESULT_SUCCESSFUL,
        JOB_RESULT_SKIPPED,
        JOB_RESULT_RUNNING,
        JOB_RESULT_BUILDING
    }

    public enum RECORD_DELETION_REASON {
        REMOVED_BY_BANK_REQUEST("campaignDataManipulation.eliminationReason.bank"),
        COMPLAINT("campaignDataManipulation.eliminationReason.complaint"),
        COUNTRY("campaignDataManipulation.eliminationReason.country"),
        LIST_CLOSURE("campaignDataManipulation.eliminationReason.list"),
        SYSTEM_ISSUE("campaignDataManipulation.eliminationReason.system"),
        REACHED_RECORD_CLOSURE("campaignDataManipulation.eliminationReason.reached");

        private final String messageKey;

        RECORD_DELETION_REASON(String messageKey) {
            this.messageKey = messageKey;
        }

        public String getMessageKey() {
            return messageKey;
        }
    }

    public enum RESPONSE_TYPE {
        JSON,
        XML
    }

    public enum CALLBACKTYPE {
        ASSIGN_TO_USER_GROUP(0),
        ASSIGN_TO_SAME_USER(1),
        NONE(-1);
        private static final Map<Integer, CALLBACKTYPE> ID_MAP;
        static {
            Map<Integer, CALLBACKTYPE> m = new HashMap<>();
            for (CALLBACKTYPE t : values()) m.put(t.id, t);
            ID_MAP = Collections.unmodifiableMap(m);
        }
        private final int id;
        CALLBACKTYPE(int id) { this.id = id; }
        public int getId() { return id; }
        public static CALLBACKTYPE fromId(int id) {
            return ID_MAP.getOrDefault(id, NONE);
        }
        public static CALLBACKTYPE fromString(String raw) {
            if (raw == null) return NONE;
            String s = raw.trim();
            if (s.isEmpty() || s.length() > 64) return NONE;
            if (s.matches("-?\\d+")) {
                try {
                    return fromId(Integer.parseInt(s));
                } catch (NumberFormatException ignore) {
                    return NONE;
                }
            }
            try {
                return CALLBACKTYPE.valueOf(s.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return NONE;
            }
        }
        public static CALLBACKTYPE fromAny(Object v) {
            if (v == null) return NONE;
            if (v instanceof CALLBACKTYPE) return (CALLBACKTYPE) v;
            if (v instanceof Number) return fromId(((Number) v).intValue());
            return fromString(String.valueOf(v));
        }
    }}
