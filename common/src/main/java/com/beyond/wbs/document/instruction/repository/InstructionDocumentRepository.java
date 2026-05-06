package com.beyond.wbs.document.instruction.repository;

import com.beyond.wbs.document.instruction.domain.InstructionDocument;
import com.beyond.wbs.document.instruction.domain.InstructionDocumentStatus;
import com.beyond.wbs.document.instruction.domain.InstructionDocumentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstructionDocumentRepository extends JpaRepository<InstructionDocument, UUID> {

    Optional<InstructionDocument> findByIdAndClientId(UUID id, UUID clientId);

    // 가장 최신 발행본 1건 (재발행 시 sha256 비교용)
    Optional<InstructionDocument> findTopByClientIdAndDocTypeAndSourceIdOrderByVersionDesc(
        UUID clientId,
        InstructionDocumentType docType,
        UUID sourceId
    );

    // 같은 (client, docType, sourceId)에서 다음 version 계산용
    @Query("""
           select coalesce(max(d.version), 0)
             from InstructionDocument d
            where d.clientId = :clientId
              and d.docType  = :docType
              and d.sourceId = :sourceId
           """)
    int findMaxVersion(UUID clientId, InstructionDocumentType docType, UUID sourceId);

    Page<InstructionDocument> findByClientIdAndDocTypeAndSourceIdOrderByVersionDesc(
        UUID clientId,
        InstructionDocumentType docType,
        UUID sourceId,
        Pageable pageable
    );

    Page<InstructionDocument> findByClientIdAndStatus(
        UUID clientId,
        InstructionDocumentStatus status,
        Pageable pageable
    );

    /**
     * 사이드바 "공식 문서함"용 다조건 검색.
     * 모든 필터는 옵셔널 — null이면 해당 조건 무시.
     * 정렬은 호출자(Pageable)가 명시. 미명시 시 Service 레이어에서 기본값 적용.
     */
    @Query("""
           select d from InstructionDocument d
            where d.clientId = :clientId
              and (:docType        is null or d.docType  = :docType)
              and (:sourceId       is null or d.sourceId = :sourceId)
              and (:sourceNoPattern is null or d.sourceNo like :sourceNoPattern)
              and (:status         is null or d.status   = :status)
              and (:issuedFrom     is null or d.issuedAt >= :issuedFrom)
              and (:issuedTo       is null or d.issuedAt <  :issuedTo)
           """)
    Page<InstructionDocument> search(
        @Param("clientId")        UUID clientId,
        @Param("docType")         InstructionDocumentType docType,
        @Param("sourceId")        UUID sourceId,
        @Param("sourceNoPattern") String sourceNoPattern,
        @Param("status")          InstructionDocumentStatus status,
        @Param("issuedFrom")      LocalDateTime issuedFrom,
        @Param("issuedTo")        LocalDateTime issuedTo,
        Pageable pageable
    );

    // ─── 사이드바 요약 카드용 통계 ───

    long countByClientId(UUID clientId);

    long countByClientIdAndIssuedAtGreaterThanEqual(UUID clientId, LocalDateTime since);

    @Query("""
           select d.docType, count(d)
             from InstructionDocument d
            where d.clientId = :clientId
            group by d.docType
           """)
    List<Object[]> countByClientIdGroupByDocType(@Param("clientId") UUID clientId);

    @Query("""
           select d.status, count(d)
             from InstructionDocument d
            where d.clientId = :clientId
            group by d.status
           """)
    List<Object[]> countByClientIdGroupByStatus(@Param("clientId") UUID clientId);
}
