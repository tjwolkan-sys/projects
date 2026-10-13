package com.powerdialer.service;

import com.powerdialer.dto.UserDTOExtended;
import com.powerdialer.dto.CampaignFormDTO;
import com.powerdialer.dto.GroupFormDTO;
import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.Group;
import com.powerdialer.model.User;
import lombok.RequiredArgsConstructor;
import org.dozer.Mapper;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ACDEngineService {

    private final ACDEngine acdEngine;

    private final CampaignService campaignService;

    private final GroupService groupService;

    private final Mapper mapper;

    public List<UserDTOExtended> getIdleUsers() {
        return acdEngine.getAllIdleUsers().stream()
                .map(user -> mapper.map(user, UserDTOExtended.class))
                .collect(Collectors.toList());
    }

    public List<UserDTOExtended> getIdleUsersByGroup(Long groupId) {
        GroupFormDTO groupDTO = groupService.get(groupId);
        Group group = mapper.map(groupDTO, Group.class);

        Queue<User> idleUsersInGroup = acdEngine.getIdleUsersInGroup(group);
        return CollectionUtils.isEmpty(idleUsersInGroup) ? Collections.emptyList() :
                idleUsersInGroup.stream().map(user -> mapper.map(user, UserDTOExtended.class)).collect(Collectors.toList());
    }

    public List<UserDTOExtended> getAvailableUsersQueueInGroup(Long groupId) {
        GroupFormDTO groupDTO = groupService.get(groupId);
        Group group = mapper.map(groupDTO, Group.class);

        Queue<User> availableUsers = acdEngine.getAvailableUsersQueueInGroup(group);
        return CollectionUtils.isEmpty(availableUsers) ? Collections.emptyList() :
                availableUsers.stream().map(user -> mapper.map(user, UserDTOExtended.class)).collect(Collectors.toList());
    }

    public Map<Long, Integer> getAvailableUserToCallsCount(Long groupId) {
        GroupFormDTO groupDTO = groupService.get(groupId);
        Group group = mapper.map(groupDTO, Group.class);

        return acdEngine.getAvailableUserToCallsCount(group)
                .entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().getId(), Map.Entry::getValue));
    }

    public List<UserDTOExtended> getCampaignUsers(Long campaignId) {
        CampaignFormDTO campaignDTO = campaignService.get(campaignId);
        Campaign campaign = mapper.map(campaignDTO, Campaign.class);

        return acdEngine.getCampaignUsers(campaign).stream()
                .map(user -> mapper.map(user, UserDTOExtended.class))
                .collect(Collectors.toList());
    }

    public boolean isCampaignHasOnlineUser(Long campaignId, Long userId) {
        CampaignFormDTO campaignDTO = campaignService.get(campaignId);
        Campaign campaign = mapper.map(campaignDTO, Campaign.class);

        return acdEngine.isCampaignHasOnlineUser(campaign, userId);
    }

    public int getCampaignUsersCount(Long campaignId) {
        CampaignFormDTO campaignDTO = campaignService.get(campaignId);
        Campaign campaign = mapper.map(campaignDTO, Campaign.class);

        return acdEngine.getCampaignUsersCount(campaign);
    }

    public long getCampaignAvailableUsersCount(Long campaignId) {
        CampaignFormDTO campaignDTO = campaignService.get(campaignId);
        Campaign campaign = mapper.map(campaignDTO, Campaign.class);

        return acdEngine.getCampaignAvailableUsersCount(campaign);
    }

    public List<UserDTOExtended> getCampaignIdleUsers(Long campaignId) {
        CampaignFormDTO campaignDTO = campaignService.get(campaignId);
        Campaign campaign = mapper.map(campaignDTO, Campaign.class);

        Set<User> users = acdEngine.getCampaignIdleUsers(campaign);
        return users.stream().map(user -> mapper.map(user, UserDTOExtended.class)).collect(Collectors.toList());
    }

    public int getCampaignIdleUsersCount(Long campaignId) {
        CampaignFormDTO campaignDTO = campaignService.get(campaignId);
        Campaign campaign = mapper.map(campaignDTO, Campaign.class);

        return acdEngine.getCampaignIdleUsersCount(campaign);
    }

    public List<UserDTOExtended> getOnlyCampaignUsers(Long campaignId) {
        CampaignFormDTO campaignDTO = campaignService.get(campaignId);
        Campaign campaign = mapper.map(campaignDTO, Campaign.class);
        List<User> users = acdEngine.getOnlyCampaignUsers(campaign);
        return users.stream().map(user -> mapper.map(user, UserDTOExtended.class)).collect(Collectors.toList());
    }

    public int getOnlyCampaignUsersCount(Long campaignId) {
        CampaignFormDTO campaignDTO = campaignService.get(campaignId);
        Campaign campaign = mapper.map(campaignDTO, Campaign.class);
        return acdEngine.getOnlyCampaignUsersCount(campaign);
    }
}
