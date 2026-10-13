package com.powerdialer.service;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;
import java.util.Arrays;
import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.powerdialer.dao.IGroupStatusDao;
import com.powerdialer.model.GroupPropertyStatus;
import com.powerdialer.model.GroupStatus;

@RunWith(MockitoJUnitRunner.class)
public class StatusServiceTest {

  @InjectMocks
  StatusService statusService;

  @Mock
  IGroupStatusDao mockGroupStatusDao;

  @Test
  public void givenNoGroupIdsWhenGetCommonResultStatusesThenReturnEmptySet() {
    Long[] groupIds = {};

    Set<GroupStatus> result = statusService.getCommonResultStatuses(groupIds);

    assertThat(result.size(), is(0));
  }

  @Test
  public void givenMultipleGroupsWithStatusesWhenGetCommonResultStatusesThenReturnCommonOnes() {
    GroupPropertyStatus status1 = createGroupPropertyStatus(1L);
    GroupPropertyStatus status2 = createGroupPropertyStatus(2L);
    GroupPropertyStatus status3 = createGroupPropertyStatus(3L);
    GroupPropertyStatus status4 = createGroupPropertyStatus(4L);

    when(mockGroupStatusDao.getStatusesByGroupId(11L, true, false)).thenReturn(Arrays.asList(status1, status2));
    when(mockGroupStatusDao.getStatusesByGroupId(12L, true, false)).thenReturn(Arrays.asList(status2, status3));
    when(mockGroupStatusDao.getStatusesByGroupId(13L, true, false)).thenReturn(Arrays.asList(status2, status3, status4));

    Long[] groupIds = { 11L, 12L, 13L };
    Set<GroupStatus> result = statusService.getCommonResultStatuses(groupIds);

    assertThat(result.size(), is(1));
    assertThat(result.iterator().next().getId(), is(status2.getStatus().getId()));
  }

  private GroupPropertyStatus createGroupPropertyStatus(Long id) {
    GroupPropertyStatus result = new GroupPropertyStatus();
    GroupStatus status = new GroupStatus();
    status.setId(id);
    result.setStatus(status);
    result.setId(id);
    return result;
  }

}
