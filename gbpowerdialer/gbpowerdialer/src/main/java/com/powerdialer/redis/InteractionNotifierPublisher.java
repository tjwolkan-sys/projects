package com.powerdialer.redis;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerdialer.dao.impl.CDRDao;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.model.CDR;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.User;
import com.powerdialer.redis.event.interactionnotifier.dto.DialerCallReportDTO;
import com.powerdialer.redis.event.interactionnotifier.mapper.CdrInMapper;
import com.powerdialer.redis.event.interactionnotifier.mapper.DialerCallReportMapper;
import com.powerdialer.service.CDRService;
import com.powerdialer.service.UserService;
import com.powerdialer.util.ApplicationUtil;
import com.powerdialer.util.Constants;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Optional;
import java.util.TimeZone;

/**
 * Publisher component responsible for broadcasting dialer call reports
 * to the Redis channel for interaction notification purposes.
 *
 * <p>Before publishing, raw call data is enriched with the full database row
 * so that all columns — including null-valued ones — are present in the
 * outgoing event payload.</p>
 */
@Component
@RequiredArgsConstructor
public class InteractionNotifierPublisher {

    private static final Logger log = LoggerFactory.getLogger(InteractionNotifierPublisher.class);

    /** Jackson mapper configured with Istanbul timezone and null-inclusive serialization. */
    private ObjectMapper objectMapper;

    /** Local IP address of this application instance, used to identify the publisher node. */
    private String publisher;

    private final RedisUtil redisUtil;
    private final ApplicationUtil applicationUtil;
    private final CampaignDbOperations campaignDbOperations;
    private final UserService userService;
    private final CDRService cdrService;
    private final CDRDao cdrDao;

    // Shared DTO instance — enriched in stages before publish
    private static DialerCallReportDTO dto;

    /**
     * Initializes the publisher after dependency injection.
     * Resolves the local IP address and configures the ObjectMapper.
     */
    @PostConstruct
    public void init() {
        publisher = applicationUtil.getThisApplicationLocalIpAddress();
        log.debug("Got publisher info: {}", publisher);

        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        dateFormat.setTimeZone(TimeZone.getTimeZone("Europe/Istanbul"));

        objectMapper = new ObjectMapper();
        objectMapper.setDateFormat(dateFormat);
        // Include null values so that all campaign columns appear in dynamicFields
        objectMapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
    }

    /**
     * Enriches the call's raw data with the full database row, maps it to a
     * {@link DialerCallReportDTO}, and publishes the result to Redis.
     *
     * @param call the call entity to be reported
     */
    public void publishDialerCallReport(Call call) {
        enrichRawDataWithFullRow(call);
        dto = DialerCallReportMapper.toDto(call, resolveReportAgent(call));
        enrichCdrFields(call);
        publish(dto);
    }

    /**
     * Resolves the agent used for the report by refreshing it from persistence, WITHOUT writing it
     * back onto the live {@link Call}.
     *
     * <p>Earlier this method was {@code enrichUser(call)} and did
     * {@code call.setUser(userService.getUserById(id))}. The entity returned there is detached and
     * {@code User.group} is a lazy {@code @ManyToOne}, so it carries an uninitialized Hibernate
     * proxy. The very same {@code Call} instance is registered in {@code CallUpToDateKeeper} while it
     * is in flight, so that write leaked a dead proxy into shared runtime state: every subsequent
     * ACD scan ({@code getCallsByUserGroup}) threw {@code LazyInitializationException}, the
     * {@code CallOriginator} tick collapsed and origination stalled on that server until the call was
     * unregistered (10.08.2026 production incident).</p>
     *
     * <p>The report still needs the persisted agent because DB-only fields such as
     * {@code adNickname} are absent on the in-memory (Redis) copy; the refreshed entity is therefore
     * handed to the mapper only. Falling back to the in-memory agent when persistence returns null
     * keeps the previous behaviour.</p>
     */
    private User resolveReportAgent(Call call) {
        User user = call.getUser();
        if (user == null) {
            return null;
        }
        User persistedUser = userService.getUserById(user.getId());
        return persistedUser != null ? persistedUser : user;
    }

    private void enrichCdrFields(Call call) {
        CDR cdr = call.getCdr();

        Optional.ofNullable(cdr)
                .map(CDR::getInteractionId)
                .map(cdrService::getByInteractionId)
                .map(CdrInMapper::toDto)
                .ifPresent(dto::setPdCdr2);

        Optional.ofNullable(cdr)
                .map(CDR::getId)
                .map(cdrDao::get)
                .map(CdrInMapper::toDto)
                .ifPresent(dto::setPdCdr3);

        Optional.ofNullable(call.getId())
                .map(cdrDao::getByInteractionId)
                .map(CdrInMapper::toDto)
                .ifPresent(dto::setPdCdr4);
    }

    /**
     * Fetches the complete database row for the given call and merges any missing
     * columns — including null-valued ones — into the call's raw data.
     *
     * <p>This ensures that {@code dynamicFields} in the published payload contains
     * every campaign table column, not just the ones that carried a non-null value
     * at fetch time.</p>
     *
     * @param call the call whose raw data will be enriched
     */
    private void enrichRawDataWithFullRow(Call call) {
        Campaign campaign = call.getCampaign();
        if (campaign == null || call.getUniqueKey() == null) return;

        try {
            JSONObject fullRow = campaignDbOperations.fetchFullRowByUniqueKey(
                    campaign, call.getUniqueKey()
            );
            if (fullRow == null) return;

            // Replace rawData with the full DB row so that all columns,
            // including null-valued ones, are available for dynamic field mapping.
            call.setRawData2(fullRow);

        } catch (Exception e) {
            log.warn("enrichRawDataWithFullRow failed for uniqueKey={}", call.getUniqueKey(), e);
        }
    }

    /**
     * Asynchronously serializes the given {@link DialerCallReportDTO} to JSON
     * and publishes it to the configured Redis channel.
     *
     * @param dialerCallReportDTO the DTO to be published
     */
    @Async
    protected void publish(DialerCallReportDTO dialerCallReportDTO) {
        try {
            redisUtil.publish(
                    Constants.REDIS_CHANNEL.channelGBInteractionNotifier.toString(),
                    objectMapper.writeValueAsString(dialerCallReportDTO)
            );
        } catch (Exception e) {
            log.error("Failed to publish DialerCallReportDTO: {}", dialerCallReportDTO, e);
        }

        log.debug("DialerCallReportDTO published: {}", dialerCallReportDTO);
    }
}