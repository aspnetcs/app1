package com.webchat.platformapi.auth.v1;

import com.webchat.platformapi.auth.credential.UserCredentialEntity;
import com.webchat.platformapi.auth.credential.UserCredentialRepository;
import com.webchat.platformapi.auth.verification.RedisVerificationService;
import com.webchat.platformapi.user.UserEntity;
import com.webchat.platformapi.user.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordLoginServiceTest {

    @Mock
    private RedisVerificationService verificationService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserCredentialRepository credentialRepository;
    @Mock
    private StringRedisTemplate redis;

    private PasswordLoginService passwordLoginService;

    @BeforeEach
    void setUp() {
        passwordLoginService = new PasswordLoginService(
                verificationService,
                userRepository,
                credentialRepository,
                redis
        );
    }

    @Test
    void authenticateAllowsLiteralAdminAliasStoredInEmailField() {
        UserEntity admin = new UserEntity();
        admin.setId(UUID.randomUUID());
        admin.setEmail("admin");
        admin.setRole("admin");

        UserCredentialEntity credential = new UserCredentialEntity();
        credential.setUserId(admin.getId());
        credential.setPasswordHash(new BCryptPasswordEncoder().encode("admin"));

        when(verificationService.consumeChallengeToken("challenge", "127.0.0.1")).thenReturn(true);
        when(redis.hasKey(anyString())).thenReturn(false);
        when(userRepository.findByEmailAndDeletedAtIsNull("admin")).thenReturn(Optional.of(admin));
        when(credentialRepository.findById(admin.getId())).thenReturn(Optional.of(credential));

        PasswordLoginService.PasswordLoginResult result = passwordLoginService.authenticate(
                new com.webchat.platformapi.auth.v1.dto.PasswordLoginRequest(null, null, "admin", "admin", "challenge"),
                "127.0.0.1",
                true
        );

        assertNull(result.errorResponse());
        assertNotNull(result.user());
        assertEquals(admin.getId(), result.user().getId());
        assertEquals("identifier", result.identifierType());
    }
}
