package com.powerdialer.engine.dialer.routing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.globalbilgi.gbqueueselectionservice.grpc.RouteDetailEntry;
import com.powerdialer.util.Constants;

/**
 * QSS {@code getRouteDetails} listesini çağrının tipine ve deneme sayısına göre süzer.
 *
 * <p>Neden gerekli: eski {@code obtainCallerId} yolu QSS'e {@code CT_PD_<callType>} ve
 * {@code AT_<attempt>} attribute'larını gönderip seçimi QSS'e bırakıyordu. {@code getRouteDetails}
 * yalnız {@code (routingGroupId, calledNumber)} aldığı için bu context kayboluyor ve çağrı tipine
 * tanımlı olmayan route'lar da deneniyordu. QSS bu bilgiyi yanıtta zaten döndürüyor
 * ({@code type} = CT_* çevirimi, {@code attempts} = AT_* değerleri, ikisi de virgülle birleşik),
 * bu sınıf o alanları kullanarak süzmeyi tüketen tarafta yapar.
 *
 * <p><b>Sınır</b>: bu süzme {@code retryCallerIdBehavior} (UNIQUE/SAME) semantiğini geri
 * kazandırmaz. O semantik "bu numara için daha önce hangi caller ID kullanıldı" geçmişini
 * gerektirir; geçmiş QSS'in provision history'sindedir ve PowerDialer'da yoktur. Tam parite için
 * {@code RouteDetailsRequest}'e context alanlarının eklenmesi gerekir (QSS tarafı iş).
 *
 * <p><b>Fail-open</b>: süzme hiçbir zaman çağrıyı caller ID'siz bırakmaz. Tanınmayan/boş alan
 * değerleri entry'yi elemez ve süzme tüm listeyi elerse filtresiz listeye dönülür.
 */
public final class RouteDetailsFilter {

  private static final Logger LOG = LoggerFactory.getLogger(RouteDetailsFilter.class);

  private static final String TYPE_MANUAL = "Manual";
  private static final String TYPE_RETRY = "Retry";
  private static final String TYPE_CALLBACK = "Callback";
  private static final String TYPE_DIALER_FIRST = "Dialer First";

  /** QSS'in ürettiği bilinen tip etiketleri (küçük harf). Bu kümenin dışı "tanınmayan" sayılır. */
  private static final Set<String> KNOWN_TYPE_LABELS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
      TYPE_MANUAL.toLowerCase(),
      TYPE_RETRY.toLowerCase(),
      TYPE_CALLBACK.toLowerCase(),
      TYPE_DIALER_FIRST.toLowerCase())));

  private RouteDetailsFilter() {
  }

  /**
   * PowerDialer çağrı tipinin QSS karşılığı. {@code CT_PD_FRESH → Dialer First},
   * {@code CT_PD_RETRY → Retry}, {@code CT_PD_CALLBACK → Callback}.
   *
   * @return QSS etiketi, çağrı tipi bilinmiyorsa {@code null} (süzme uygulanmaz)
   */
  static String expectedTypeLabel(Constants.CALL_TYPES callType) {
    if (callType == null) {
      return null;
    }
    switch (callType) {
      case FRESH:
        return TYPE_DIALER_FIRST;
      case RETRY:
        return TYPE_RETRY;
      case CALLBACK:
        return TYPE_CALLBACK;
      default:
        return null;
    }
  }

  /**
   * Çağrının tipine ve deneme sayısına uyan entry'leri döndürür; QSS sıralaması korunur.
   *
   * @param attempt {@code call.getAttempts()} — originate öncesi artırıldığı için QSS'in
   *                {@code AT_} indeksiyle aynı değerdedir
   * @return süzülmüş liste; süzme tüm listeyi elerse ya da uygulanamazsa gelen listenin kendisi
   */
  public static List<RouteDetailEntry> filterForCall(List<RouteDetailEntry> entries,
                                                     Constants.CALL_TYPES callType,
                                                     int attempt) {
    if (entries == null || entries.isEmpty()) {
      return entries;
    }
    String expectedType = expectedTypeLabel(callType);
    if (expectedType == null) {
      return entries;
    }

    List<RouteDetailEntry> filtered = new ArrayList<>(entries.size());
    for (RouteDetailEntry entry : entries) {
      if (matchesType(entry.getType(), expectedType) && matchesAttempt(entry.getAttempts(), attempt)) {
        filtered.add(entry);
      }
    }

    if (filtered.isEmpty()) {
      LOG.warn("Route details filter emptied the list, falling back to unfiltered: callType={}, attempt={}, size={}",
          callType, attempt, entries.size());
      return entries;
    }
    return filtered;
  }

  /**
   * Tip eşleşmesi. Boş değer elemez. Beklenen etiket varsa eşleşir. Değerde hiç BİLİNEN etiket
   * yoksa (QSS sözlüğü değişmiş olabilir) entry elenmez — fail-open.
   */
  private static boolean matchesType(String type, String expectedType) {
    if (isBlank(type)) {
      return true;
    }
    boolean sawKnownLabel = false;
    for (String raw : type.split(",")) {
      String token = raw.trim();
      if (token.isEmpty()) {
        continue;
      }
      if (expectedType.equalsIgnoreCase(token)) {
        return true;
      }
      if (KNOWN_TYPE_LABELS.contains(token.toLowerCase())) {
        sawKnownLabel = true;
      }
    }
    return !sawKnownLabel;
  }

  /**
   * Deneme eşleşmesi. Boş değer elemez. Değerde hiç sayısal token yoksa entry elenmez — fail-open.
   * QSS route'ta {@code AT_} attribute'u yoksa 0..99 fallback'i döndürdüğü için bu koşul pratikte
   * "her deneme" anlamına gelir.
   */
  private static boolean matchesAttempt(String attempts, int attempt) {
    if (isBlank(attempts)) {
      return true;
    }
    String expected = String.valueOf(attempt);
    boolean sawNumericToken = false;
    for (String raw : attempts.split(",")) {
      String token = raw.trim();
      if (token.isEmpty()) {
        continue;
      }
      if (expected.equals(token)) {
        return true;
      }
      if (isNumeric(token)) {
        sawNumericToken = true;
      }
    }
    return !sawNumericToken;
  }

  private static boolean isNumeric(String token) {
    for (int index = 0; index < token.length(); index++) {
      if (!Character.isDigit(token.charAt(index))) {
        return false;
      }
    }
    return true;
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
