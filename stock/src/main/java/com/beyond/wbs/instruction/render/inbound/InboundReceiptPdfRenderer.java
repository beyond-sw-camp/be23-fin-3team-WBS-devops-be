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
import com.beyond.wbs.evidence.defect.domain.DefectEvidenceSourceType;
import com.beyond.wbs.evidence.defect.repository.DefectEvidenceRepository;
import com.beyond.wbs.inbounds.domain.InboundOrders;
import com.beyond.wbs.inbounds.domain.InboundReceiptItemCondition;
import com.beyond.wbs.inbounds.domain.InboundReceiptItems;
import com.beyond.wbs.inbounds.domain.InboundReceipts;
import com.beyond.wbs.inbounds.repository.InboundOrderRepository;
import com.beyond.wbs.inbounds.repository.InboundReceiptItemRepository;
import com.beyond.wbs.inbounds.repository.InboundReceiptRepository;
import com.beyond.wbs.instruction.render.InstructionPdfFontRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class InboundReceiptPdfRenderer
        implements InstructionDocumentRenderer<InboundReceiptPdfData> {

    private static final Map<InboundReceiptItemCondition, String> CONDITION_LABEL = Map.of(
        InboundReceiptItemCondition.normal, "정상",
        InboundReceiptItemCondition.defect, "불량",
        InboundReceiptItemCondition.damaged, "파손"
    );

    private final InboundReceiptRepository inboundReceiptRepository;
    private final InboundReceiptItemRepository inboundReceiptItemRepository;
    private final InboundOrderRepository inboundOrderRepository;
    private final DefectEvidenceRepository defectEvidenceRepository;
    private final MasterServiceClient masterServiceClient;
    private final AccountServiceClient accountServiceClient;
    private final TemplateEngine templateEngine;
    private final InstructionPdfFontRegistry fontRegistry;

    @Override
    public InstructionDocumentType supportedType() {
        return InstructionDocumentType.INBOUND_RECEIPT;
    }

    @Override
    public InboundReceiptPdfData loadData(UUID sourceId, UUID clientId) {
        InboundReceipts receipt = inboundReceiptRepository.findById(sourceId)
            .orElseThrow(() -> new InstructionRenderException(
                "입고전표를 찾을 수 없음: sourceId=" + sourceId));
        InboundOrders order = inboundOrderRepository.findById(receipt.getInboundOrderId())
            .orElseThrow(() -> new InstructionRenderException(
                "입고지시서를 찾을 수 없음: orderId=" + receipt.getInboundOrderId()));
        if (!order.getClientId().equals(clientId)) {
            throw new InstructionRenderException("입고전표 회사 불일치: sourceId=" + sourceId);
        }

        List<InboundReceiptItems> items = inboundReceiptItemRepository.findByReceiptId(sourceId);
        String clientHeader = clientId.toString();
        String adminHeader = receipt.getReceivedBy().toString();

        String warehouseName = bestEffort(
            () -> masterServiceClient.getWarehouse(receipt.getWarehouseId(), clientHeader),
            WarehouseResDto::getName, "창고");
        String supplierName = order.getSupplierId() != null
            ? bestEffort(() -> masterServiceClient.getSupplier(order.getSupplierId(), clientHeader),
                         SupplierResDto::getName, "협력사")
            : "-";
        Map<UUID, ProductResDto> productMap = bestEffortBatchProducts(
            items.stream().map(InboundReceiptItems::getProductId).distinct().toList(), clientHeader);

        String clientName = bestEffortQuiet(() -> accountServiceClient.getClient(clientId), ClientResDto::getName);
        String receivedByName = bestEffortUserName(receipt.getReceivedBy(), adminHeader);

        List<InboundReceiptPdfData.Line> lines = items.stream().map(it -> {
            ProductResDto p = productMap.get(it.getProductId());
            return InboundReceiptPdfData.Line.builder()
                .sku(p != null && p.getSku() != null ? p.getSku() : "-")
                .productName(p != null && p.getName() != null ? p.getName() : "(상품 미조회)")
                .qty(Optional.ofNullable(it.getQty()).orElse(0))
                .lotNo(it.getLotNo())
                .expiryDate(it.getExpiryDate())
                .conditionLabel(CONDITION_LABEL.getOrDefault(it.getItemCondition(), "-"))
                .inspectedByName(it.getInspectedBy() != null
                    ? bestEffortUserName(it.getInspectedBy(), adminHeader) : null)
                .build();
        }).toList();

        int normalQty = lines.stream()
            .filter(l -> "정상".equals(l.getConditionLabel()))
            .mapToInt(InboundReceiptPdfData.Line::getQty).sum();
        int defectQty = lines.stream()
            .filter(l -> !"정상".equals(l.getConditionLabel()))
            .mapToInt(InboundReceiptPdfData.Line::getQty).sum();

        // 첨부된 불량 사진 카운트 —
        // 사진은 inbound_order_items 단위로 anchor(검수 전·중·후 모두 첨부 가능하도록).
        // receipt items.orderItemId 로 역매핑해서 카운트 조회.
        List<UUID> orderItemIds = items.stream()
            .map(InboundReceiptItems::getOrderItemId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
        long evidenceCount = orderItemIds.isEmpty() ? 0L
            : defectEvidenceRepository.countReadyBySourceIds(
                clientId, DefectEvidenceSourceType.INBOUND_ORDER_ITEM, orderItemIds);

        return InboundReceiptPdfData.builder()
            .clientName(clientName)
            .sourceNo(receipt.getReceiptNo())
            .warehouseName(warehouseName)
            .inboundOrderNo(order.getOrderNo())
            .supplierName(supplierName)
            .receivedAt(receipt.getReceivedAt())
            .note(receipt.getNote())
            .receivedByName(receivedByName)
            .items(lines)
            .totalNormalQty(normalQty)
            .totalDefectQty(defectQty)
            .defectEvidenceCount(evidenceCount)
            .build();
    }

    @Override
    public byte[] render(InboundReceiptPdfData data, InstructionDocumentRenderContext context) {
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
            throw new InstructionRenderException("PDF 생성 실패: sourceNo=" + context.sourceNo(), e);
        }
    }

    private String bestEffortUserName(UUID userId, String adminHeader) {
        if (userId == null) return null;
        try {
            UserResDto u = accountServiceClient.getUser(userId, adminHeader);
            return u != null ? u.getName() : null;
        } catch (Exception e) { return null; }
    }
    private <T> String bestEffort(java.util.function.Supplier<T> c, Function<T, String> n, String label) {
        try { T r = c.get(); return r == null ? "(미조회 " + label + ")" : n.apply(r); }
        catch (Exception e) { return "(미조회 " + label + ")"; }
    }
    private <T> String bestEffortQuiet(java.util.function.Supplier<T> c, Function<T, String> n) {
        try { T r = c.get(); return r == null ? null : n.apply(r); }
        catch (Exception e) { return null; }
    }
    private Map<UUID, ProductResDto> bestEffortBatchProducts(List<UUID> ids, String h) {
        if (ids.isEmpty()) return Map.of();
        try { return masterServiceClient.getProducts(ids, h).stream()
            .collect(Collectors.toMap(ProductResDto::getId, Function.identity())); }
        catch (Exception e) { return Map.of(); }
    }
}
