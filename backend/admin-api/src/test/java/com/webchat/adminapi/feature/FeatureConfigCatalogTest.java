package com.webchat.adminapi.feature;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureConfigCatalogTest {

    @Test
    void teamChatDefinitionIncludesRuntimeLlmConcurrencyField() {
        FeatureDefinition definition = FeatureConfigCatalog.createDefinitions().get("team-chat");

        assertThat(definition).isNotNull();
        FeatureField field = definition.fields().stream()
                .filter(candidate -> "maxLlmConcurrency".equals(candidate.key()))
                .findFirst()
                .orElse(null);

        assertThat(field).isNotNull();
        assertThat(field.propertyKey()).isEqualTo("platform.team-chat.max-llm-concurrency");
        assertThat(field.defaultValue()).isEqualTo(8);
        assertThat(field.min()).isEqualTo(2);
        assertThat(field.max()).isEqualTo(20);
    }

}
