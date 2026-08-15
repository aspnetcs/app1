package com.webchat.platformapi.user.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserIdentityRepository extends JpaRepository<UserIdentityEntity, Long> {

    Optional<UserIdentityEntity> findFirstByProviderAndProviderScopeAndTypeAndIdentifier(
            String provider,
            String providerScope,
            String type,
            String identifier
    );

    boolean existsByUserIdAndProvider(UUID userId, String provider);

    boolean existsByProviderAndProviderScopeAndTypeAndIdentifier(String provider, String providerScope, String type, String identifier);

    boolean existsByUserIdAndProviderAndType(UUID userId, String provider, String type);

    void deleteAllByUserIdAndProvider(UUID userId, String provider);

    void deleteAllByUserId(UUID userId);
}
