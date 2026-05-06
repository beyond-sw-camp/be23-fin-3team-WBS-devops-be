package com.beyond.wbs.evidence.defect.repository;

import com.beyond.wbs.evidence.defect.domain.DefectEvidence;
import com.beyond.wbs.evidence.defect.domain.DefectEvidenceSourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DefectEvidenceRepository extends JpaRepository<DefectEvidence, UUID> {

    Optional<DefectEvidence> findByIdAndClientId(UUID id, UUID clientId);

    List<DefectEvidence> findByClientIdAndSourceTypeAndSourceIdOrderByUploadedAtAsc(
        UUID clientId,
        DefectEvidenceSourceType sourceType,
        UUID sourceId
    );

    /**
     * 1장 row 수만 카운트 — PDF 푸터의 "사진 N장" 표시용.
     */
    long countByClientIdAndSourceTypeAndSourceId(
        UUID clientId,
        DefectEvidenceSourceType sourceType,
        UUID sourceId
    );

    /**
     * 한 receipt 의 모든 item 들에 첨부된 사진 카운트 — receipt 단위 카운트 표시용.
     */
    @Query("""
           select count(d)
             from DefectEvidence d
            where d.clientId   = :clientId
              and d.sourceType = :sourceType
              and d.sourceId in :sourceIds
              and d.status     = com.beyond.wbs.evidence.defect.domain.DefectEvidenceStatus.READY
           """)
    long countReadyBySourceIds(@Param("clientId") UUID clientId,
                               @Param("sourceType") DefectEvidenceSourceType sourceType,
                               @Param("sourceIds") List<UUID> sourceIds);
}
