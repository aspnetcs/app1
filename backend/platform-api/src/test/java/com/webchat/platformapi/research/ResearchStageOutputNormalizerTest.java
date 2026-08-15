package com.webchat.platformapi.research;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResearchStageOutputNormalizerTest {

    private final ResearchStageOutputNormalizer normalizer = new ResearchStageOutputNormalizer();

    @Test
    void extractDisplayContentReturnsNullWhenPayloadOnlyContainsBlankContentMetadata() {
        String restored = normalizer.extractDisplayContent("{\"format\":\"markdown\",\"content\":\"   \"}");

        assertNull(restored);
    }

    @Test
    void normalizeStoredOutputJsonKeepsStructuredPayloadWhenNonTextFieldsExist() {
        String normalized = normalizer.normalizeStoredOutputJson("{\"matchCount\":2}");

        assertNotNull(normalized);
        assertTrue(normalized.contains("\"content\""));
        assertTrue(normalized.contains("\"matchCount\":2"));
    }
}
