package com.webchat.platformapi.skill;

import com.webchat.platformapi.market.ContextAssetContract;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class LocalSkillDiscoveryService {

    private static final Pattern FRONTMATTER_PATTERN = Pattern.compile("\\A---\\R(.*?)\\R---\\R?(.*)\\z", Pattern.DOTALL);
    private static final int CONTENT_PREVIEW_LIMIT = 1200;

    private final boolean enabled;
    private final String configuredRootDir;

    public LocalSkillDiscoveryService(
            @Value("${platform.skill-library.enabled:true}") boolean enabled,
            @Value("${platform.skill-library.root-dir:}") String configuredRootDir
    ) {
        this.enabled = enabled;
        this.configuredRootDir = configuredRootDir == null ? "" : configuredRootDir.trim();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<LocalSkillDefinition> listSkills() {
        if (!enabled) {
            return List.of();
        }
        Path root = resolveSkillRoot().orElse(null);
        if (root == null) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(root)) {
            return entries
                    .filter(Files::isDirectory)
                    .map(dir -> parseSkill(root, dir))
                    .flatMap(Optional::stream)
                    .sorted((left, right) -> left.sourceId().compareToIgnoreCase(right.sourceId()))
                    .toList();
        } catch (IOException ex) {
            return List.of();
        }
    }

    public Optional<LocalSkillDefinition> getSkill(String sourceId) {
        String normalizedSourceId = normalize(sourceId);
        if (normalizedSourceId == null) {
            return Optional.empty();
        }
        return listSkills().stream()
                .filter(skill -> normalizedSourceId.equalsIgnoreCase(skill.sourceId()))
                .findFirst();
    }

    Optional<Path> resolveSkillRoot() {
        String normalizedConfiguredRoot = normalize(configuredRootDir);
        if (normalizedConfiguredRoot != null) {
            Path configuredPath = Path.of(normalizedConfiguredRoot).toAbsolutePath().normalize();
            if (Files.isDirectory(configuredPath)) {
                return Optional.of(configuredPath);
            }
        }

        for (Path searchRoot : candidateSearchRoots()) {
            Optional<Path> candidate = walkUpForSkillRoot(searchRoot);
            if (candidate.isPresent()) {
                return candidate;
            }
        }
        return Optional.empty();
    }

    private List<Path> candidateSearchRoots() {
        LinkedHashSet<Path> roots = new LinkedHashSet<>();
        addCandidateRoot(roots, Path.of("").toAbsolutePath().normalize());
        addCandidateRoot(roots, systemPath("user.dir"));
        addCandidateRoot(roots, systemPath("maven.multiModuleProjectDirectory"));
        addCandidateRoot(roots, envPath("SKILL_LIBRARY_ROOT_DIR"));
        addCandidateRoot(roots, classLocationRoot());
        return new ArrayList<>(roots);
    }

    private Optional<Path> walkUpForSkillRoot(Path start) {
        Path current = start;
        while (current != null) {
            Path candidate = current.resolve(".agents").resolve("skills");
            if (Files.isDirectory(candidate)) {
                return Optional.of(candidate);
            }
            current = current.getParent();
        }
        return Optional.empty();
    }

    private void addCandidateRoot(LinkedHashSet<Path> roots, Path candidate) {
        if (candidate != null) {
            roots.add(candidate.toAbsolutePath().normalize());
        }
    }

    private Path systemPath(String key) {
        String value = normalize(System.getProperty(key));
        if (value == null) {
            return null;
        }
        try {
            return Path.of(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private Path envPath(String key) {
        String value = normalize(System.getenv(key));
        if (value == null) {
            return null;
        }
        try {
            return Path.of(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private Path classLocationRoot() {
        try {
            URI location = LocalSkillDiscoveryService.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path path = Path.of(location).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) {
                return path.getParent();
            }
            return path;
        } catch (Exception ex) {
            return null;
        }
    }

    private Optional<LocalSkillDefinition> parseSkill(Path root, Path skillDir) {
        Path skillFile = skillDir.resolve("SKILL.md");
        if (!Files.isRegularFile(skillFile)) {
            return Optional.empty();
        }
        try {
            String raw = Files.readString(skillFile, StandardCharsets.UTF_8);
            ParsedSkillContent parsed = parseContent(raw);
            String sourceId = skillDir.getFileName().toString();
            String name = firstNonBlank(parsed.metadata().get("name"), sourceId);
            String description = firstNonBlank(parsed.metadata().get("description"), "");
            String content = parsed.body().isBlank() ? raw.trim() : parsed.body().trim();
            String preview = content.length() <= CONTENT_PREVIEW_LIMIT
                    ? content
                    : content.substring(0, CONTENT_PREVIEW_LIMIT).trim() + "\n...";
            String relativePath = root.relativize(skillFile).toString().replace('\\', '/');
            return Optional.of(new LocalSkillDefinition(
                    sourceId,
                    name,
                    description,
                    skillFile.toAbsolutePath().normalize().toString(),
                    relativePath,
                    "SKILL.md",
                    content,
                    preview,
                    raw.getBytes(StandardCharsets.UTF_8).length,
                    ContextAssetContract.USAGE_MODE_FULL_INSTRUCTION,
                    ContextAssetContract.skillUsageInstruction(),
                    parsed.metadata()
            ));
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    private static ParsedSkillContent parseContent(String raw) {
        Matcher matcher = FRONTMATTER_PATTERN.matcher(raw == null ? "" : raw);
        if (!matcher.matches()) {
            return new ParsedSkillContent(Map.of(), raw == null ? "" : raw);
        }
        return new ParsedSkillContent(parseMetadata(matcher.group(1)), matcher.group(2));
    }

    private static Map<String, String> parseMetadata(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
        for (String line : raw.split("\\R")) {
            if (line == null || line.isBlank() || Character.isWhitespace(line.charAt(0))) {
                continue;
            }
            int separator = line.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            String key = normalize(line.substring(0, separator));
            String value = normalize(stripQuotes(line.substring(separator + 1)));
            if (key != null && value != null) {
                metadata.put(key, value);
            }
        }
        return metadata;
    }

    private static String stripQuotes(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.length() >= 2) {
            if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static String firstNonBlank(String first, String second) {
        String firstValue = normalize(first);
        if (firstValue != null) {
            return firstValue;
        }
        String secondValue = normalize(second);
        return secondValue == null ? "" : secondValue;
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        return value.isEmpty() ? null : value;
    }

    private record ParsedSkillContent(Map<String, String> metadata, String body) {
    }
}
