package tests.ami;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletContext;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.powerdialer.engine.CallCleaner;
import com.powerdialer.engine.asterisk.CallerIdManager;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.dialer.CallOriginator;
import com.powerdialer.model.CDR;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.TelephonyResultCode;
import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;

@RunWith(MockitoJUnitRunner.class)
public class CallCleanerTelephonyResultRegressionTest {

  @Mock
  private LoggerDecorator loggerDecorator;

  @Mock
  private ServletContext servletContext;

  @Mock
  private CampaignDbOperations dbOperations;

  @Mock
  private CallerIdManager callerIdManager;

  @Mock
  private CallOriginator callOriginator;

  @Mock
  private Campaign campaign;

  private CallCleaner callCleaner;
  private TelephonyResultCode amiResult;
  private TelephonyResultCode expiredResult;

  @Before
  public void setUp() {
    when(loggerDecorator.getLogger(CallCleaner.class)).thenReturn(loggerDecorator);
    callCleaner = new CallCleaner(loggerDecorator);
    ReflectionTestUtils.setField(callCleaner, "context", servletContext);
    ReflectionTestUtils.setField(callCleaner, "dbOperations", dbOperations);
    ReflectionTestUtils.setField(callCleaner, "callerIdManager", callerIdManager);
    when(servletContext.getAttribute(Constants.TELEPHONY_RESULT_CODES)).thenReturn(telephonyResultCodes());
  }

  @Test
  public void shouldNotOverwritePersistedAmiResultWithExpiredClearing() {
    Call call = persistedCall();
    call.setTelephonyResultCode(amiResult);

    callCleaner.clearExpiredCall(call);

    assertThat(call.getTelephonyResultCode(), sameInstance(amiResult));
    verify(callOriginator).saveCDR(call);
  }

  @Test
  public void shouldKeepExpiredClearingWhenAmiResultWasNeverReceived() {
    Call call = persistedCall();

    callCleaner.clearExpiredCall(call);

    assertThat(call.getTelephonyResultCode(), sameInstance(expiredResult));
    verify(callOriginator).saveCDR(call);
  }

  private Call persistedCall() {
    Call call = new Call();
    call.setId("dl-cleanup-1");
    call.setCampaign(campaign);
    call.setCallOriginator(callOriginator);
    CDR cdr = new CDR();
    cdr.setId(2002L);
    call.setCdr(cdr);
    return call;
  }

  private Map<Integer, TelephonyResultCode> telephonyResultCodes() {
    amiResult = telephonyResultCode(16L, Constants.TELEPHONY_RC_NORMAL_CLEARING);
    expiredResult = telephonyResultCode(901L, Constants.ERROR_RESULT_CODES.CALL_EXPIRED_CLEARING.getCode());
    Map<Integer, TelephonyResultCode> resultCodes = new HashMap<>();
    resultCodes.put(Constants.TELEPHONY_RC_NORMAL_CLEARING, amiResult);
    resultCodes.put(Constants.ERROR_RESULT_CODES.CALL_EXPIRED_CLEARING.getCode(), expiredResult);
    return resultCodes;
  }

  private TelephonyResultCode telephonyResultCode(Long id, int code) {
    TelephonyResultCode telephonyResultCode = new TelephonyResultCode();
    telephonyResultCode.setId(id);
    telephonyResultCode.setCode(code);
    telephonyResultCode.setName("TRC-" + code);
    return telephonyResultCode;
  }
}
