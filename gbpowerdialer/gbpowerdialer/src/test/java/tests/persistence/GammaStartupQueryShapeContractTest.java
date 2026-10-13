package tests.persistence;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.powerdialer.dao.impl.CDRDao;
import java.util.List;
import javax.persistence.QueryTimeoutException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.NativeQuery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * FIX-333607: GAMMA startup ortalama sorguları index'e uygun ve süre sınırlı çalışır.
 *
 * <p>02.10.2026 Turkcell canlı planında konuşma süresi sorgusu günü
 * {@code CAST(c.call_dial_time AS DATE) = (alt sorgu)} ile filtrelediği için campaign'in tüm
 * geçmişini okuyup her satıra key lookup yapıyordu (elapsed 67 sn, CPU 0,58 sn) ve campaign
 * start'ları dakikalar sürüyordu. {@code call_dial_time} indexsizdir. Gün artık
 * {@code (campaign_id, call_start_time)} index'ine uyan bir aralıkla filtrelenir; son çalışma günü
 * {@code (campaign_id, call_handling_start)} index'inden tek satır okunur.</p>
 */
@RunWith(MockitoJUnitRunner.class)
public class GammaStartupQueryShapeContractTest {

  private static final long CAMPAIGN_ID = 42L;

  @Mock private SessionFactory sessionFactory;
  @Mock private Session session;
  @Mock @SuppressWarnings("rawtypes") private NativeQuery nativeQuery;

  private CDRDao cdrDao;

  @Before
  @SuppressWarnings("unchecked")
  public void setUp() {
    cdrDao = new CDRDao();
    cdrDao.setSessionFactory(sessionFactory);
    when(sessionFactory.getCurrentSession()).thenReturn(session);
    when(session.createNativeQuery(anyString())).thenReturn(nativeQuery);
    when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);
    when(nativeQuery.setTimeout(anyInt())).thenReturn(nativeQuery);
  }

  @Test
  public void talkTimeQueryFiltersDayByIndexedStartTimeRange() {
    when(nativeQuery.getSingleResult()).thenReturn(100.0d);

    cdrDao.findGammaStartupAverageTalkTime(CAMPAIGN_ID, 10);

    assertDayRangeShape(captureNativeSql());
    verify(nativeQuery).setTimeout(10);
  }

  @Test
  public void ringTimeQueryFiltersDayByIndexedStartTimeRange() {
    when(nativeQuery.getSingleResult()).thenReturn(10.0d);

    cdrDao.findGammaStartupAverageRingTime(CAMPAIGN_ID);

    assertDayRangeShape(captureNativeSql());
    verify(nativeQuery).setTimeout(10);
  }

  @Test
  public void answeredRingTimeQueryFiltersDayByIndexedStartTimeRange() {
    when(nativeQuery.getSingleResult()).thenReturn(13.0d);

    cdrDao.findGammaStartupAverageRingTimeAnswered(CAMPAIGN_ID);

    assertDayRangeShape(captureNativeSql());
    verify(nativeQuery).setTimeout(10);
  }

  @Test
  public void todayAnswerStatsQueryIsTimeBounded() {
    when(nativeQuery.getResultList()).thenReturn(java.util.Collections.emptyList());

    cdrDao.findGammaTodayAnswerStatsByType(CAMPAIGN_ID);

    verify(nativeQuery).setTimeout(10);
  }

  @Test
  public void averageQueriesReturnNullWhenTheyTimeOut() {
    when(nativeQuery.getSingleResult()).thenThrow(new QueryTimeoutException("timeout"));

    assertNull(cdrDao.findGammaStartupAverageTalkTime(CAMPAIGN_ID, 10));
    assertNull(cdrDao.findGammaStartupAverageRingTime(CAMPAIGN_ID));
    assertNull(cdrDao.findGammaStartupAverageRingTimeAnswered(CAMPAIGN_ID));
  }

  @Test
  public void todayAnswerStatsReturnEmptyWhenQueryTimesOut() {
    when(nativeQuery.getResultList()).thenThrow(new QueryTimeoutException("timeout"));

    List<Object[]> rows = cdrDao.findGammaTodayAnswerStatsByType(CAMPAIGN_ID);

    assertTrue(rows.isEmpty());
  }

  @Test(expected = RuntimeException.class)
  public void nonTimeoutFailureIsNotHidden() {
    when(nativeQuery.getSingleResult()).thenThrow(new RuntimeException("db failure"));

    cdrDao.findGammaStartupAverageTalkTime(CAMPAIGN_ID, 10);
  }

  private void assertDayRangeShape(String sql) {
    String normalized = sql.replaceAll("\\s+", " ");
    assertFalse("Gün filtresi indexsiz call_dial_time üzerinde CAST ile yapılmamalı",
        normalized.contains("CAST(c.call_dial_time AS DATE)"));
    assertTrue(normalized.contains("c.call_start_time >= lw.working_day"));
    assertTrue(normalized.contains("c.call_start_time < DATEADD(day, 1, lw.working_day)"));
    assertTrue("Son çalışma günü handling_start index'inden sargable koşulla okunmalı",
        normalized.contains("call_handling_start < CAST(GETDATE() AS DATE)"));
    assertTrue(normalized.contains("ORDER BY call_handling_start DESC"));
    assertFalse(normalized.contains("CAST(call_handling_start AS DATE) < CAST(GETDATE() AS DATE)"));
  }

  private String captureNativeSql() {
    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(session).createNativeQuery(sqlCaptor.capture());
    return sqlCaptor.getValue();
  }
}
