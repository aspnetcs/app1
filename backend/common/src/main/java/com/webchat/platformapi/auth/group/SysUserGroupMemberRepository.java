package com.webchat.platformapi.auth.group;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SysUserGroupMemberRepository extends JpaRepository<SysUserGroupMemberEntity, UUID> {

    List<SysUserGroupMemberEntity> findByUserIdIn(Collection<UUID> userIds);

    List<SysUserGroupMemberEntity> findByUserId(UUID userId);

    List<SysUserGroupMemberEntity> findByGroupId(UUID groupId);

    List<SysUserGroupMemberEntity> findByGroupIdIn(Collection<UUID> groupIds);

    void deleteByGroupId(UUID groupId);
}
