package com.powerdialer.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerdialer.dao.impl.CDRDao;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.model.Call;
import com.powerdialer.model.User;
import com.powerdialer.redis.event.interactionnotifier.MockFactory;
import com.powerdialer.redis.event.interactionnotifier.dto.DialerCallReportDTO;
import com.powerdialer.service.CDRService;
import com.powerdialer.service.UserService;
import com.powerdialer.util.ApplicationUtil;
import com.powerdialer.util.Constants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link InteractionNotifierPublisher}.
 *
 * <p>Because {@code @PostConstruct} is not invoked by Mockito, {@code objectMapper}
 * and {@code publisher} are injected via reflection in {@link #setUp()} to replicate
 * the state the bean has after Spring initialization.</p>
 *
 * <p>Coverage targets:
 * <ul>
 *   <li>{@code init()} — objectMapper and publisher initialized from applicationUtil</li>
 *   <li>{@code publishDialerCallReport()} — delegates to {@code publish()} with mapped DTO</li>
 *   <li>{@code publish()} — serializes DTO and calls {@code redisUtil.publish()}</li>
 *   <li>{@code publish()} — correct Redis channel used</li>
 *   <li>{@code publish()} — serialization exception caught and logged, no rethrow</li>
 *   <li>{@code publish()} — null DTO serialized gracefully (Jackson outputs "null")</li>
 * </ul>
 * </p>
 */
@RunWith(MockitoJUnitRunner.class)
public class InteractionNotifierPublisherTest {

    @Mock
    private RedisUtil redisUtil;

    @Mock
    private ApplicationUtil applicationUtil;

    @Mock
    private CampaignDbOperations campaignDbOperations;

    @Mock
    private UserService userService;

    @Mock
    private CDRService cdrService;

    @Mock
    private CDRDao cdrDao;

    @InjectMocks
    private InteractionNotifierPublisher publisher;

    /** The call used across tests. */
    private Call call;

    @Before
    public void setUp() throws Exception {
        call = MockFactory.buildCall();
        User callUser = call.getUser();

        // Simulate @PostConstruct — inject publisher IP and objectMapper manually
        when(applicationUtil.getThisApplicationLocalIpAddress()).thenReturn("127.0.0.1");
        when(userService.getUserById(callUser.getId())).thenReturn(callUser);
        publisher.init();
    }

    // -----------------------------------------------------------------------
    // init()
    // -----------------------------------------------------------------------

    /** After init(), applicationUtil.getThisApplicationLocalIpAddress() must have been called once. */
    @Test
    public void init_calledOnce_resolvesLocalIp() {
        // verify called once during setUp() + once more here = 2 total
        publisher.init();
        verify(applicationUtil, times(2)).getThisApplicationLocalIpAddress();
    }

    // -----------------------------------------------------------------------
    // publishDialerCallReport()
    // -----------------------------------------------------------------------

    /**
     * publishDialerCallReport() must result in exactly one Redis publish call
     * for a valid Call.
     */
    @Test
    public void publishDialerCallReport_validCall_publishesOnce() throws Exception {
        publisher.publishDialerCallReport(call);

        verify(redisUtil, times(1)).publish(
                eq(Constants.REDIS_CHANNEL.channelGBInteractionNotifier.toString()),
                anyString()
        );
    }

    /**
     * The JSON payload sent to Redis must contain the call uniqueKey
     * so we can assert the DTO was actually mapped and serialized.
     */
    @Test
    public void publishDialerCallReport_validCall_payloadContainsUniqueKey() throws Exception {
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

        publisher.publishDialerCallReport(call);

        verify(redisUtil).publish(anyString(), payloadCaptor.capture());
        String json = payloadCaptor.getValue();
        assertTrue("JSON payload must contain uniqueKey",
                json.contains("ukey-001"));
    }

    /**
     * The Redis channel used must be exactly
     * {@code Constants.REDIS_CHANNEL.channelGBInteractionNotifier.toString()}.
     */
    @Test
    public void publishDialerCallReport_validCall_usesCorrectChannel() throws Exception {
        ArgumentCaptor<String> channelCaptor = ArgumentCaptor.forClass(String.class);

        publisher.publishDialerCallReport(call);

        verify(redisUtil).publish(channelCaptor.capture(), anyString());
        assertEquals(Constants.REDIS_CHANNEL.channelGBInteractionNotifier.toString(),
                channelCaptor.getValue());
    }

    // -----------------------------------------------------------------------
    // publish() — directly exercised
    // -----------------------------------------------------------------------

    /**
     * publish() with a non-null DTO must call redisUtil.publish() exactly once
     * with non-empty JSON.
     */
    @Test
    public void publish_validDto_callsRedisUtilPublish() throws Exception {
        DialerCallReportDTO dto = DialerCallReportDTO.builder()
                .additional(DialerCallReportDTO.AdditionalInfo.builder()
                        .interaction_type("OUTBOUND_DIALER")
                        .build())
                .build();

        publisher.publish(dto);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(redisUtil, times(1)).publish(anyString(), captor.capture());
        assertFalse(captor.getValue().isEmpty());
    }

    /**
     * publish() must NOT throw even when ObjectMapper fails to serialize —
     * the exception must be swallowed and redisUtil.publish() must NOT be called.
     */
    @Test
    public void publish_serializationFailure_doesNotThrow() throws Exception {
        // Inject a broken ObjectMapper that always throws
        ObjectMapper broken = mock(ObjectMapper.class);
        when(broken.writeValueAsString(any())).thenThrow(new RuntimeException("serialize error"));

        setPrivateField(publisher, "objectMapper", broken);

        // Must not throw
        publisher.publish(DialerCallReportDTO.builder().build());

        // redisUtil.publish should NOT have been called
        verify(redisUtil, never()).publish(anyString(), anyString());
    }

    /**
     * publish() with a null DTO must still call redisUtil.publish()
     * (Jackson serializes null as the string "null").
     */
    @Test
    public void publish_nullDto_publishesNullJson() throws Exception {
        publisher.publish(null);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(redisUtil, times(1)).publish(anyString(), captor.capture());
        assertEquals("null", captor.getValue());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Reflectively sets a private field on the target instance.
     * Used to override {@code objectMapper} after {@code @PostConstruct}.
     *
     * @param target    object whose field will be set
     * @param fieldName name of the private field
     * @param value     new value to assign
     */
    private static void setPrivateField(Object target, String fieldName, Object value)
            throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
