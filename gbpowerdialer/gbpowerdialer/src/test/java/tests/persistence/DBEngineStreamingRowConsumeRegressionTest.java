package tests.persistence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.json.JSONObject;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

import com.powerdialer.engine.db.DBEngine;

/**
 * Task 327143: DataManipulation fetch'inin JSONArray biriktirmeden satir satir
 * tuketilmesi icin DBEngine'e eklenen streaming okuma dongusunu kilitler.
 *
 * <p>Korunan mevcut semantik (JSONArray yolu ile birebir): NULL kolon degeri
 * JSONObject'e konmaz; tum kolonlari NULL olan satir consumer'a hic verilmez.
 * Yeni semantik: maxRows tavanina ulasildiktan sonra bir non-empty satir daha
 * gelirse okuma kesilir ve truncated=true doner.</p>
 *
 * <p>Metot heniz yokken bu test MethodNotFound ile RED'dir; reflection
 * kullanmasinin nedeni DBEngine'in gercek DataSource kurulumuna girmeden
 * salt dongu davranisini test etmektir.</p>
 */
public class DBEngineStreamingRowConsumeRegressionTest {

  private static final String METHOD = "consumeResultSetRows";

  private ResultSet resultSet(String[][] cells, String... columnNames) throws Exception {
    ResultSetMetaData meta = mock(ResultSetMetaData.class);
    when(meta.getColumnCount()).thenReturn(columnNames.length);
    for (int j = 0; j < columnNames.length; j++) {
      when(meta.getColumnName(j + 1)).thenReturn(columnNames[j]);
    }
    ResultSet rs = mock(ResultSet.class);
    when(rs.getMetaData()).thenReturn(meta);
    Boolean[] nextTail = new Boolean[cells.length];
    for (int i = 0; i < cells.length; i++) {
      nextTail[i] = i < cells.length - 1;
    }
    if (cells.length == 0) {
      when(rs.next()).thenReturn(false);
    } else {
      when(rs.next()).thenReturn(true, nextTail);
    }
    for (int j = 0; j < columnNames.length; j++) {
      String first = cells[0][j];
      String[] rest = new String[cells.length - 1];
      for (int i = 1; i < cells.length; i++) {
        rest[i - 1] = cells[i][j];
      }
      when(rs.getString(j + 1)).thenReturn(first, rest);
    }
    return rs;
  }

  private List<JSONObject> collect(ResultSet rs, Integer maxRows, boolean expectedTruncated) throws Exception {
    final List<JSONObject> delivered = new ArrayList<>();
    Consumer<JSONObject> consumer = new Consumer<JSONObject>() {
      @Override
      public void accept(JSONObject row) {
        delivered.add(row);
      }
    };
    boolean truncated = Whitebox.invokeMethod(DBEngine.class, METHOD, rs, maxRows, consumer);
    assertEquals("truncated bayragi beklenenle uyusmali", expectedTruncated, truncated);
    return delivered;
  }

  @Test
  public void streamsAllRowsOmittingNullColumnsWhenNoCap() throws Exception {
    ResultSet rs = resultSet(new String[][] {
        {"1", "a"},
        {"2", null},
        {"3", "c"}}, "id", "name");

    List<JSONObject> rows = collect(rs, null, false);

    assertEquals(3, rows.size());
    assertEquals("a", rows.get(0).getString("name"));
    assertFalse("NULL kolon JSONObject'e konmamali", rows.get(1).has("name"));
    assertEquals("2", rows.get(1).getString("id"));
    assertEquals("c", rows.get(2).getString("name"));
  }

  @Test
  public void capReachedStopsDeliveryAndReportsTruncated() throws Exception {
    ResultSet rs = resultSet(new String[][] {
        {"1", "a"},
        {"2", "b"},
        {"3", "c"}}, "id", "name");

    List<JSONObject> rows = collect(rs, 2, true);

    assertEquals("Tavan sonrasi satir consumer'a verilmemeli", 2, rows.size());
    assertEquals("1", rows.get(0).getString("id"));
    assertEquals("2", rows.get(1).getString("id"));
  }

  @Test
  public void exactCapWithoutExtraRowIsNotTruncated() throws Exception {
    ResultSet rs = resultSet(new String[][] {
        {"1", "a"},
        {"2", "b"},
        {"3", "c"}}, "id", "name");

    List<JSONObject> rows = collect(rs, 3, false);

    assertEquals(3, rows.size());
  }

  @Test
  public void allNullRowsAreSkippedEntirely() throws Exception {
    ResultSet rs = resultSet(new String[][] {
        {null, null},
        {null, null}}, "id", "name");

    List<JSONObject> rows = collect(rs, null, false);

    assertTrue("Tum kolonlari NULL satirlar hic verilmemeli", rows.isEmpty());
  }
}
