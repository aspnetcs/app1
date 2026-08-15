package com.webchat.platformapi.knowledge;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeQueryNormalizerTest {

    @Test
    void normalizeStripsKnowledgePromptWrapperInChinese() {
        assertThat(KnowledgeQueryNormalizer.normalize(
                "请基于已选知识库回答：用户忘记手机号时，账号找回应该怎么处理？如果信息不足请明确指出。"
        )).isEqualTo("用户忘记手机号时，账号找回应该怎么处理？");
    }

    @Test
    void normalizeStripsKnowledgePromptWrapperInEnglish() {
        assertThat(KnowledgeQueryNormalizer.normalize(
                "Please answer based on the selected knowledge base: how do I install it? If information is insufficient, say so."
        )).isEqualTo("how do I install it?");
    }
}
