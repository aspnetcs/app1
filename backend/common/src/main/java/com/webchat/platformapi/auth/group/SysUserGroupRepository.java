package com.webchat.platformapi.auth.group;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface SysUserGroupRepository extends JpaRepository<SysUserGroupEntity, UUID>, JpaSpecificationExecutor<SysUserGroupEntity> {
}
