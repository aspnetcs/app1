package com.webchat.platformapi.research.entity;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ResearchRunEntityTest {

    @Test
    void prePersistInitializesSummaryJsonAsJsonObject() {
        ResearchRunEntity entity = new ResearchRunEntity();
        entity.setSummaryJson(null);

        entity.prePersist();

        assertNotNull(entity.getSummaryJson());
        assertEquals(Map.of(), entity.getSummaryJson());
    }
}
