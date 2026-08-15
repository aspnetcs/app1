package com.webchat.platformapi.auth.group;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserGroupServiceTest {

    @Test
    void legacyRoleplayFeatureFlagGrantsMultiAgentDiscussion() {
        SysUserGroupRepository groupRepository = mock(SysUserGroupRepository.class);
        SysUserGroupMemberRepository memberRepository = mock(SysUserGroupMemberRepository.class);
        UserGroupService service = new UserGroupService(groupRepository, memberRepository);
        UUID userId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();

        SysUserGroupMemberEntity member = new SysUserGroupMemberEntity();
        member.setUserId(userId);
        member.setGroupId(groupId);
        when(memberRepository.findByUserId(userId)).thenReturn(List.of(member));

        SysUserGroupEntity group = new SysUserGroupEntity();
        group.setId(groupId);
        group.setName("legacy-roleplay");
        group.setEnabled(true);
        group.setFeatureFlags("roleplay");
        when(groupRepository.findAllById(List.of(groupId))).thenReturn(List.of(group));

        assertThat(service.isFeatureAllowed(userId, "multi_agent_discussion")).isTrue();
    }
}
