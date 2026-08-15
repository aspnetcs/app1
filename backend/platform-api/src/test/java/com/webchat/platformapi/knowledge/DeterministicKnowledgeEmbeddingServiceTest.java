package com.webchat.platformapi.knowledge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicKnowledgeEmbeddingServiceTest {

    private final DeterministicKnowledgeEmbeddingService service = new DeterministicKnowledgeEmbeddingService();

    @Test
    void chineseQueryShouldStayCloseToRelatedDocument() {
        float[] query = service.embed("用户忘记手机号时，账号找回应该怎么处理？");
        float[] related = service.embed("当用户忘记手机号时，账号找回必须先走人工核验流程。");
        float[] unrelated = service.embed("production 环境的常规发布时间窗是每周二和周四 20:00 到 21:00。");

        double relatedScore = service.cosine(query, related);
        double unrelatedScore = service.cosine(query, unrelated);

        assertTrue(relatedScore > unrelatedScore, "related Chinese document should rank above unrelated content");
        assertTrue(relatedScore > 0.4d, "related Chinese document should keep enough overlap for retrieval");
    }
}
