package tests.dialer.routing;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.globalbilgi.gbqueueselectionservice.grpc.RouteDetailEntry;
import com.powerdialer.engine.dialer.routing.RouteDetailsCursor;
import org.junit.Test;

public class RouteDetailsCursorTest {

  @Test
  public void shouldReportEmptyForNullList() {
    RouteDetailsCursor cursor = new RouteDetailsCursor(null);

    assertThat(cursor.isEmpty(), is(true));
    assertThat(cursor.size(), is(0));
    assertThat(cursor.hasCurrent(), is(false));
    assertThat(cursor.hasNext(), is(false));
    assertThat(cursor.current(), nullValue());
    assertThat(cursor.next(), nullValue());
  }

  @Test
  public void shouldReportEmptyForEmptyList() {
    RouteDetailsCursor cursor = new RouteDetailsCursor(Collections.<RouteDetailEntry>emptyList());

    assertThat(cursor.isEmpty(), is(true));
    assertThat(cursor.hasCurrent(), is(false));
    assertThat(cursor.hasNext(), is(false));
  }

  @Test
  public void shouldReturnFirstEntryAsCurrentBeforeAdvance() {
    RouteDetailEntry first = entry("0501", "Manual", 1);
    RouteDetailEntry second = entry("0502", "Retry", 2);
    RouteDetailsCursor cursor = new RouteDetailsCursor(Arrays.asList(first, second));

    assertThat(cursor.isEmpty(), is(false));
    assertThat(cursor.size(), is(2));
    assertThat(cursor.position(), is(0));
    assertThat(cursor.hasCurrent(), is(true));
    assertThat(cursor.current().getPhoneNumber(), is("0501"));
  }

  @Test
  public void shouldAdvanceThroughEntries() {
    RouteDetailEntry first = entry("0501", "Manual", 1);
    RouteDetailEntry second = entry("0502", "Retry", 2);
    RouteDetailsCursor cursor = new RouteDetailsCursor(Arrays.asList(first, second));

    assertThat(cursor.hasNext(), is(true));
    RouteDetailEntry advancedTo = cursor.next();
    assertThat(advancedTo.getPhoneNumber(), is("0502"));
    assertThat(cursor.position(), is(1));
    assertThat(cursor.current().getPhoneNumber(), is("0502"));
    assertThat(cursor.hasNext(), is(false));
    assertThat(cursor.next(), nullValue());
    assertThat(cursor.position(), is(1));
  }

  @Test
  public void shouldKeepCurrentIdempotent() {
    RouteDetailEntry only = entry("0501", "Manual", 1);
    RouteDetailsCursor cursor = new RouteDetailsCursor(Collections.singletonList(only));

    RouteDetailEntry firstRead = cursor.current();
    RouteDetailEntry secondRead = cursor.current();

    assertThat(firstRead.getPhoneNumber(), is(secondRead.getPhoneNumber()));
    assertThat(cursor.position(), is(0));
  }

  /**
   * Faz 2: {@code remaining()} HAS_NEXT üretimi için eklendi. Gate, "sıradaki adaylardan en az biri
   * cooldown dışında mı" sorusunu rezervasyon yapmadan yanıtlamak zorundadır; {@code next()} ile
   * bakmak cursor'ı tüketip gerçek karar yolunu bozardı.
   */
  @Test
  public void remainingShouldReturnEntriesAfterCurrentWithoutAdvancing() {
    RouteDetailsCursor cursor = new RouteDetailsCursor(
        Arrays.asList(entry("0501", "Manual", 1), entry("0502", "Manual", 1), entry("0503", "Manual", 1)));

    assertThat(cursor.remaining().size(), is(2));
    assertThat(cursor.remaining().get(0).getPhoneNumber(), is("0502"));
    assertThat("remaining cursor'ı İLERLETMEMELİ", cursor.position(), is(0));
    assertThat(cursor.current().getPhoneNumber(), is("0501"));
  }

  @Test
  public void remainingShouldBeEmptyOnLastEntryAndOnEmptyCursor() {
    RouteDetailsCursor cursor = new RouteDetailsCursor(
        Arrays.asList(entry("0501", "Manual", 1), entry("0502", "Manual", 1)));
    cursor.next();

    assertThat("son entry'de kalan aday yok", cursor.remaining().isEmpty(), is(true));
    assertThat(new RouteDetailsCursor(Collections.<RouteDetailEntry>emptyList()).remaining().isEmpty(), is(true));
    assertThat(new RouteDetailsCursor(null).remaining().isEmpty(), is(true));
  }

  private RouteDetailEntry entry(String phone, String type, int attempts) {
    return RouteDetailEntry.newBuilder()
        .setPhoneNumber(phone)
        .setType(type)
        // QSS canonical proto'da attempts string alandır (FEAT-GLB90114534-FCT-KANAL-KAPASITE-GATE).
        .setAttempts(String.valueOf(attempts))
        .build();
  }
}
