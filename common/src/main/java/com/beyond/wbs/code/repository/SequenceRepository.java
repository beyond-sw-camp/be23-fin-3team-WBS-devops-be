package com.beyond.wbs.code.repository;

import com.beyond.wbs.code.domain.Sequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 채번 시퀀스 Repository
 *
 * 비관적 락(PESSIMISTIC_WRITE)으로 조회하여
 * 동시에 같은 타입의 번호를 채번해도 중복이 생기지 않도록 보장.
 */
public interface SequenceRepository extends JpaRepository<Sequence, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Sequence s WHERE s.type = :type")
    Optional<Sequence> findByTypeWithLock(@Param("type") String type);
}
