package com.powerdialer.engine.scheduler;

import com.powerdialer.engine.UserHolder;
import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.Group;
import com.powerdialer.model.Skill;
import com.powerdialer.model.User;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Queue;
import java.util.Set;

@Component
public class AutomationGroupAgentStatsProvider {

    private final UserHolder userHolder;
    private final ACDEngine acdEngine;

    public AutomationGroupAgentStatsProvider(UserHolder userHolder, ACDEngine acdEngine) {
        this.userHolder = userHolder;
        this.acdEngine = acdEngine;
    }

    public AgentStatsEvaluationContext buildContext(Campaign campaign, Group group) {
        int loginCount = countOnlineUsersInGroup(campaign, group);
        int availableCount = countIdleUsersInGroup(campaign, group);

        long groupId = group.getId() == null ? -1L : group.getId();
        String groupName = safeGroupName(group);

        return new AgentStatsEvaluationContext(groupId, groupName, loginCount, availableCount);
    }

    private int countOnlineUsersInGroup(Campaign campaign, Group group) {
        Set<Long> onlineUserIds = userHolder.getOnlineUserIds();
        if (onlineUserIds == null || onlineUserIds.isEmpty()) return 0;

        int count = 0;
        for (Long userId : onlineUserIds) {
            User u = userHolder.getUser(userId);
            if (u == null || u.getGroup() == null) continue;
            if (!group.equals(u.getGroup())) continue;
            if (!isUserEligibleForCampaign(campaign, u)) continue;
            count++;
        }
        return count;
    }

    private int countIdleUsersInGroup(Campaign campaign, Group group) {
        Queue<User> idle = acdEngine.getIdleUsersInGroup(group);
        if (idle == null || idle.isEmpty()) return 0;

        int count = 0;
        for (User u : idle) {
            if (u == null) continue;
            if (!isUserEligibleForCampaign(campaign, u)) continue;
            count++;
        }
        return count;
    }


    private boolean isUserEligibleForCampaign(Campaign campaign, User user) {
        if (campaign == null || user == null) return false;

        if (Boolean.TRUE.equals(campaign.getSkillEnabled())) {
            Set<Skill> campaignSkills = campaign.getSkills();
            if (campaignSkills != null && !campaignSkills.isEmpty()) {
                Set<Skill> userSkills = user.getSkillWeightPair() == null ? null : user.getSkillWeightPair().keySet();
                return userSkills != null && userSkills.containsAll(campaignSkills);
            }
        }
        return true;
    }

    private String safeGroupName(Group group) {
        try {
            Object name = group.getClass().getMethod("getName").invoke(group);
            return Objects.toString(name, "N/A");
        } catch (Exception ignore) {
            return "N/A";
        }
    }
}
