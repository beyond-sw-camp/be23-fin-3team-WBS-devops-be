package com.beyond.wbs.instruction.render.transfer;

import com.beyond.wbs.common.client.AccountServiceClient;
import com.beyond.wbs.common.client.MasterServiceClient;
import com.beyond.wbs.common.client.dto.ClientResDto;
import com.beyond.wbs.common.client.dto.LocationResDto;
import com.beyond.wbs.common.client.dto.ProductResDto;
import com.beyond.wbs.common.client.dto.UserResDto;
import com.beyond.wbs.common.client.dto.WarehouseResDto;
import com.beyond.wbs.document.instruction.domain.InstructionDocumentType;
import com.beyond.wbs.document.instruction.render.InstructionDocumentRenderContext;
import com.beyond.wbs.document.instruction.render.InstructionDocumentRenderer;
import com.beyond.wbs.document.instruction.render.exception.InstructionRenderException;
import com.beyond.wbs.instruction.render.InstructionPdfFontRegistry;
import com.beyond.wbs.transfer.domain.TransferOrder;
import com.beyond.wbs.transfer.domain.TransferOrderItem;
import com.beyond.wbs.transfer.domain.TransferOrderStatus;
import com.beyond.wbs.transfer.repository.TransferOrderItemRepository;
import com.beyond.wbs.transfer.repository.TransferOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransferOrderPdfRenderer
        implements InstructionDocumentRenderer<TransferOrderPdfData> {

    private static final Map<TransferOrderStatus, String> STATUS_LABEL = Map.of(
        TransferOrderStatus.draft,       "초안",
        TransferOrderStatus.approved,    "승인",
        TransferOrderStatus.in_progress, "진행중",
        TransferOrderStatus.completed,   "완료",
        TransferOrderStatus.partial,     "부분완료",
        TransferOrderStatus.cancelled,   "취소"
    );

    private final TransferOrderRepository transferOrderRepository;
    private final TransferOrderItemRepository transferOrderItemRepository;
    private final MasterServiceClient masterServiceClient;
    private final AccountServiceClient accountServiceClient;
    private final TemplateEngine templateEngine;
    private final InstructionPdfFontRegistry fontRegistry;

    @Override
    public InstructionDocumentType supportedType() {
        return InstructionDocumentType.TRANSFER_ORDER;
    }

    @Override
    public TransferOrderPdfData loadData(UUID sourceId, UUID clientId) {
        TransferOrder order = transferOrderRepository.findById(sourceId)
            .orElseThrow(() -> new InstructionRenderException("이동지시서 미존재: " + sourceId));
        if (!order.getClientId().equals(clientId)) {
            throw new InstructionRenderException("회사 불일치");
        }
        List<TransferOrderItem> items = transferOrderItemRepository.findByTransferOrderId(sourceId);
        String h = clientId.toString();
        String adminHeader = order.getApprovedBy() != null
            ? order.getApprovedBy().toString() : order.getCreatedBy().toString();

        String fromWh = bestEffort(() -> masterServiceClient.getWarehouse(order.getFromWarehouseId(), h),
            WarehouseResDto::getName, "출발창고");
        String toWh = bestEffort(() -> masterServiceClient.getWarehouse(order.getToWarehouseId(), h),
            WarehouseResDto::getName, "도착창고");
        Map<UUID, ProductResDto> productMap = bestEffortBatchProducts(
            items.stream().map(TransferOrderItem::getProductId).distinct().toList(), h);

        List<UUID> locIds = items.stream()
            .flatMap(it -> Stream.of(it.getFromLocationId(), it.getToLocationId()))
            .filter(Objects::nonNull).distinct().toList();
        Map<UUID, LocationResDto> locMap = bestEffortBatchLocations(locIds, h);

        String clientName = bestEffortQuiet(() -> accountServiceClient.getClient(clientId), ClientResDto::getName);
        String createdByName = bestEffortUserName(order.getCreatedBy(), adminHeader);
        String approvedByName = order.getApprovedBy() != null
            ? bestEffortUserName(order.getApprovedBy(), adminHeader) : null;

        List<TransferOrderPdfData.Line> lines = items.stream().map(it -> {
            ProductResDto p = productMap.get(it.getProductId());
            LocationResDto fl = locMap.get(it.getFromLocationId());
            LocationResDto tl = locMap.get(it.getToLocationId());
            return TransferOrderPdfData.Line.builder()
                .sku(p != null && p.getSku() != null ? p.getSku() : "-")
                .productName(p != null && p.getName() != null ? p.getName() : "(상품 미조회)")
                .fromLocationName(fl != null ? formatLocation(fl) : "-")
                .toLocationName(tl != null ? formatLocation(tl) : "-")
                .orderedQty(Optional.ofNullable(it.getOrderedQty()).orElse(0))
                .processedQty(Optional.ofNullable(it.getProcessedQty()).orElse(0))
                .defectQty(Optional.ofNullable(it.getDefectQty()).orElse(0))
                .lotNo(it.getLotNo())
                .build();
        }).toList();

        int totalQty = lines.stream().mapToInt(TransferOrderPdfData.Line::getOrderedQty).sum();

        return TransferOrderPdfData.builder()
            .clientName(clientName)
            .sourceNo(order.getOrderNo())
            .fromWarehouseName(fromWh)
            .toWarehouseName(toWh)
            .expectedDate(order.getExpectedDate())
            .note(order.getNote())
            .statusLabel(STATUS_LABEL.getOrDefault(order.getStatus(), "-"))
            .createdByName(createdByName)
            .approvedByName(approvedByName)
            .items(lines)
            .totalQty(totalQty)
            .build();
    }

    @Override
    public byte[] render(TransferOrderPdfData data, InstructionDocumentRenderContext context) {
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
            throw new InstructionRenderException("PDF 생성 실패: " + context.sourceNo(), e);
        }
    }

    private String formatLocation(LocationResDto loc) {
        StringBuilder sb = new StringBuilder();
        if (loc.getRackCode() != null) sb.append(loc.getRackCode()).append(" / ");
        if (loc.getFloorNo() != null) sb.append(loc.getFloorNo()).append("층 / ");
        if (loc.getCode() != null) sb.append(loc.getCode());
        else if (sb.toString().endsWith(" / ")) sb.setLength(sb.length() - 3);
        if (sb.length() == 0) sb.append(loc.getId());
        return sb.toString();
    }

    private String bestEffortUserName(UUID userId, String adminHeader) {
        if (userId == null) return null;
        try { UserResDto u = accountServiceClient.getUser(userId, adminHeader);
            return u != null ? u.getName() : null; } catch (Exception e) { return null; }
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
    private Map<UUID, LocationResDto> bestEffortBatchLocations(List<UUID> ids, String h) {
        if (ids.isEmpty()) return Map.of();
        try { return masterServiceClient.getLocations(ids, h).stream()
            .collect(Collectors.toMap(LocationResDto::getId, Function.identity())); }
        catch (Exception e) { return Map.of(); }
    }
}
