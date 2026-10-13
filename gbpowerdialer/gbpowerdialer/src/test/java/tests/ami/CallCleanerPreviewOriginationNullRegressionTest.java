package tests.ami;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.powerdialer.engine.CallCleaner;
import com.powerdialer.engine.CallUpToDateKeeper;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.Status;
import com.powerdialer.model.User;
import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;
import com.powerdialer.util.Utils;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * FIX-333607: user status değişiminde preview çağrı temizliği, henüz originate edilmemiş
 * ({@code originationDate == null}) çağrıda NPE atıyordu; tarih dönüşümü null kontrolünden önce
 * yapılıyordu (02.10.2026'da 3 kez, {@code UserSubscriber.handleStatusChange}).
 */
public class CallCleanerPreviewOriginationNullRegressionTest {

  @Test
  public void callWithoutOriginationDateDoesNotBreakPreviewCleanup() {
    LoggerDecorator loggerDecorator = mock(LoggerDecorator.class);
    when(loggerDecorator.getLogger(CallCleaner.class)).thenReturn(loggerDecorator);
    CallCleaner callCleaner = new CallCleaner(loggerDecorator);
    CallUpToDateKeeper keeper = mock(CallUpToDateKeeper.class);
    ReflectionTestUtils.setField(callCleaner, "callUpToDateKeeper", keeper);

    Status available = mock(Status.class);
    when(available.isAvailableForDialerCalls()).thenReturn(true);
    User user = mock(User.class);
    when(user.getStatus()).thenReturn(available);

    Call notOriginated = new Call();
    notOriginated.setCampaign(mock(Campaign.class));
    when(keeper.getUserCalls(user)).thenReturn(Collections.singletonList(notOriginated));

    callCleaner.clearExpiredPreviewCallsForUser(user);
  }

  /**
   * Null kontrolü yalnız originate edilmemiş çağrıyı atlar; originate edilmiş ve süresi geçmiş preview
   * çağrısı eskisi gibi temizlenir, süresi geçmemiş olana dokunulmaz. Eskiden listedeki null tarihli
   * çağrı NPE ile döngüyü (ve {@code handleStatusChange}'in geri kalanını) kestiği için ondan sonraki
   * çağrılar hiç kontrol edilmiyordu.
   */
  @Test
  public void expiredPreviewCallAfterNotOriginatedCallIsStillCleared() {
    LoggerDecorator loggerDecorator = mock(LoggerDecorator.class);
    when(loggerDecorator.getLogger(CallCleaner.class)).thenReturn(loggerDecorator);
    CallCleaner callCleaner = spy(new CallCleaner(loggerDecorator));
    CallUpToDateKeeper keeper = mock(CallUpToDateKeeper.class);
    ReflectionTestUtils.setField(callCleaner, "callUpToDateKeeper", keeper);
    doNothing().when(callCleaner).clearExpiredCall(any(Call.class));

    Status available = mock(Status.class);
    when(available.isAvailableForDialerCalls()).thenReturn(true);
    User user = mock(User.class);
    when(user.getStatus()).thenReturn(available);
    Campaign preview = mock(Campaign.class);
    when(preview.isPreviewCampaign()).thenReturn(true);

    Call notOriginated = previewCall("dl-not-originated", preview, null);
    Call expired = previewCall("dl-expired", preview, Utils.convertDateToDouble(new Date(System.currentTimeMillis() - Constants.CALL_EXPIRE_TIMEOUT - 60_000L)));
    Call recent = previewCall("dl-recent", preview, Utils.convertDateToDouble(new Date()));
    when(keeper.getUserCalls(user)).thenReturn(Arrays.asList(notOriginated, expired, recent));

    callCleaner.clearExpiredPreviewCallsForUser(user);

    verify(callCleaner).clearExpiredCall(expired);
    verify(callCleaner, never()).clearExpiredCall(notOriginated);
    verify(callCleaner, never()).clearExpiredCall(recent);
  }

  /** {@code Call.equals} id'ye bakar; çağrılar ayrı id taşır. */
  private static Call previewCall(String id, Campaign campaign, Double originationDate) {
    Call call = new Call();
    call.setId(id);
    call.setCampaign(campaign);
    call.setOriginationDate(originationDate);
    return call;
  }
}
