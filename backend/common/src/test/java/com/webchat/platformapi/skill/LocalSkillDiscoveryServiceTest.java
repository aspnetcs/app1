package com.webchat.platformapi.skill;

import com.webchat.platformapi.market.ContextAssetContract;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalSkillDiscoveryServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void listSkillsParsesFrontmatterAndBody() throws IOException {
        Path skillsRoot = tempDir.resolve("skills");
        Path skillDir = skillsRoot.resolve("demo-skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                name: demo-skill
                description: Demo description
                metadata:
                  author: tester
                ---

                # Demo Skill

                Follow the full skill.
                """, StandardCharsets.UTF_8);

        LocalSkillDiscoveryService service = new LocalSkillDiscoveryService(true, skillsRoot.toString());

        List<LocalSkillDefinition> skills = service.listSkills();
        assertEquals(1, skills.size());
        assertEquals("demo-skill", skills.get(0).sourceId());
        assertEquals("Demo description", skills.get(0).description());
        assertEquals(ContextAssetContract.USAGE_MODE_FULL_INSTRUCTION, skills.get(0).usageMode());
        assertTrue(skills.get(0).content().contains("# Demo Skill"));
    }

    @Test
    void disabledServiceReturnsEmptyList() {
        LocalSkillDiscoveryService service = new LocalSkillDiscoveryService(false, tempDir.resolve("skills").toString());
        assertTrue(service.listSkills().isEmpty());
    }
}
