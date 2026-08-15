package com.webchat.platformapi.preferences;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPreferenceServiceTest {

    @Mock
    private UserPreferenceRepository repository;

    private UserPreferenceService service;

    @BeforeEach
    void setUp() {
        service = new UserPreferenceService(repository);
        when(repository.save(any(UserPreferenceEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void updatePreferencesPersistsSpacingFieldsAndNormalizesInvalidValues() {
        UUID userId = UUID.randomUUID();
        when(repository.findById(userId)).thenReturn(Optional.empty());

        Map<String, Object> payload = service.updatePreferences(userId, Map.of(
                "spacingVertical", "20px",
                "spacingHorizontal", "bad-value",
                "fontScale", "lg"
        ));

        assertEquals("20px", payload.get("spacingVertical"));
        assertEquals("16px", payload.get("spacingHorizontal"));
        assertEquals("lg", payload.get("fontScale"));
    }
}
