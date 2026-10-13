package com.powerdialer.api;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.powerdialer.service.ACDEngineService;
import lombok.RequiredArgsConstructor;
import org.dozer.Mapper;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import com.powerdialer.dto.UserDTOExtended;

/**
 * Rest controller providing campaigns information
 */
@RestController
@RequestMapping(value = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ACDEngineResource {

    private final ACDEngineService acdEngineService;

    private final Mapper mapper;

    @GetMapping("/groups/{groupId}/users/idle")
    public List<UserDTOExtended> getIdleUsersByGroup(@PathVariable Long groupId) {
        return acdEngineService.getIdleUsersByGroup(groupId).stream()
                .map(user -> mapper.map(user, UserDTOExtended.class))
                .collect(Collectors.toList());
    }

    @GetMapping("/groups/{groupId}/users/available")
    public List<UserDTOExtended> getAvailableUsersQueueInGroup(@PathVariable Long groupId) {
        return acdEngineService.getAvailableUsersQueueInGroup(groupId).stream()
                .map(user -> mapper.map(user, UserDTOExtended.class))
                .collect(Collectors.toList());
    }

    @GetMapping("/groups/{groupId}/users/available/calls")
    public Map<Long, Integer> getAvailableUserToCallsCount(@PathVariable Long groupId) {
        return acdEngineService.getAvailableUserToCallsCount(groupId);
    }

    @GetMapping("/campaigns/{campaignId}/users")
    public List<UserDTOExtended> getCampaignUsers(@PathVariable Long campaignId) {
        return acdEngineService.getCampaignUsers(campaignId).stream()
                .map(user -> mapper.map(user, UserDTOExtended.class))
                .collect(Collectors.toList());
    }

    @GetMapping("/campaigns/{campaignId}/users/only")
    public List<UserDTOExtended> getOnlyCampaignUsers(@PathVariable Long campaignId) {
        return acdEngineService.getOnlyCampaignUsers(campaignId).stream()
                .map(user -> mapper.map(user, UserDTOExtended.class))
                .collect(Collectors.toList());
    }

    @GetMapping("/campaigns/{campaignId}/users/{userId}/online")
    public boolean isCampaignHasOnlineUser(@PathVariable Long campaignId, @PathVariable Long userId) {
        return acdEngineService.isCampaignHasOnlineUser(campaignId, userId);
    }

    @GetMapping("/campaigns/{campaignId}/users/count")
    public int getCampaignUsersCount(@PathVariable Long campaignId) {
        return acdEngineService.getCampaignUsersCount(campaignId);
    }

    @GetMapping("/campaigns/{campaignId}/users/available/count")
    public long getCampaignAvailableUsersCount(@PathVariable Long campaignId) {
        return acdEngineService.getCampaignAvailableUsersCount(campaignId);
    }

    @GetMapping("/campaigns/{campaignId}/users/idle")
    public Set<UserDTOExtended> getCampaignIdleUsers(@PathVariable Long campaignId) {
        return acdEngineService.getCampaignIdleUsers(campaignId).stream()
                .map(user -> mapper.map(user, UserDTOExtended.class))
                .collect(Collectors.toSet());
    }

    @GetMapping("/campaigns/{campaignId}/users/idle/count")
    public int getCampaignIdleUsersCount(@PathVariable Long campaignId) {
        return acdEngineService.getCampaignIdleUsersCount(campaignId);
    }

    @GetMapping("/campaigns/{campaignId}/users/only/count")
    public int getOnlyCampaignUsersCount(@PathVariable Long campaignId) {
        return acdEngineService.getOnlyCampaignUsersCount(campaignId);
    }

    @GetMapping("/users/idle")
    public List<UserDTOExtended> getAllIdleUsers() {
        return acdEngineService.getIdleUsers().stream()
                .map(user -> mapper.map(user, UserDTOExtended.class))
                .collect(Collectors.toList());
    }
}
