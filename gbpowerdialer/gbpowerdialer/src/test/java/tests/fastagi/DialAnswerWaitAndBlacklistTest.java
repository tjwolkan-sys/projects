package tests.fastagi;

import com.powerdialer.engine.UserHolder;
import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionComposition;
import com.powerdialer.engine.asterisk.fastagi.AgentDialLockRegistry;
import com.powerdialer.engine.asterisk.fastagi.DialWithAbandonTimeoutScript;
import com.powerdialer.model.Call;
import com.powerdialer.model.Group;
import com.powerdialer.model.Server;
import com.powerdialer.model.Status;
import com.powerdialer.model.User;
import com.powerdialer.redis.CallEventPublisher;
import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;
import org.asteriskjava.fastagi.AgiChannel;
import org.asteriskjava.fastagi.AgiRequest;
import org.asteriskjava.fastagi.reply.AgiReply;
import org.asteriskjava.fastagi.command.DialCommand;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.reflect.Whitebox;

import java.net.InetAddress;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T4 (dial cevap bekleme suresi) ve T5 (kara listeye yanlis kayit) davranislarini kilitler.
 *
 * <p><b>T4:</b> Dial komutuna artik {@code campaign.abandonTimeout} DEGIL sabit 5 sn verilir.
 * Abandon karari degismedi — onu hala {@code abandonTimeout} belirler.
 *
 * <p><b>T5:</b> "Kilit alinamadi" ve "statu musait degil" durumlari artik kara listeye
 * YAZILMAZ (gecici durumlar, telefon hic calmadi). Yalniz "dial gitti, baglanamadi"
 * kalici olarak yazilir.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class DialAnswerWaitAndBlacklistTest {

    /** Kodda tanimli sabitin test tarafindaki yansimasi (bkz. DIAL_ANSWER_WAIT_SEC). */
    private static final int BEKLENEN_DIAL_BEKLEME_SN = 5;

    @Mock private ACDEngine acdEngine;
    @Mock private ManagerConnectionComposition managerConnectionComposition;
    @Mock private UserHolder userHolder;
    @Mock private CallEventPublisher callEventPublisher;

    private DialWithAbandonTimeoutScript script;
    private AgiRequest mockAgiRequest;
    private AgiChannel mockAgiChannel;
    private AgiReply mockAgiReply;
    private Call call;
    private AgentDialLockRegistry lockRegistry;

    @Before
    public void init() throws Exception {
        LoggerDecorator loggerDecorator = mock(LoggerDecorator.class, RETURNS_DEEP_STUBS);
        script = spy(new DialWithAbandonTimeoutScript(loggerDecorator));
        lockRegistry = new AgentDialLockRegistry();

        Whitebox.setInternalState(script, "acdEngine", acdEngine);
        Whitebox.setInternalState(script, "managerConnectionComposition", managerConnectionComposition);
        Whitebox.setInternalState(script, "userHolder", userHolder);
        Whitebox.setInternalState(script, "callEventPublisher", callEventPublisher);
        Whitebox.setInternalState(script, "agentDialLockRegistry", lockRegistry);

        mockAgiRequest = mock(AgiRequest.class);
        mockAgiChannel = mock(AgiChannel.class);
        mockAgiReply = mock(AgiReply.class);

        call = mock(Call.class, RETURNS_DEEP_STUBS);
        when(call.getTelephonyResultCode()).thenReturn(null);
        when(call.getId()).thenReturn("dl-1");
        when(call.getCampaign().isPredictiveCampaign()).thenReturn(false);
        // Abandon timeout BILEREK 60: dial'e bu degerin GITMEDIGINI ispatlamak icin.
        when(call.getCampaign().getAbandonTimeout()).thenReturn(60);

        doNothing().when(script).setVariable(any(), any());
        doReturn(0).when(script).sendAgentCalledByDialerEvent(any());
        doReturn(0).when(script).exec(any(), any());
        doReturn("").when(script).getFullVariable(any());

        doReturn(call).when(managerConnectionComposition).getOriginatedCall("dl-1");
        doReturn("dl-1").when(mockAgiChannel).getVariable(Constants.MANAGER_ACTION_VARIABLE.ORIGIN_ID.getParam());
        doReturn(InetAddress.getByName("127.0.0.1")).when(mockAgiRequest).getRemoteAddress();
    }

    // ==================================================================== T4

    /**
     * T4 ASIL KAPI: Dial komutuna abandonTimeout (60) DEGIL, sabit 5 sn gider.
     */
    @Test
    public void t4_dialKomutunaSabit5SaniyeGider() throws Exception {
        when(call.getUser()).thenReturn(user(1L, "81341"));
        when(call.isAnsweredByUser()).thenReturn(true);

        ArgumentCaptor<DialCommand> captor = ArgumentCaptor.forClass(DialCommand.class);
        doReturn(mockAgiReply).when(script).sendCommand(any(DialCommand.class));

        script.service(mockAgiRequest, mockAgiChannel);

        verify(script, atLeastOnce()).sendCommand(captor.capture());
        // DialCommand'in timeout getter'i yok; uretilen AGI komut metni uzerinden dogrulanir.
        // Format: EXEC "dial" "SIP/...","<timeout>","<options>"
        String komut = captor.getAllValues().get(0).buildCommand();

        assertTrue("Dial bekleme suresi sabit " + BEKLENEN_DIAL_BEKLEME_SN + " sn olmali. Komut: " + komut,
                komut.contains("\"" + BEKLENEN_DIAL_BEKLEME_SN + "\""));
        assertTrue("abandonTimeout (60) dial'e GITMEMELI. Komut: " + komut,
                !komut.contains("\"60\""));
    }

    // ==================================================================== T5

    /**
     * T5 ASIL KAPI: kilit baska cagride iken agent kara listeye YAZILMAZ.
     *
     * <p>Senaryo: agent'in kilidi baska bir cagri tarafindan tutuluyor -> ilk deneme SKIPPED.
     * ACD ayni agent'i tekrar onerdiginde, kara liste OLMADIGI icin tekrar denenebilmeli.
     * Eski davranista agent kalici yaniyor ve dongу bosa donuyordu.
     */
    @Test
    public void t5_kilitAlinamadiginda_agentKaraListeyeYazilmaz() throws Exception {
        User agent = user(1L, "81341");
        when(call.getUser()).thenReturn(agent);
        when(call.isAnsweredByUser()).thenReturn(false);

        // Kilit BASKA bir cagri tarafindan tutuluyor -> tryLock false doner.
        lockRegistry.tryLock(1L, "baska-cagri");

        final int[] acdCagriSayisi = {0};
        doAnswer(inv -> {
            acdCagriSayisi[0]++;
            if (acdCagriSayisi[0] == 1) {
                // ACD ayni agent'i TEKRAR oneriyor. Kara liste olsaydi reddedilirdi.
                lockRegistry.release(1L, "baska-cagri");   // kilit serbest kaldi
                return null;
            }
            when(call.getUser()).thenReturn(null);          // dongunun sonlanmasi icin
            return null;
        }).when(acdEngine).processACD(any(Call.class));

        doReturn(mockAgiReply).when(script).sendCommand(any(DialCommand.class));
        doReturn("ANSWER").when(script).getFullVariable("${DIALSTATUS}");

        script.service(mockAgiRequest, mockAgiChannel);

        // Agent kara listede olmadigi icin ACD'nin onerisi kabul edilip TEKRAR dial edilmeli.
        verify(script, atLeastOnce()).sendCommand(any(DialCommand.class));
    }

    /**
     * T5 KORUMA KAPISI: "dial gitti, baglanamadi" kaydi KALICI kalmali.
     *
     * <p>Agent aranir, cevap gelmez -> NOT_ANSWERED -> kara listeye yazilir. ACD ayni agent'i
     * tekrar onerdiginde REDDEDILMELI: toplam dial sayisi 1'de kalmalidir.
     *
     * <p>Sonsuz donguyu onlemek icin abandonTimeout 1 sn'ye cekilir; ilk turdan sonra sure dolar.
     */
    @Test
    public void t5_cevapVermeyenAgent_kaliciOlarakYazilirVeTekrarAranmaz() throws Exception {
        when(call.getCampaign().getAbandonTimeout()).thenReturn(1);
        User agent = user(1L, "81341");
        when(call.getUser()).thenReturn(agent);
        when(call.isAnsweredByUser()).thenReturn(false);

        // ACD her cagrilisinda AYNI agent'i oneriyor (call.getUser degismiyor).
        doAnswer(inv -> null).when(acdEngine).processACD(any(Call.class));

        doReturn(mockAgiReply).when(script).sendCommand(any(DialCommand.class));
        doReturn("").when(script).getFullVariable("${DIALSTATUS}");   // cevap YOK
        doNothing().when(script).hangup();

        script.service(mockAgiRequest, mockAgiChannel);

        // Kalici kara liste sayesinde ayni agent'a IKINCI bir dial gitmemeli.
        verify(script, times(1)).sendCommand(any(DialCommand.class));
    }

    // ==================================================================== yardimcilar

    private User user(long id, String username) {
        Status status = new Status();
        status.setAvailableForDialerCalls(true);
        Server server = new Server();
        server.setIp("192.168.1.1");
        Group group = new Group();
        group.setId(1L);
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setStatus(status);
        user.setServer(server);
        user.setGroup(group);
        return user;
    }
}
