package com.beyond.wbs.instruction.render.outbound;

import com.beyond.wbs.common.client.AccountServiceClient;
import com.beyond.wbs.common.client.MasterServiceClient;
import com.beyond.wbs.common.client.dto.ClientResDto;
import com.beyond.wbs.common.client.dto.ProductResDto;
import com.beyond.wbs.common.client.dto.StoreResDto;
import com.beyond.wbs.common.client.dto.UserResDto;
import com.beyond.wbs.common.client.dto.WarehouseResDto;
import com.beyond.wbs.document.instruction.domain.InstructionDocumentType;
import com.beyond.wbs.document.instruction.render.InstructionDocumentRenderContext;
import com.beyond.wbs.document.instruction.render.InstructionDocumentRenderer;
import com.beyond.wbs.document.instruction.render.exception.InstructionRenderException;
import com.beyond.wbs.instruction.render.InstructionPdfFontRegistry;
import com.beyond.wbs.outbounds.domain.OutboundOrderItems;
import com.beyond.wbs.outbounds.domain.OutboundOrderStatus;
import com.beyond.wbs.outbounds.domain.OutboundOrders;
import com.beyond.wbs.outbounds.repository.OutboundOrderItemRepository;
import com.beyond.wbs.outbounds.repository.OutboundOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboundOrderPdfRenderer
        implements InstructionDocumentRenderer<OutboundOrderPdfData> {

    private static final Map<OutboundOrderStatus, String> STATUS_LABEL = Map.of(
        OutboundOrderStatus.draft,       "초안",
        OutboundOrderStatus.approved,    "승인",
        OutboundOrderStatus.in_progress, "처리중",
        OutboundOrderStatus.completed,   "완료",
        OutboundOrderStatus.partial,     "부분완료",
        OutboundOrderStatus.cancelled,   "취소"
    );

    private final OutboundOrderRepository outboundOrderRepository;
    private final OutboundOrderItemRepository outboundOrderItemRepository;
    private final MasterServiceClient masterServiceClient;
    private final AccountServiceClient accountServiceClient;
    private final TemplateEngine templateEngine;
    private final InstructionPdfFontRegistry fontRegistry;

    @Override
    public InstructionDocumentType supportedType() {
        return InstructionDocumentType.OUTBOUND_ORDER;
    }

    @Override
    public OutboundOrderPdfData loadData(UUID sourceId, UUID clientId) {
        OutboundOrders order = outboundOrderRepository
            .findByIdAndClientId(sourceId, clientId)
            .orElseThrow(() -> new InstructionRenderException(
                "출고지시서를 찾을 수 없음: sourceId=" + sourceId + " clientId=" + clientId));

        List<OutboundOrderItems> items =
            outboundOrderItemRepository.findByOutboundOrdersId(sourceId);

        String clientHeader = clientId.toString();

        // master 호출 — best-effort
        String warehouseName = bestEffort(
            () -> masterServiceClient.getWarehouse(order.getWarehouseId(), clientHeader),
            WarehouseResDto::getName, "창고");
        String storeName = bestEffort(
            () -> masterServiceClient.getStore(order.getStoreId(), clientHeader),
            StoreResDto::getName, "출고처");
        Map<UUID, ProductResDto> productMap = bestEffortBatchProducts(
            items.stream().map(OutboundOrderItems::getProductId).distinct().toList(),
            clientHeader);

        // 발행 주체 회사 (clientId → name) — 발행자 ID를 X-User-Id로 사용해 best-effort
        String adminHeader = order.getApprovedBy() != null
            ? order.getApprovedBy().toString()
            : order.getCreatedBy().toString();
        String clientName = bestEffortQuiet(
            () -> accountServiceClient.getClient(clientId),
            ClientResDto::getName);

        // 사용자명 3종 — best-effort. account-service 권한 거부 시 null fallback
        String createdByName = bestEffortUserName(order.getCreatedBy(), adminHeader);
        String approvedByName = order.getApprovedBy() != null
            ? bestEffortUserName(order.getApprovedBy(), adminHeader) : null;
        String issuedByName = approvedByName;  // 승인 = 발행 (PoC #1)

        // 품목·금액 계산
        List<OutboundOrderPdfData.Line> lines = items.stream()
            .map(it -> {
                ProductResDto p = productMap.get(it.getProductId());
                int qty = Optional.ofNullable(it.getOrderedQty()).orElse(0);
                BigDecimal unitPrice = Optional.ofNullable(it.getUnitPrice()).orElse(BigDecimal.ZERO);
                BigDecimal amount = unitPrice.multiply(BigDecimal.valueOf(qty));
                return OutboundOrderPdfData.Line.builder()
                    .sku(p != null && p.getSku() != null ? p.getSku() : "-")
                    .productName(p != null && p.getName() != null ? p.getName() : "(상품 미조회)")
                    .qty(qty)
                    .unitPrice(unitPrice)
                    .amount(amount)
                    .note(null)  // OutboundOrderItems에 item-level 비고 컬럼 없음
                    .build();
            })
            .toList();

        int totalQty = lines.stream().mapToInt(OutboundOrderPdfData.Line::getQty).sum();
        BigDecimal totalAmount = lines.stream()
            .map(OutboundOrderPdfData.Line::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return OutboundOrderPdfData.builder()
            .clientName(clientName)
            .customerName(storeName)
            .sourceNo(order.getOrderNo())
            .warehouseName(warehouseName)
            .storeName(storeName)
            .shippingAddress(order.getShippingAddress())
            .note(order.getNote())
            .scheduledDate(order.getScheduledDate())
            .statusLabel(STATUS_LABEL.getOrDefault(order.getStatus(),
                order.getStatus() != null ? order.getStatus().name() : "-"))
            .originType(formatOriginType(order.getOriginType()))
            .createdByName(createdByName)
            .issuedByName(issuedByName)
            .approvedByName(approvedByName)
            .items(lines)
            .totalQty(totalQty)
            .totalAmount(totalAmount)
            .build();
    }

    @Override
    public byte[] render(OutboundOrderPdfData data, InstructionDocumentRenderContext context) {
        Context tlCtx = new Context();
        tlCtx.setVariable("data", data);
        tlCtx.setVariable("context", context);

        String html = templateEngine.process(supportedType().getTemplatePath(), tlCtx);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024)) {
            ITextRenderer renderer = new ITextRenderer();
            fontRegistry.registerInto(renderer.getFontResolver());
            renderer.setDocumentFromString(html);
            renderer.layout();
            renderer.createPDF(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new InstructionRenderException(
                "PDF 생성 실패: sourceNo=" + context.sourceNo() +
                " version=" + context.version(), e);
        }
    }

    // ---- best-effort 헬퍼 ----

    private String formatOriginType(String originType) {
        if (originType == null || originType.isBlank()) return "-";
        // sales_order → ERP, 그 외는 원본 값 그대로
        return "sales_order".equalsIgnoreCase(originType) ? "ERP" : originType;
    }

    private String bestEffortUserName(UUID userId, String adminHeader) {
        if (userId == null) return null;
        try {
            UserResDto u = accountServiceClient.getUser(userId, adminHeader);
            return u != null ? u.getName() : null;
        } catch (Exception e) {
            log.warn("[InstructionPdf] 사용자 조회 실패 userId={} : {}", userId, e.getMessage());
            return null;
        }
    }

    private <T> String bestEffort(java.util.function.Supplier<T> call,
                                  Function<T, String> name,
                                  String label) {
        try {
            T resp = call.get();
            return resp == null ? "(미조회 " + label + ")" : name.apply(resp);
        } catch (Exception e) {
            log.warn("[InstructionPdf] {} 조회 실패 — fallback: {}", label, e.getMessage());
            return "(미조회 " + label + ")";
        }
    }

    private <T> String bestEffortQuiet(java.util.function.Supplier<T> call,
                                       Function<T, String> name) {
        try {
            T resp = call.get();
            return resp == null ? null : name.apply(resp);
        } catch (Exception e) {
            log.warn("[InstructionPdf] 회사 조회 실패: {}", e.getMessage());
            return null;
        }
    }

    private Map<UUID, ProductResDto> bestEffortBatchProducts(List<UUID> ids, String clientHeader) {
        if (ids.isEmpty()) return Map.of();
        try {
            return masterServiceClient.getProducts(ids, clientHeader).stream()
                .collect(Collectors.toMap(ProductResDto::getId, Function.identity()));
        } catch (Exception e) {
            log.warn("[InstructionPdf] 상품 배치 조회 실패 — fallback: {}", e.getMessage());
            return new HashMap<>();
        }
    }
}
