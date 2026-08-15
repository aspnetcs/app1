package com.webchat.platformapi.auth.credential;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserCredentialRepository extends JpaRepository<UserCredentialEntity, UUID> {}

