package com.beyond.wbs.instruction.service;

import com.beyond.wbs.document.instruction.domain.InstructionDocument;
import com.beyond.wbs.document.instruction.domain.InstructionDocumentStatus;
import com.beyond.wbs.document.instruction.domain.InstructionDocumentType;
import com.beyond.wbs.document.instruction.dto.InstructionDocumentResDto;
import com.beyond.wbs.document.instruction.dto.InstructionDocumentSummaryDto;
import com.beyond.wbs.document.instruction.render.InstructionDocumentRenderContext;
import com.beyond.wbs.document.instruction.render.InstructionDocumentRenderer;
import com.beyond.wbs.document.instruction.render.exception.InstructionRenderException;
import com.beyond.wbs.document.instruction.render.exception.InstructionUploadException;
import com.beyond.wbs.document.instruction.repository.InstructionDocumentRepository;
import com.beyond.wbs.instruction.s3.InstructionDocumentS3Uploader;
import com.beyond.wbs.kafka.event.InstructionDocumentIssuedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 지시서 PDF 발행 오케스트레이션.
 *
 * 흐름 (issue):
 *   1) 직전 발행본 조회 → 다음 version 결정
 *   2) GENERATING 행 선삽입 (낙관적 락 충돌 시 재시도는 호출자/Kafka가 책임)
 *   3) docType에 맞는 Renderer.loadData() → render()
 *   4) sha256 비교 — 직전 발행본과 같으면 새 행을 만들지 않고 기존 행 반환 (F7 멱등성 정책)
 *   5) S3 업로드 → READY로 전환 (s3Key, fileSize, sha256)
 *   6) 실패 시 FAILED + errorMessage. 호출자(Listener)가 DLQ로 라우팅.
 */
@Service
@Slf4j
public class InstructionDocumentService {

    private final InstructionDocumentRepository documentRepository;
    private final InstructionDocumentS3Uploader s3Uploader;
    private final Map<InstructionDocumentType, InstructionDocumentRenderer<?>> renderersByType;

    @Autowired
    public InstructionDocumentService(
        InstructionDocumentRepository documentRepository,
        InstructionDocumentS3Uploader s3Uploader,
        List<InstructionDocumentRenderer<?>> renderers
    ) {
        this.documentRepository = documentRepository;
        this.s3Uploader = s3Uploader;
        this.renderersByType = new HashMap<>();
        for (InstructionDocumentRenderer<?> r : renderers) {
            this.renderersByType.put(r.supportedType(), r);
        }
        log.info("[InstructionDoc] Registered renderers: {}", renderersByType.keySet());
    }

    /**
     * 발행 처리. 컨슈머가 메시지 1건당 1회 호출.
     *
     * @return 생성/재사용된 InstructionDocument의 id
     */
    @Transactional
    public UUID issue(InstructionDocumentIssuedEvent event) {
        InstructionDocumentRenderer<?> renderer = renderersByType.get(event.getDocType());
        if (renderer == null) {
            throw new InstructionRenderException(
                "지원하지 않는 docType: " + event.getDocType());
        }

        int previousVersion = documentRepository.findMaxVersion(
            event.getClientId(), event.getDocType(), event.getSourceId());
        int nextVersion = previousVersion + 1;

        Optional<InstructionDocument> previousReady = documentRepository
            .findTopByClientIdAndDocTypeAndSourceIdOrderByVersionDesc(
                event.getClientId(), event.getDocType(), event.getSourceId())
            .filter(d -> d.getStatus() == InstructionDocumentStatus.READY);

        InstructionDocument generating = InstructionDocument.builder()
            .clientId(event.getClientId())
            .docType(event.getDocType())
            .sourceId(event.getSourceId())
            .sourceNo(event.getSourceNo())
            .version(nextVersion)
            .status(InstructionDocumentStatus.GENERATING)
            .issuedBy(event.getIssuedBy())
            .reissuedFromId(previousReady.map(InstructionDocument::getId).orElse(null))
            .build();
        documentRepository.save(generating);

        try {
            byte[] pdfBytes = renderAndLoad(renderer, event, generating);

            String sha = sha256Hex(pdfBytes);
            // F7 멱등성: 직전 READY와 sha256 동일 → 새 row 만들지 말고 기존 row 반환
            if (previousReady.isPresent()
                && sha.equalsIgnoreCase(previousReady.get().getSha256())) {
                log.info("[InstructionDoc] sha256 unchanged → reuse previous version. " +
                        "docType={} sourceNo={} prevVersion={}",
                    event.getDocType(), event.getSourceNo(), previousReady.get().getVersion());
                documentRepository.delete(generating);
                return previousReady.get().getId();
            }

            InstructionDocumentS3Uploader.UploadResult uploaded = s3Uploader.upload(
                event.getClientId(), event.getDocType(),
                event.getSourceId(), nextVersion, pdfBytes);
            generating.markReady(uploaded.s3Key(), uploaded.fileSize(), uploaded.sha256());
            documentRepository.save(generating);

            log.info("[InstructionDoc] READY docType={} sourceNo={} version={} sizeKB={} sha256={}",
                event.getDocType(), event.getSourceNo(), nextVersion,
                uploaded.fileSize() / 1024, uploaded.sha256().substring(0, 12));
            return generating.getId();

        } catch (InstructionRenderException | InstructionUploadException e) {
            generating.markFailed(e.getMessage());
            documentRepository.save(generating);
            log.error("[InstructionDoc] FAILED docType={} sourceNo={} version={} reason={}",
                event.getDocType(), event.getSourceNo(), nextVersion, e.getMessage(), e);
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private <T> byte[] renderAndLoad(
        InstructionDocumentRenderer<?> renderer,
        InstructionDocumentIssuedEvent event,
        InstructionDocument doc
    ) {
        InstructionDocumentRenderer<T> typed = (InstructionDocumentRenderer<T>) renderer;
        T data = typed.loadData(event.getSourceId(), event.getClientId());
        InstructionDocumentRenderContext ctx = new InstructionDocumentRenderContext(
            event.getClientId(),
            event.getSourceId(),
            event.getSourceNo(),
            doc.getVersion(),
            event.getIssuedBy(),
            doc.getIssuedAt() != null ? doc.getIssuedAt() : LocalDateTime.now()
        );
        return typed.render(data, ctx);
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(md.digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Transactional(readOnly = true)
    public InstructionDocumentResDto getDownloadable(UUID docId, UUID clientId) {
        InstructionDocument doc = documentRepository.findByIdAndClientId(docId, clientId)
            .orElseThrow(() -> new IllegalArgumentException(
                "지시서 문서를 찾을 수 없거나 접근 권한이 없습니다."));
        if (doc.getStatus() != InstructionDocumentStatus.READY) {
            throw new IllegalStateException(
                "아직 발행 중이거나 실패한 문서입니다. status=" + doc.getStatus());
        }
        return InstructionDocumentResDto.from(doc);
    }

    public String presignDownloadUrl(UUID docId, UUID clientId) {
        InstructionDocument doc = documentRepository.findByIdAndClientId(docId, clientId)
            .orElseThrow(() -> new IllegalArgumentException(
                "지시서 문서를 찾을 수 없거나 접근 권한이 없습니다."));
        if (doc.getStatus() != InstructionDocumentStatus.READY) {
            throw new IllegalStateException(
                "아직 발행 중이거나 실패한 문서입니다. status=" + doc.getStatus());
        }
        return s3Uploader.presignDownloadUrl(doc.getS3Key());
    }

    @Transactional(readOnly = true)
    public Page<InstructionDocumentResDto> list(
        UUID clientId,
        InstructionDocumentType docType,
        UUID sourceId,
        Pageable pageable
    ) {
        Page<InstructionDocument> page = documentRepository
            .findByClientIdAndDocTypeAndSourceIdOrderByVersionDesc(
                clientId, docType, sourceId, pageable);
        return page.map(InstructionDocumentResDto::from);
    }

    /**
     * 사이드바 "공식 문서함"용 다조건 검색.
     *
     * 정렬 규칙(Pageable이 unsorted일 때만 적용):
     *  - sourceId가 있으면 → version DESC (거래 단위 진입의 발행 이력)
     *  - 없으면            → issuedAt DESC (전사 최근 발행 순)
     */
    @Transactional(readOnly = true)
    public Page<InstructionDocumentResDto> search(
        UUID clientId,
        InstructionDocumentType docType,
        UUID sourceId,
        String sourceNo,
        InstructionDocumentStatus status,
        LocalDateTime issuedFrom,
        LocalDateTime issuedTo,
        Pageable pageable
    ) {
        Pageable effective = pageable;
        if (pageable == null || pageable.getSort().isUnsorted()) {
            Sort defaultSort = sourceId != null
                ? Sort.by(Sort.Direction.DESC, "version")
                : Sort.by(Sort.Direction.DESC, "issuedAt");
            int pageNum = pageable != null ? pageable.getPageNumber() : 0;
            int pageSize = pageable != null ? pageable.getPageSize() : 20;
            effective = PageRequest.of(pageNum, pageSize, defaultSort);
        }

        String pattern = (sourceNo != null && !sourceNo.isBlank())
            ? "%" + sourceNo.trim() + "%"
            : null;

        Page<InstructionDocument> page = documentRepository.search(
            clientId, docType, sourceId, pattern, status, issuedFrom, issuedTo, effective);
        return page.map(InstructionDocumentResDto::from);
    }

    /**
     * 사이드바 진입 시 표시할 요약 통계.
     * 한 회사 단위로 total · docType별 · status별 · 최근7일 4가지 메트릭을 한 번에 반환.
     */
    @Transactional(readOnly = true)
    public InstructionDocumentSummaryDto summary(UUID clientId) {
        long total = documentRepository.countByClientId(clientId);
        long last7days = documentRepository.countByClientIdAndIssuedAtGreaterThanEqual(
            clientId, LocalDateTime.now().minusDays(7));

        Map<InstructionDocumentType, Long> byType = new EnumMap<>(InstructionDocumentType.class);
        for (Object[] row : documentRepository.countByClientIdGroupByDocType(clientId)) {
            byType.put((InstructionDocumentType) row[0], (Long) row[1]);
        }

        Map<InstructionDocumentStatus, Long> byStatus = new EnumMap<>(InstructionDocumentStatus.class);
        for (Object[] row : documentRepository.countByClientIdGroupByStatus(clientId)) {
            byStatus.put((InstructionDocumentStatus) row[0], (Long) row[1]);
        }

        return InstructionDocumentSummaryDto.builder()
            .total(total)
            .byDocType(byType)
            .byStatus(byStatus)
            .last7DaysCount(last7days)
            .build();
    }
}
