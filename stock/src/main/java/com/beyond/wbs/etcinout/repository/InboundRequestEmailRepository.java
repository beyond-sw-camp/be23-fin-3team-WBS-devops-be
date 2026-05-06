package com.beyond.wbs.etcinout.repository;

import com.beyond.wbs.etcinout.domain.InboundRequestEmail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InboundRequestEmailRepository extends JpaRepository<InboundRequestEmail, UUID> {

    List<InboundRequestEmail> findByEtcOrderIdOrderByCreatedAtDesc(UUID etcOrderId);
}
