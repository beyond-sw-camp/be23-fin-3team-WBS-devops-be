package com.beyond.wbs.ai.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeAutoIngestRunner implements ApplicationRunner {

    private final DocumentIngestService ingestService;
    private final ResourcePatternResolver resourcePatternResolver;

    @Value("${wms.rag.auto-ingest.enabled:true}")
    private boolean enabled;

    @Value("${wms.rag.auto-ingest.location:classpath:/knowledge/*.txt}")
    private String locationPattern;

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("[RAG_AUTO_INGEST] skipped: disabled");
            return;
        }

        try {
            Resource[] resources = resourcePatternResolver.getResources(locationPattern);
            if (resources.length == 0) {
                log.warn("[RAG_AUTO_INGEST] no knowledge resources found: location={}", locationPattern);
                return;
            }

            int totalChunks = 0;
            int successCount = 0;
            for (Resource resource : resources) {
                try {
                    String filename = resource.getFilename();
                    String source = sourceName(filename);
                    String content = resource.getContentAsString(StandardCharsets.UTF_8);
                    if (content.isBlank()) {
                        log.warn("[RAG_AUTO_INGEST] skipped blank resource: source={}", source);
                        continue;
                    }

                    Map<String, Object> metadata = new LinkedHashMap<>();
                    metadata.put("source", source);
                    metadata.put("category", source);
                    metadata.put("ingest_type", "classpath_auto");

                    int chunks = ingestService.ingestText(content, metadata);
                    totalChunks += chunks;
                    successCount++;
                    log.info("[RAG_AUTO_INGEST] source={}, chunks={}", source, chunks);
                } catch (Exception e) {
                    log.error("[RAG_AUTO_INGEST] resource failed: name={}, error={}",
                            resource.getFilename(), e.getMessage(), e);
                }
            }

            log.info("[RAG_AUTO_INGEST] completed: resources={}, succeeded={}, chunks={}",
                    resources.length, successCount, totalChunks);
        } catch (Exception e) {
            log.error("[RAG_AUTO_INGEST] failed to resolve resources: location={}, error={}",
                    locationPattern, e.getMessage(), e);
        }
    }

    private String sourceName(String filename) {
        if (filename == null || filename.isBlank()) {
            return "unknown";
        }
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}
