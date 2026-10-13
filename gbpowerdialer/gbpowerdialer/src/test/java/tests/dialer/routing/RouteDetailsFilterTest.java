package tests.dialer.routing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import com.globalbilgi.gbqueueselectionservice.grpc.RouteDetailEntry;
import com.powerdialer.engine.dialer.routing.RouteDetailsFilter;
import com.powerdialer.util.Constants;

/**
 * Kalem 1a: QSS route listesi çağrının tipine ve deneme sayısına göre süzülür.
 *
 * <p>Gerekçe: eski `obtainCallerId` yolu QSS'e `CT_PD_<callType>` ve `AT_<attempt>` gönderip seçimi
 * QSS'e bırakıyordu; `getRouteDetails` bu context'i almadığı için çağrı tipine tanımlı olmayan
 * route'lar da deneniyordu. Süzme hiçbir koşulda çağrıyı caller ID'siz bırakmamalı (fail-open).
 */
public class RouteDetailsFilterTest {

  @Test
  public void shouldKeepOnlyEntriesMatchingCallType() {
    List<RouteDetailEntry> entries = Arrays.asList(
        entry("0501", "Manual", "1"),
        entry("0502", "Retry", "1"),
        entry("0503", "Dialer First", "1"));

    List<RouteDetailEntry> filtered = RouteDetailsFilter.filterForCall(entries, Constants.CALL_TYPES.RETRY, 1);

    assertEquals(1, filtered.size());
    assertEquals("0502", filtered.get(0).getPhoneNumber());
  }

  @Test
  public void shouldMapFreshCallTypeToDialerFirstLabel() {
    List<RouteDetailEntry> entries = Arrays.asList(
        entry("0501", "Retry", "0"),
        entry("0502", "Dialer First", "0"));

    List<RouteDetailEntry> filtered = RouteDetailsFilter.filterForCall(entries, Constants.CALL_TYPES.FRESH, 0);

    assertEquals(1, filtered.size());
    assertEquals("0502", filtered.get(0).getPhoneNumber());
  }

  @Test
  public void shouldMatchCommaJoinedMultiValueType() {
    List<RouteDetailEntry> entries = Arrays.asList(
        entry("0501", "Manual,Callback", "3"),
        entry("0502", "Retry,Dialer First", "3"));

    List<RouteDetailEntry> filtered = RouteDetailsFilter.filterForCall(entries, Constants.CALL_TYPES.CALLBACK, 3);

    assertEquals(1, filtered.size());
    assertEquals("0501", filtered.get(0).getPhoneNumber());
  }

  @Test
  public void shouldPreserveQssOrdering() {
    List<RouteDetailEntry> entries = Arrays.asList(
        entry("0501", "Retry", "2"),
        entry("0502", "Manual", "2"),
        entry("0503", "Retry", "2"));

    List<RouteDetailEntry> filtered = RouteDetailsFilter.filterForCall(entries, Constants.CALL_TYPES.RETRY, 2);

    assertEquals(2, filtered.size());
    assertEquals("QSS sıralaması korunmalı", "0501", filtered.get(0).getPhoneNumber());
    assertEquals("0503", filtered.get(1).getPhoneNumber());
  }

  @Test
  public void shouldFilterByAttemptWhenAttemptListIsExplicit() {
    List<RouteDetailEntry> entries = Arrays.asList(
        entry("0501", "Retry", "0,1"),
        entry("0502", "Retry", "5,6"));

    List<RouteDetailEntry> filtered = RouteDetailsFilter.filterForCall(entries, Constants.CALL_TYPES.RETRY, 5);

    assertEquals(1, filtered.size());
    assertEquals("0502", filtered.get(0).getPhoneNumber());
  }

  /** QSS route'ta hiç AT_ attribute'u yoksa 0..99 fallback'i döndürüyor → her deneme uyar. */
  @Test
  public void shouldMatchAnyAttemptWithQssFallbackRange() {
    StringBuilder fallback = new StringBuilder();
    for (int index = 0; index < 100; index++) {
      if (index > 0) {
        fallback.append(',');
      }
      fallback.append(index);
    }
    List<RouteDetailEntry> entries = Collections.singletonList(entry("0501", "Retry", fallback.toString()));

    List<RouteDetailEntry> filtered = RouteDetailsFilter.filterForCall(entries, Constants.CALL_TYPES.RETRY, 42);

    assertEquals(1, filtered.size());
  }

  // --- fail-open davranışı ------------------------------------------------

  @Test
  public void shouldFallBackToUnfilteredListWhenFilterEmptiesIt() {
    List<RouteDetailEntry> entries = Arrays.asList(
        entry("0501", "Manual", "1"),
        entry("0502", "Manual", "1"));

    List<RouteDetailEntry> filtered = RouteDetailsFilter.filterForCall(entries, Constants.CALL_TYPES.RETRY, 1);

    assertSame("süzme tüm listeyi elerse çağrı caller ID'siz bırakılmaz", entries, filtered);
  }

  @Test
  public void shouldNotFilterEntriesWithBlankType() {
    List<RouteDetailEntry> entries = Arrays.asList(
        entry("0501", "", "1"),
        entry("0502", "Manual", "1"));

    List<RouteDetailEntry> filtered = RouteDetailsFilter.filterForCall(entries, Constants.CALL_TYPES.RETRY, 1);

    assertEquals(1, filtered.size());
    assertEquals("boş type elenmez", "0501", filtered.get(0).getPhoneNumber());
  }

  @Test
  public void shouldNotFilterEntriesWithUnknownTypeVocabulary() {
    List<RouteDetailEntry> entries = Arrays.asList(
        entry("0501", "Blast", "1"),
        entry("0502", "Manual", "1"));

    List<RouteDetailEntry> filtered = RouteDetailsFilter.filterForCall(entries, Constants.CALL_TYPES.RETRY, 1);

    assertEquals(1, filtered.size());
    assertEquals("QSS sözlüğü değişirse entry elenmez", "0501", filtered.get(0).getPhoneNumber());
  }

  @Test
  public void shouldNotFilterEntriesWithBlankAttempts() {
    List<RouteDetailEntry> entries = Arrays.asList(
        entry("0501", "Retry", ""),
        entry("0502", "Retry", "9"));

    List<RouteDetailEntry> filtered = RouteDetailsFilter.filterForCall(entries, Constants.CALL_TYPES.RETRY, 1);

    assertEquals(1, filtered.size());
    assertEquals("boş attempts elenmez", "0501", filtered.get(0).getPhoneNumber());
  }

  @Test
  public void shouldSkipFilteringWhenCallTypeIsUnknown() {
    List<RouteDetailEntry> entries = Arrays.asList(
        entry("0501", "Manual", "1"),
        entry("0502", "Retry", "1"));

    List<RouteDetailEntry> filtered = RouteDetailsFilter.filterForCall(entries, null, 1);

    assertSame("callType bilinmiyorsa süzme uygulanmaz", entries, filtered);
  }

  @Test
  public void shouldHandleNullAndEmptyInput() {
    assertEquals(null, RouteDetailsFilter.filterForCall(null, Constants.CALL_TYPES.RETRY, 1));
    List<RouteDetailEntry> empty = Collections.emptyList();
    assertSame(empty, RouteDetailsFilter.filterForCall(empty, Constants.CALL_TYPES.RETRY, 1));
  }

  private RouteDetailEntry entry(String phoneNumber, String type, String attempts) {
    return RouteDetailEntry.newBuilder()
        .setPhoneNumber(phoneNumber)
        .setType(type)
        .setAttempts(attempts)
        .setCapacity(4)
        .build();
  }
}
