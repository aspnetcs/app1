package com.webchat.adminapi.knowledge;

import com.webchat.platformapi.common.api.ErrorCodes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
class KnowledgeAdminControllerTest {

    @Test
    void listBasesReturnsAdminPayload() {
        KnowledgeAdminController controller = new KnowledgeAdminController(new StubKnowledgeAdminService(
                List.of(Map.of("name", "KB"))
        ));
        var response = controller.listBases(UUID.randomUUID(), "admin");
        assertEquals(0, response.code());
        assertEquals(1, ((List<?>) response.data().get("items")).size());
    }

    @Test
    void configRejectsNonAdmin() {
        KnowledgeAdminController controller = new KnowledgeAdminController(new StubKnowledgeAdminService(List.of()));
        var response = controller.config(UUID.randomUUID(), "user");
        assertEquals(ErrorCodes.UNAUTHORIZED, response.code());
    }

    private static final class StubKnowledgeAdminService extends KnowledgeAdminService {

        private final List<Map<String, Object>> bases;

        private StubKnowledgeAdminService(List<Map<String, Object>> bases) {
            super(null, null, null);
            this.bases = bases;
        }

        @Override
        public List<Map<String, Object>> listBases() {
            return bases;
        }

        @Override
        public List<Map<String, Object>> listJobs() {
            return List.of();
        }

        @Override
        public Map<String, Object> config() {
            return Map.of();
        }

        @Override
        public Map<String, Object> updateConfig(Map<String, Object> body) {
            return Map.of();
        }
    }
}
