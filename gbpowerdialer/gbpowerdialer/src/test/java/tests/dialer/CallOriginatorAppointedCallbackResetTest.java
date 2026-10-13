package tests.dialer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingDeque;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.reflect.Whitebox;

import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionDecorator;
import com.powerdialer.engine.dialer.CallOriginator;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.util.Constants;
import com.powerdialer.util.Constants.APPOINTER_RESET_STRATEGY;

/**
 * FEAT-321997: CallOriginator.processCall appointed-callback dalında strateji bazlı yönlendirmeyi kilitler.
 *
 * ATTEMPT_COUNT + sahip atanamadı (AVAILABLE değil; online-meşgul VEYA offline farketmez)
 *   -> rescheduleAppointedCallbackAsync(campaign, call) (deneme sayacı +1; eşikte campaign-wide).
 * TIMER (isAppointerResetByAttempt=false)
 *   -> legacy returnCallToDb (removeFetchedDataFlagFromCallsAsync); reschedule ÇAĞRILMAZ.
 *
 * Not: ATTEMPT modunda originator artık owner'ın online/offline ayrımını yapmaz (uniform attempt model);
 * her iki durumda da reschedule/deneme-say yolu işler.
 *
 * Mock kurulumu CallOriginatorTest deseniyle aynı: @InjectMocks @Spy CallOriginator + deep-stub
 * ManagerConnectionDecorator. processCall private olduğundan Whitebox ile çağrılır.
 */
@RunWith(MockitoJUnitRunner.class)
public class CallOriginatorAppointedCallbackResetTest {

  private static final Long OWNER_ID = 7L;

  private Campaign campaign;

  @InjectMocks
  @Spy
  private CallOriginator callOriginator;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private ManagerConnectionDecorator managerConnectionDecorator;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private DialerFacade dialerFacade;

  private ACDEngine acdMock;
  private CampaignDbOperations dbOperationsMock;

  @Before
  public void setUp() {
    campaign = new Campaign();
    campaign.setType(Constants.CAMPAIGN_TYPE.PREVIEW_STRAIGHT);
    campaign.setAppointerReset(false);
    campaign.setAllowCallback(true);
    campaign.setCallbackToAppointer(true);
    campaign.setColResultCallbackDate("callback_date");
    campaign.setColResultUserId("user_id");

    acdMock = managerConnectionDecorator.getConnectionComposition().getAcdEngine();
    dbOperationsMock = managerConnectionDecorator.getConnectionComposition().getCampaignDbOperations();
    // Async DB sarmalayıcıları tamamlanmış future döndürsün (exceptionally chain NPE atmasın).
    doReturn(CompletableFuture.completedFuture(null))
        .when(dbOperationsMock).rescheduleAppointedCallbackAsync(any(Campaign.class), any(Call.class));
    doReturn(CompletableFuture.completedFuture(null))
        .when(dbOperationsMock).removeFetchedDataFlagFromCallsAsync(any(Campaign.class), any(List.class));
  }

  @Test
  public void attemptCountStrategyReschedulesWhenOwnerNotAvailable() throws Exception {
    campaign.setAppointerResetStrategy(APPOINTER_RESET_STRATEGY.ATTEMPT_COUNT);

    invokeProcessCall(appointedCall());

    // Sahip AVAILABLE değil (online-meşgul veya offline) -> reschedule/deneme-say (uniform).
    verify(dbOperationsMock).rescheduleAppointedCallbackAsync(eq(campaign), any(Call.class));
    verify(dbOperationsMock, never()).removeFetchedDataFlagFromCallsAsync(eq(campaign), any(List.class));
  }

  @Test
  public void timerStrategyUsesLegacyReturnToDbWithoutReschedule() throws Exception {
    // Strateji TIMER (varsayılan/null) -> isAppointerResetByAttempt=false -> legacy returnCallToDb.
    campaign.setAppointerResetStrategy(APPOINTER_RESET_STRATEGY.TIMER);

    invokeProcessCall(appointedCall());

    verify(dbOperationsMock).removeFetchedDataFlagFromCallsAsync(eq(campaign), any(List.class));
    verify(dbOperationsMock, never()).rescheduleAppointedCallbackAsync(any(Campaign.class), any(Call.class));
  }

  private void invokeProcessCall(Call call) throws Exception {
    BlockingDeque<Call> callQueue = new LinkedBlockingDeque<>();
    callQueue.addAll(Arrays.asList(call));
    // ACD sahibi available bulamaz -> user null kalır, appointed-callback dalına düşer.
    org.mockito.Mockito.doNothing().when(acdMock).processACD(org.mockito.ArgumentMatchers.any());
    Method processCall = Whitebox.getMethod(CallOriginator.class, "processCall", BlockingDeque.class, Call.class);
    processCall.invoke(callOriginator, callQueue, call);
  }

  private Call appointedCall() {
    Call call = new Call();
    call.setId("c1");
    call.setCampaign(campaign);
    call.setCalledNumber("ANY_NUMBER");
    // getUserIdAppointedToCallback != null için user_id rawData'da olmalı.
    call.setRawData(new JSONObject().put("user_id", OWNER_ID));
    return call;
  }
}
