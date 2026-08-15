package com.webchat.platformapi.admin.ops;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface SysBannerRepository extends JpaRepository<SysBannerEntity, UUID>, JpaSpecificationExecutor<SysBannerEntity> {
}
