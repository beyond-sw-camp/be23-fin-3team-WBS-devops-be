package com.beyond.wbs.ai.rag;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * WMS 운영 지식 텍스트를 섹션 단위 Document로 분할한다.
 * txt 문서의 "문서/영역/섹션/항목" 라벨을 인식한다.
 */
@Component
public class KnowledgeSectionParser {

    private static final Pattern LABELED_HEADING_PATTERN =
            Pattern.compile("^(문서|영역|섹션|항목)\\s*:\\s*(.+?)\\s*$");

    public List<Document> parse(String content, Map<String, Object> baseMetadata) {
        List<Document> sections = new ArrayList<>();
        String[] lines = content.split("\n", -1);

        String[] headingStack = new String[7];
        StringBuilder body = new StringBuilder();
        String currentPath = null;

        for (String line : lines) {
            Heading heading = parseHeading(line);
            if (heading != null) {
                if (currentPath != null && !body.toString().isBlank()) {
                    sections.add(buildSectionDoc(currentPath, body.toString(), baseMetadata));
                }
                headingStack[heading.level()] = heading.title();
                for (int i = heading.level() + 1; i < headingStack.length; i++) {
                    headingStack[i] = null;
                }
                currentPath = Arrays.stream(headingStack, 1, headingStack.length)
                        .filter(Objects::nonNull)
                        .collect(Collectors.joining(" > "));
                body.setLength(0);
            } else {
                body.append(line).append("\n");
            }
        }

        if (currentPath != null && !body.toString().isBlank()) {
            sections.add(buildSectionDoc(currentPath, body.toString(), baseMetadata));
        }
        return sections;
    }

    private Heading parseHeading(String line) {
        Matcher labeled = LABELED_HEADING_PATTERN.matcher(line);
        if (labeled.matches()) {
            return new Heading(labelLevel(labeled.group(1)), labeled.group(2).trim());
        }
        return null;
    }

    private int labelLevel(String label) {
        return switch (label) {
            case "문서" -> 1;
            case "영역", "섹션" -> 2;
            case "항목" -> 3;
            default -> 2;
        };
    }

    private Document buildSectionDoc(String sectionPath, String body, Map<String, Object> base) {
        Map<String, Object> meta = new HashMap<>(base == null ? Map.of() : base);
        meta.put("section_path", sectionPath);
        String content = "[섹션: " + sectionPath + "]\n\n" + body.trim();
        return new Document(content, meta);
    }

    private record Heading(int level, String title) {
    }
}
