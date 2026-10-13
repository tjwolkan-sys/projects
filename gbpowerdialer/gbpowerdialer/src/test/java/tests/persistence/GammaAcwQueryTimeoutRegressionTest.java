package tests.persistence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

import com.powerdialer.dao.impl.CDRDao;

@RunWith(MockitoJUnitRunner.class)
public class GammaAcwQueryTimeoutRegressionTest {

  private static final long CAMPAIGN_ID = 42L;

  @Mock
  private SessionFactory sessionFactory;

  @Mock
  private Session session;

  @Mock
  @SuppressWarnings("rawtypes")
  private NativeQuery nativeQuery;

  private CDRDao cdrDao;

  @Before
  @SuppressWarnings("unchecked")
  public void setUp() {
    cdrDao = new CDRDao();
    cdrDao.setSessionFactory(sessionFactory);

    when(sessionFactory.getCurrentSession()).thenReturn(session);
    when(session.createNativeQuery(anyString())).thenReturn(nativeQuery);
    when(nativeQuery.setParameter(eq("campaignId"), eq(CAMPAIGN_ID))).thenReturn(nativeQuery);
    when(nativeQuery.setTimeout(anyInt())).thenReturn(nativeQuery);
  }

  @Test
  public void startupAcwQueryReturnsZeroWhenQueryTimesOut() {
    when(nativeQuery.getSingleResult()).thenThrow(new QueryTimeoutException("timeout"));

    Double result = cdrDao.findGammaStartupAverageAcwTime(CAMPAIGN_ID);

    assertEquals(0.0d, result, 0.0d);
    verify(nativeQuery).setTimeout(10);
  }

  @Test
  public void startupAcwQueryUsesRecentFourHundredStatusRows() {
    when(nativeQuery.getSingleResult()).thenReturn(12.0d);

    cdrDao.findGammaStartupAverageAcwTime(CAMPAIGN_ID);

    String sql = captureNativeSql();
    assertTrue(sql.contains("RecentAcwRows AS"));
    assertTrue(sql.contains("SELECT TOP 400"));
    assertTrue(sql.contains("ORDER BY US.log_date DESC"));
    assertTrue(sql.contains("RecentAcwSessions AS"));
    assertTrue(sql.contains("SELECT DISTINCT [user_id], status_session"));
    assertTrue(sql.contains("INNER JOIN RecentAcwSessions R"));
    assertTrue(sql.contains("SELECT AVG(CAST(wrapup_seconds AS FLOAT)) AS avg_wrapup_seconds"));
  }

  @Test
  public void currentAcwQueryReturnsZeroWhenQueryTimesOut() {
    when(nativeQuery.getSingleResult()).thenThrow(new QueryTimeoutException("timeout"));

    Double result = cdrDao.findGammaCurrentAverageAcwTime(CAMPAIGN_ID);

    assertEquals(0.0d, result, 0.0d);
    verify(nativeQuery).setTimeout(10);
  }

  @Test
  public void currentAcwQueryUsesRecentFourHundredStatusRows() {
    when(nativeQuery.getSingleResult()).thenReturn(12.0d);

    cdrDao.findGammaCurrentAverageAcwTime(CAMPAIGN_ID);

    String sql = captureNativeSql();
    assertTrue(sql.contains("RecentAcwRows AS"));
    assertTrue(sql.contains("SELECT TOP 400"));
    assertTrue(sql.contains("ORDER BY US.log_date DESC"));
    assertTrue(sql.contains("RecentAcwSessions AS"));
    assertTrue(sql.contains("SELECT DISTINCT [user_id], status_session"));
    assertTrue(sql.contains("INNER JOIN RecentAcwSessions R"));
    assertTrue(sql.contains("SELECT AVG(CAST(wrapup_seconds AS FLOAT)) AS avg_wrapup_seconds"));
  }

  @Test(expected = RuntimeException.class)
  public void startupAcwQueryRethrowsNonTimeoutFailures() {
    when(nativeQuery.getSingleResult()).thenThrow(new RuntimeException("db failure"));

    cdrDao.findGammaStartupAverageAcwTime(CAMPAIGN_ID);
  }

  private String captureNativeSql() {
    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(session).createNativeQuery(sqlCaptor.capture());
    return sqlCaptor.getValue();
  }
}
