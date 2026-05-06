package com.beyond.wbs.instruction.render.inbound;

import com.beyond.wbs.common.client.AccountServiceClient;
import com.beyond.wbs.common.client.MasterServiceClient;
import com.beyond.wbs.common.client.dto.ClientResDto;
import com.beyond.wbs.common.client.dto.ProductResDto;
import com.beyond.wbs.common.client.dto.SupplierResDto;
import com.beyond.wbs.common.client.dto.UserResDto;
import com.beyond.wbs.common.client.dto.WarehouseResDto;
import com.beyond.wbs.document.instruction.domain.InstructionDocumentType;
import com.beyond.wbs.document.instruction.render.InstructionDocumentRenderContext;
import com.beyond.wbs.document.instruction.render.InstructionDocumentRenderer;
import com.beyond.wbs.document.instruction.render.exception.InstructionRenderException;
import com.beyond.wbs.inbounds.domain.InboundOrderItems;
import com.beyond.wbs.inbounds.domain.InboundOrderStatus;
import com.beyond.wbs.inbounds.domain.InboundOrders;
import com.beyond.wbs.inbounds.repository.InboundOrderItemRepository;
import com.beyond.wbs.inbounds.repository.InboundOrderRepository;
import com.beyond.wbs.instruction.render.InstructionPdfFontRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class InboundOrderPdfRenderer
        implements InstructionDocumentRenderer<InboundOrderPdfData> {

    private static final Map<InboundOrderStatus, String> STATUS_LABEL = Map.of(
        InboundOrderStatus.draft,       "초안",
        InboundOrderStatus.approved,    "승인",
        InboundOrderStatus.received,    "검수완료",
        InboundOrderStatus.placing,     "적치중",
        InboundOrderStatus.completed,   "완료",
        InboundOrderStatus.partial,     "부분완료",
        InboundOrderStatus.cancelled,   "취소"
    );

    private final InboundOrderRepository inboundOrderRepository;
    private final InboundOrderItemRepository inboundOrderItemRepository;
    private final MasterServiceClient masterServiceClient;
    private final AccountServiceClient accountServiceClient;
    private final TemplateEngine templateEngine;
    private final InstructionPdfFontRegistry fontRegistry;

    @Override
    public InstructionDocumentType supportedType() {
        return InstructionDocumentType.INBOUND_ORDER;
    }

    @Override
    public InboundOrderPdfData loadData(UUID sourceId, UUID clientId) {
        InboundOrders order = inboundOrderRepository.findById(sourceId)
            .orElseThrow(() -> new InstructionRenderException(
                "입고지시서를 찾을 수 없음: sourceId=" + sourceId));
        if (!order.getClientId().equals(clientId)) {
            throw new InstructionRenderException(
                "입고지시서 회사 불일치: sourceId=" + sourceId);
        }

        List<InboundOrderItems> items = inboundOrderItemRepository.findByInboundOrderId(sourceId);
        String clientHeader = clientId.toString();

        String warehouseName = bestEffort(
            () -> masterServiceClient.getWarehouse(order.getWarehouseId(), clientHeader),
            WarehouseResDto::getName, "창고");
        String supplierName = order.getSupplierId() != null
            ? bestEffort(() -> masterServiceClient.getSupplier(order.getSupplierId(), clientHeader),
                         SupplierResDto::getName, "협력사")
            : "-";
        Map<UUID, ProductResDto> productMap = bestEffortBatchProducts(
            items.stream().map(InboundOrderItems::getProductId).distinct().toList(), clientHeader);

        String adminHeader = order.getApprovedBy() != null
            ? order.getApprovedBy().toString() : order.getCreatedBy().toString();
        String clientName = bestEffortQuiet(() -> accountServiceClient.getClient(clientId), ClientResDto::getName);
        String createdByName = bestEffortUserName(order.getCreatedBy(), adminHeader);
        String approvedByName = order.getApprovedBy() != null
            ? bestEffortUserName(order.getApprovedBy(), adminHeader) : null;

        List<InboundOrderPdfData.Line> lines = items.stream().map(it -> {
            ProductResDto p = productMap.get(it.getProductId());
            int qty = Optional.ofNullable(it.getOrderedQty()).orElse(0);
            BigDecimal unit = Optional.ofNullable(it.getUnitPrice()).orElse(BigDecimal.ZERO);
            return InboundOrderPdfData.Line.builder()
                .sku(p != null && p.getSku() != null ? p.getSku() : "-")
                .productName(p != null && p.getName() != null ? p.getName() : "(상품 미조회)")
                .orderedQty(qty)
                .receivedQty(Optional.ofNullable(it.getReceivedQty()).orElse(0))
                .defectQty(Optional.ofNullable(it.getDefectQty()).orElse(0))
                .unitPrice(unit)
                .amount(unit.multiply(BigDecimal.valueOf(qty)))
                .build();
        }).toList();

        int totalQty = lines.stream().mapToInt(InboundOrderPdfData.Line::getOrderedQty).sum();
        BigDecimal totalAmount = lines.stream()
            .map(InboundOrderPdfData.Line::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        return InboundOrderPdfData.builder()
            .clientName(clientName)
            .sourceNo(order.getOrderNo())
            .warehouseName(warehouseName)
            .supplierName(supplierName)
            .expectedDate(order.getExpectedDate())
            .note(order.getNote())
            .statusLabel(STATUS_LABEL.getOrDefault(order.getStatus(),
                order.getStatus() != null ? order.getStatus().name() : "-"))
            .originType(formatOriginType(order.getOriginType()))
            .createdByName(createdByName)
            .approvedByName(approvedByName)
            .issuedByName(approvedByName)
            .items(lines)
            .totalQty(totalQty)
            .totalAmount(totalAmount)
            .build();
    }

    @Override
    public byte[] render(InboundOrderPdfData data, InstructionDocumentRenderContext context) {
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
                "PDF 생성 실패: sourceNo=" + context.sourceNo(), e);
        }
    }

    private String formatOriginType(String s) {
        if (s == null || s.isBlank()) return "-";
        return switch (s) {
            case "purchase_order" -> "ERP";
            case "manual" -> "수동";
            case "return" -> "반품";
            default -> s;
        };
    }

    private String bestEffortUserName(UUID userId, String adminHeader) {
        if (userId == null) return null;
        try {
            UserResDto u = accountServiceClient.getUser(userId, adminHeader);
            return u != null ? u.getName() : null;
        } catch (Exception e) {
            log.warn("[InstructionPdf] 사용자 조회 실패 {}: {}", userId, e.getMessage());
            return null;
        }
    }

    private <T> String bestEffort(java.util.function.Supplier<T> call,
                                  Function<T, String> name, String label) {
        try {
            T r = call.get();
            return r == null ? "(미조회 " + label + ")" : name.apply(r);
        } catch (Exception e) {
            log.warn("[InstructionPdf] {} 조회 실패: {}", label, e.getMessage());
            return "(미조회 " + label + ")";
        }
    }

    private <T> String bestEffortQuiet(java.util.function.Supplier<T> call, Function<T, String> name) {
        try {
            T r = call.get();
            return r == null ? null : name.apply(r);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<UUID, ProductResDto> bestEffortBatchProducts(List<UUID> ids, String h) {
        if (ids.isEmpty()) return Map.of();
        try {
            return masterServiceClient.getProducts(ids, h).stream()
                .collect(Collectors.toMap(ProductResDto::getId, Function.identity()));
        } catch (Exception e) {
            return Map.of();
        }
    }
}
