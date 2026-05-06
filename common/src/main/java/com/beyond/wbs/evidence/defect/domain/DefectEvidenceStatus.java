package com.beyond.wbs.evidence.defect.domain;

/**
 * presigned PUT 흐름의 단계 추적.
 *  PENDING : presigned URL 발급 후, S3 업로드 완료 통보 대기
 *  READY   : 업로드 완료 통보 수신 (사용 가능)
 *  ORPHAN  : 일정 시간 내 통보 없음 (정리 후보)
 */
public enum DefectEvidenceStatus {
    PENDING,
    READY,
    ORPHAN
}
