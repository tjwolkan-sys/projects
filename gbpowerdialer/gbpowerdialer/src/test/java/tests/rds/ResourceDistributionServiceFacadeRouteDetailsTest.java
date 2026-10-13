package tests.rds;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.mockito.ArgumentCaptor;

import com.globalbilgi.gbqueueselectionservice.grpc.QueueSelectionServiceGrpc;
import com.globalbilgi.gbqueueselectionservice.grpc.RouteDetailEntry;
import com.globalbilgi.gbqueueselectionservice.grpc.RouteDetailsRequest;
import com.globalbilgi.gbqueueselectionservice.grpc.RouteDetailsResponse;
import com.powerdialer.exceptions.RouteDetailsUnavailableException;
import com.powerdialer.rds.ResourceDistributionServiceFacade;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class ResourceDistributionServiceFacadeRouteDetailsTest {

  private ResourceDistributionServiceFacade facade;
  private QueueSelectionServiceGrpc.QueueSelectionServiceBlockingStub blockingStub;

  @Before
  public void setUp() {
    facade = new ResourceDistributionServiceFacade();
    blockingStub = mock(QueueSelectionServiceGrpc.QueueSelectionServiceBlockingStub.class);
    ReflectionTestUtils.setField(facade, "blockingStub", blockingStub);
  }

  /**
   * Erişim hatası artık boş listeden AYRI sinyal veriyor. Eskiden ikisi de boş liste dönüyordu ve
   * geçici bir QSS/Eureka kesintisinde çağrı retry'sız iptal ediliyordu
   * (FIX-GLB90114534-FCT-CALLERID-CONTEXT-VE-LIST-RETRY, kalem 2).
   */
  @Test(expected = RouteDetailsUnavailableException.class)
  public void shouldThrowWhenChannelNotConfigured() throws Exception {
    ReflectionTestUtils.setField(facade, "blockingStub", null);

    facade.getRouteDetails(123L);
  }

  @Test
  public void shouldMapResponseEntriesInOrder() throws Exception {
    RouteDetailsResponse response = RouteDetailsResponse.newBuilder()
        .addEntries(entry("0501", "Manual", 1))
        .addEntries(entry("0502", "Retry", 2))
        .addEntries(entry("0503", "Dialer First", 0))
        .build();
    when(blockingStub.getRouteDetails(any(RouteDetailsRequest.class))).thenReturn(response);

    List<RouteDetailEntry> entries = facade.getRouteDetails(456L);

    assertThat(entries.size(), is(3));
    assertThat(entries.get(0).getPhoneNumber(), is("0501"));
    assertThat(entries.get(1).getPhoneNumber(), is("0502"));
    assertThat(entries.get(2).getPhoneNumber(), is("0503"));
  }

  @Test
  public void shouldThrowWithCauseWhenStubThrows() {
    RuntimeException grpcFailure = new RuntimeException("simulated grpc failure");
    when(blockingStub.getRouteDetails(any(RouteDetailsRequest.class))).thenThrow(grpcFailure);

    try {
      facade.getRouteDetails(789L);
      fail("gRPC hatasi bos liste degil RouteDetailsUnavailableException uretmeli");
    } catch (RouteDetailsUnavailableException e) {
      assertThat("teshis icin cause zincire eklenmeli", e.getCause(), is((Throwable) grpcFailure));
    }
  }

  /** QSS sözleşmesi: grup bulunamazsa boş liste döner ve bu hata DEĞİLDİR. */
  @Test
  public void shouldReturnEmptyListWhenGroupHasNoRoutes() throws Exception {
    when(blockingStub.getRouteDetails(any(RouteDetailsRequest.class)))
        .thenReturn(RouteDetailsResponse.newBuilder().build());

    List<RouteDetailEntry> entries = facade.getRouteDetails(999L);

    assertTrue("bos grup exception degil bos liste uretmeli", entries.isEmpty());
  }

  /**
   * QSS sözleşmesi grup bazlıdır: istek yalnız {@code groupId} taşır. PD bir dönem
   * {@code calledNumber} de gönderiyordu (ebb5188d) ama QSS bunu hiç okumuyordu ve canonical
   * proto'da alan bulunmuyor. Çağrı tipine/denemeye göre süzme bu yüzden tüketen tarafta
   * ({@code RouteDetailsFilter}) yapılır.
   */
  @Test
  public void shouldSendOnlyGroupIdInRequest() throws Exception {
    when(blockingStub.getRouteDetails(any(RouteDetailsRequest.class)))
        .thenReturn(RouteDetailsResponse.newBuilder().build());
    ArgumentCaptor<RouteDetailsRequest> captor = ArgumentCaptor.forClass(RouteDetailsRequest.class);

    facade.getRouteDetails(456L);

    verify(blockingStub).getRouteDetails(captor.capture());
    assertThat(captor.getValue().getGroupId(), is(456L));
  }

  /** Trunk alanı (field 5) PD tarafında yorumlanmadan olduğu gibi taşınır. */
  @Test
  public void shouldCarryTrunkFieldThrough() throws Exception {
    RouteDetailsResponse response = RouteDetailsResponse.newBuilder()
        .addAllEntries(Arrays.asList(entry("0501", "Manual", 1, "ININ"), entry("0502", "Retry", 2, "")))
        .build();
    when(blockingStub.getRouteDetails(any(RouteDetailsRequest.class))).thenReturn(response);

    List<RouteDetailEntry> entries = facade.getRouteDetails(456L);

    assertThat(entries.get(0).getTrunk(), is("ININ"));
    assertThat("tanimsiz trunk bos string doner, null degil", entries.get(1).getTrunk(), is(""));
  }

  private RouteDetailEntry entry(String phone, String type, int attempts) {
    return entry(phone, type, attempts, "");
  }

  private RouteDetailEntry entry(String phone, String type, int attempts, String trunk) {
    return RouteDetailEntry.newBuilder()
        .setPhoneNumber(phone)
        .setType(type)
        // QSS canonical proto'da attempts string alandır (FEAT-GLB90114534-FCT-KANAL-KAPASITE-GATE).
        .setAttempts(String.valueOf(attempts))
        .setTrunk(trunk)
        .build();
  }
}
