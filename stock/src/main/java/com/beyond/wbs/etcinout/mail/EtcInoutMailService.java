package com.beyond.wbs.etcinout.mail;

import com.beyond.wbs.common.client.MasterServiceClient;
import com.beyond.wbs.common.client.dto.ProductResDto;
import com.beyond.wbs.etcinout.domain.EtcInoutCancellationLink;
import com.beyond.wbs.etcinout.domain.EtcInoutOrder;
import com.beyond.wbs.etcinout.domain.EtcInoutOrderItem;
import com.beyond.wbs.etcinout.domain.InboundRequestEmail;
import com.beyond.wbs.etcinout.domain.InboundRequestEmailStatus;
import com.beyond.wbs.etcinout.dto.InboundRequestEmailHistoryResDto;
import com.beyond.wbs.etcinout.dto.InboundRequestPreviewResDto;
import com.beyond.wbs.etcinout.dto.InboundRequestSendReqDto;
import com.beyond.wbs.etcinout.dto.InboundRequestSendResDto;
import com.beyond.wbs.etcinout.repository.EtcInoutCancellationLinkRepository;
import com.beyond.wbs.etcinout.repository.InboundRequestEmailRepository;
import com.beyond.wbs.outbounds.domain.OutboundOrderItems;
import com.beyond.wbs.outbounds.repository.OutboundOrderItemRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 기타출고 — OMS 입고 요청 메일 SMTP 발송 + 이력 저장.
 *
 * <p>운영자가 폼에서 편집한 값을 그대로 받아 발송. 비워둔 필드는 yml 기본값 + 자동 본문.
 * 자동 본문은 cancellation_link 의 cancelled outbound 의 품목들을 productId 별 합산하여 출력.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EtcInoutMailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final InboundRequestEmailRepository emailRepository;
    private final EtcInoutCancellationLinkRepository cancellationLinkRepository;
    private final OutboundOrderItemRepository outboundOrderItemRepository;
    private final MasterServiceClient masterServiceClient;

    @Value("${app.mail.inbound-request.to}")
    private String defaultRecipient;

    @Value("${app.mail.inbound-request.from}")
    private String senderAddress;   // 시스템 발송 계정 (편집 불가)

    @Value("${app.mail.inbound-request.from-name:WMS 시스템}")
    private String defaultSenderName;

    @Value("${app.mail.inbound-request.subject-prefix:[기타출고] 입고 요청}")
    private String subjectPrefix;

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 미리보기 — 프론트 폼 초기값으로 사용.
     */
    public InboundRequestPreviewResDto buildPreview(EtcInoutOrder order,
                                                     List<EtcInoutOrderItem> items,
                                                     String requesterName,
                                                     String requesterEmail) {
        return InboundRequestPreviewResDto.builder()
                .recipient(defaultRecipient)
                .senderName(defaultSenderName)
                .subject(buildDefaultSubject(order))
                .body(buildDefaultBody(order, items, null, requesterName, requesterEmail))
                .build();
    }

    /**
     * 입고 요청 메일 발송 — 운영자가 편집한 값 우선, 없으면 기본값.
     */
    @Transactional
    public InboundRequestSendResDto sendInboundRequest(EtcInoutOrder order,
                                                       List<EtcInoutOrderItem> items,
                                                       InboundRequestSendReqDto dto,
                                                       UUID requesterId,
                                                       String requesterName,
                                                       String requesterEmail) {
        // 운영자 편집값 우선, 없으면 기본값
        String recipient = override(dto != null ? dto.getRecipient() : null, defaultRecipient);
        String senderName = override(dto != null ? dto.getSenderName() : null, defaultSenderName);
        String subject = override(dto != null ? dto.getSubject() : null, buildDefaultSubject(order));
        String body = override(dto != null ? dto.getBody() : null,
                buildDefaultBody(order, items, dto, requesterName, requesterEmail));

        // 이력 row 먼저 (status=failed 로 일단)
        InboundRequestEmail emailLog = emailRepository.save(InboundRequestEmail.builder()
                .etcOrderId(order.getId())
                .sentBy(requesterId)
                .sentByName(requesterName)
                .sentByEmail(requesterEmail)
                .senderAddress(senderAddress)
                .recipient(recipient)
                .subject(subject)
                .body(body)
                .status(InboundRequestEmailStatus.failed)
                .build());

        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            return saveFailure(emailLog, "JavaMailSender Bean 이 구성되지 않았습니다.");
        }

        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, false, StandardCharsets.UTF_8.name());

            helper.setFrom(new InternetAddress(senderAddress, senderName, StandardCharsets.UTF_8.name()));
            helper.setTo(recipient);
            if (requesterEmail != null && !requesterEmail.isBlank()) {
                helper.setReplyTo(requesterEmail);
            }
            helper.setSubject(subject);
            helper.setText(body, false);

            sender.send(message);

            return saveSuccess(emailLog, message.getMessageID());
        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("[Mail] MIME 메시지 구성 실패 — etcOrderId={}", order.getId(), e);
            return saveFailure(emailLog, e.getMessage());
        } catch (Exception e) {
            log.error("[Mail] SMTP 발송 실패 — etcOrderId={}", order.getId(), e);
            return saveFailure(emailLog, e.getMessage());
        }
    }

    /**
     * 발송 이력 조회.
     */
    @Transactional(readOnly = true)
    public List<InboundRequestEmailHistoryResDto> getHistory(UUID etcOrderId) {
        return emailRepository.findByEtcOrderIdOrderByCreatedAtDesc(etcOrderId).stream()
                .map(InboundRequestEmailHistoryResDto::fromEntity)
                .toList();
    }

    // ============================================================
    // 내부 헬퍼
    // ============================================================

    private String override(String userValue, String fallback) {
        return (userValue != null && !userValue.isBlank()) ? userValue : fallback;
    }

    private String buildDefaultSubject(EtcInoutOrder order) {
        return subjectPrefix + " — " + order.getOrderNo();
    }

    private String buildDefaultBody(EtcInoutOrder order,
                                    List<EtcInoutOrderItem> items,
                                    InboundRequestSendReqDto dto,
                                    String requesterName,
                                    String requesterEmail) {
        StringBuilder sb = new StringBuilder();
        sb.append("안녕하세요, OMS팀.\n\n");

        String requesterLabel = requesterName != null ? requesterName : "(미지정)";
        if (requesterEmail != null && !requesterEmail.isBlank()) {
            requesterLabel += " (" + requesterEmail + ")";
        }
        sb.append("요청자: ").append(requesterLabel).append('\n');
        sb.append("요청일시: ").append(LocalDateTime.now().format(TS_FMT)).append('\n');
        sb.append("기타출고 번호: ").append(order.getOrderNo()).append("\n\n");

        sb.append("[부족 품목]\n");
        boolean useShortage = dto != null
                && dto.getShortageItems() != null
                && !dto.getShortageItems().isEmpty();

        if (useShortage) {
            int idx = 1;
            for (var s : dto.getShortageItems()) {
                String name = s.getProductName() != null ? s.getProductName()
                        : ("상품ID " + s.getProductId());
                sb.append(' ').append(idx++).append(". ").append(name);
                if (s.getRequested() != null) sb.append(" — 요청 ").append(s.getRequested());
                if (s.getAvailable() != null) sb.append(" / 가용 ").append(s.getAvailable());
                if (s.getShortage() != null) sb.append(" / 부족 ").append(s.getShortage());
                sb.append('\n');
            }
        } else {
            // 실제 취소된 출고지시서들의 품목만 합산 — etc_inout_order_items 가 아니라 cancellation_link 기준
            Map<UUID, Integer> qtyByProduct = aggregateCancelledItems(order.getId());
            if (qtyByProduct.isEmpty()) {
                sb.append(" - (취소된 출고지시서가 없습니다)\n");
            } else {
                Map<UUID, String> productNames = fetchProductNames(qtyByProduct.keySet(), order.getClientId());
                int idx = 1;
                for (var entry : qtyByProduct.entrySet()) {
                    String name = productNames.getOrDefault(entry.getKey(), "상품ID " + entry.getKey());
                    sb.append(' ').append(idx++).append(". ").append(name)
                            .append(" — 수량 ").append(entry.getValue()).append('\n');
                }
            }
        }

        sb.append("\n빠른 입고 부탁드립니다.\n\n");
        sb.append("─ WMS 시스템에서 자동 발송된 메일입니다 ─\n");
        return sb.toString();
    }

    // 해당 etc-out 의 cancellation_link 들을 따라 cancelled outbound 의 품목을 productId 별로 합산.
    private Map<UUID, Integer> aggregateCancelledItems(UUID etcOrderId) {
        List<EtcInoutCancellationLink> links = cancellationLinkRepository.findByEtcOrderId(etcOrderId);
        Map<UUID, Integer> qtyByProduct = new LinkedHashMap<>();
        for (EtcInoutCancellationLink link : links) {
            List<OutboundOrderItems> oitems = outboundOrderItemRepository
                    .findByOutboundOrdersId(link.getCancelledOutboundId());
            for (OutboundOrderItems oi : oitems) {
                qtyByProduct.merge(oi.getProductId(),
                        oi.getOrderedQty() != null ? oi.getOrderedQty() : 0,
                        Integer::sum);
            }
        }
        return qtyByProduct;
    }

    private Map<UUID, String> fetchProductNames(java.util.Collection<UUID> productIds, UUID clientId) {
        if (productIds == null || productIds.isEmpty() || clientId == null) {
            return new HashMap<>();
        }
        try {
            List<ProductResDto> products = masterServiceClient.getProducts(
                    new java.util.ArrayList<>(productIds), clientId.toString());
            Map<UUID, String> map = new HashMap<>();
            for (ProductResDto p : products) {
                if (p != null && p.getId() != null) {
                    map.put(p.getId(), p.getName());
                }
            }
            return map;
        } catch (Exception e) {
            log.warn("[Mail] 상품명 batch 조회 실패: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    private InboundRequestSendResDto saveSuccess(InboundRequestEmail emailLog, String messageId) {
        InboundRequestEmail updated = InboundRequestEmail.builder()
                .id(emailLog.getId())
                .etcOrderId(emailLog.getEtcOrderId())
                .sentBy(emailLog.getSentBy())
                .sentByName(emailLog.getSentByName())
                .sentByEmail(emailLog.getSentByEmail())
                .senderAddress(emailLog.getSenderAddress())
                .recipient(emailLog.getRecipient())
                .subject(emailLog.getSubject())
                .body(emailLog.getBody())
                .smtpMessageId(messageId)
                .status(InboundRequestEmailStatus.sent)
                .errorMessage(null)
                .sentAt(LocalDateTime.now())
                .createdAt(emailLog.getCreatedAt())
                .build();
        emailRepository.save(updated);

        log.info("[Mail] 입고 요청 발송 성공 — emailLogId={}, to={}",
                updated.getId(), updated.getRecipient());

        return InboundRequestSendResDto.builder()
                .emailLogId(updated.getId())
                .sentAt(updated.getSentAt())
                .status("sent")
                .build();
    }

    private InboundRequestSendResDto saveFailure(InboundRequestEmail emailLog, String errorMessage) {
        InboundRequestEmail updated = InboundRequestEmail.builder()
                .id(emailLog.getId())
                .etcOrderId(emailLog.getEtcOrderId())
                .sentBy(emailLog.getSentBy())
                .sentByName(emailLog.getSentByName())
                .sentByEmail(emailLog.getSentByEmail())
                .senderAddress(emailLog.getSenderAddress())
                .recipient(emailLog.getRecipient())
                .subject(emailLog.getSubject())
                .body(emailLog.getBody())
                .smtpMessageId(null)
                .status(InboundRequestEmailStatus.failed)
                .errorMessage(errorMessage)
                .sentAt(null)
                .createdAt(emailLog.getCreatedAt())
                .build();
        emailRepository.save(updated);

        return InboundRequestSendResDto.builder()
                .emailLogId(updated.getId())
                .sentAt(null)
                .status("failed")
                .errorMessage(errorMessage)
                .build();
    }
}
