package com.webchat.platformapi.ai.chat.team;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamLlmClientConstructorTest {

    @Test
    void teamLlmClientKeepsSpringProxyConstructorAndInitializesHelpers() throws Exception {
        boolean hasNoArgConstructor = Arrays.stream(TeamLlmClient.class.getDeclaredConstructors())
                .map(Constructor::getParameterCount)
                .anyMatch(parameterCount -> parameterCount == 0);

        assertTrue(hasNoArgConstructor);

        Constructor<TeamLlmClient> constructor = TeamLlmClient.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        TeamLlmClient client = constructor.newInstance();

        Field httpClientField = TeamLlmClient.class.getDeclaredField("httpClient");
        httpClientField.setAccessible(true);
        Field objectMapperField = TeamLlmClient.class.getDeclaredField("objectMapper");
        objectMapperField.setAccessible(true);

        assertNotNull(httpClientField.get(client));
        assertNotNull(objectMapperField.get(client));
    }
}
