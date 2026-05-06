package com.beyond.wbs.instruction.render.inbound;

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
import com.beyond.wbs.inbounds.domain.InboundOrders;
import com.beyond.wbs.inbounds.domain.PlacementItems;
import com.beyond.wbs.inbounds.domain.PlacementOrderStatus;
import com.beyond.wbs.inbounds.domain.PlacementOrders;
import com.beyond.wbs.inbounds.repository.InboundOrderRepository;
import com.beyond.wbs.inbounds.repository.PlacementItemRepository;
import com.beyond.wbs.inbounds.repository.PlacementOrderRepository;
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
public class PlacementOrderPdfRenderer
        implements InstructionDocumentRenderer<PlacementOrderPdfData> {

    private static final Map<PlacementOrderStatus, String> STATUS_LABEL = Map.of(
        PlacementOrderStatus.pending,     "대기",
        PlacementOrderStatus.in_progress, "진행중",
        PlacementOrderStatus.completed,   "완료"
    );

    private final PlacementOrderRepository placementOrderRepository;
    private final PlacementItemRepository placementItemRepository;
    private final InboundOrderRepository inboundOrderRepository;
    private final MasterServiceClient masterServiceClient;
    private final AccountServiceClient accountServiceClient;
    private final TemplateEngine templateEngine;
    private final InstructionPdfFontRegistry fontRegistry;

    @Override
    public InstructionDocumentType supportedType() {
        return InstructionDocumentType.PLACEMENT_ORDER;
    }

    @Override
    public PlacementOrderPdfData loadData(UUID sourceId, UUID clientId) {
        PlacementOrders po = placementOrderRepository.findById(sourceId)
            .orElseThrow(() -> new InstructionRenderException(
                "적치지시서를 찾을 수 없음: sourceId=" + sourceId));
        if (!po.getClientId().equals(clientId)) {
            throw new InstructionRenderException("적치지시서 회사 불일치");
        }

        InboundOrders inboundOrder = inboundOrderRepository.findById(po.getInboundOrderId()).orElse(null);
        List<PlacementItems> items = placementItemRepository.findByPlacementOrderId(sourceId);
        String h = clientId.toString();
        String adminHeader = po.getAssignedTo() != null
            ? po.getAssignedTo().toString() : (po.getCompletedBy() != null ? po.getCompletedBy().toString() : clientId.toString());

        String warehouseName = bestEffort(
            () -> masterServiceClient.getWarehouse(po.getWarehouseId(), h),
            WarehouseResDto::getName, "창고");
        Map<UUID, ProductResDto> productMap = bestEffortBatchProducts(
            items.stream().map(PlacementItems::getProductId).distinct().toList(), h);
        Map<UUID, LocationResDto> locMap = bestEffortBatchLocations(
            items.stream().map(PlacementItems::getLocationId).filter(java.util.Objects::nonNull).distinct().toList(), h);

        String clientName = bestEffortQuiet(() -> accountServiceClient.getClient(clientId), ClientResDto::getName);
        String assignedToName = po.getAssignedTo() != null
            ? bestEffortUserName(po.getAssignedTo(), adminHeader) : null;

        List<PlacementOrderPdfData.Line> lines = items.stream().map(it -> {
            ProductResDto p = productMap.get(it.getProductId());
            LocationResDto loc = it.getLocationId() != null ? locMap.get(it.getLocationId()) : null;
            return PlacementOrderPdfData.Line.builder()
                .sku(p != null && p.getSku() != null ? p.getSku() : "-")
                .productName(p != null && p.getName() != null ? p.getName() : "(상품 미조회)")
                .locationName(loc != null ? formatLocation(loc) : "(미배정)")
                .qty(Optional.ofNullable(it.getQty()).orElse(0))
                .lotNo(it.getLotNo())
                .isDefect(it.isDefect())
                .isPlaced(it.isPlaced())
                .completedByName(it.getCompletedBy() != null
                    ? bestEffortUserName(it.getCompletedBy(), adminHeader) : null)
                .build();
        }).toList();

        int totalQty = lines.stream().mapToInt(PlacementOrderPdfData.Line::getQty).sum();
        int totalDefectQty = lines.stream().filter(PlacementOrderPdfData.Line::isDefect)
            .mapToInt(PlacementOrderPdfData.Line::getQty).sum();

        return PlacementOrderPdfData.builder()
            .clientName(clientName)
            .sourceNo(po.getPlacementNo())
            .warehouseName(warehouseName)
            .inboundOrderNo(inboundOrder != null ? inboundOrder.getOrderNo() : "-")
            .statusLabel(STATUS_LABEL.getOrDefault(po.getStatus(), "-"))
            .assignedToName(assignedToName)
            .createdAt(po.getCreatedAt())
            .completedAt(po.getCompletedAt())
            .items(lines)
            .totalQty(totalQty)
            .totalDefectQty(totalDefectQty)
            .build();
    }

    @Override
    public byte[] render(PlacementOrderPdfData data, InstructionDocumentRenderContext context) {
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

    private String formatLocation(LocationResDto loc) {
        // 예: "A존 / R-12 / 3층 / L-001"
        StringBuilder sb = new StringBuilder();
        if (loc.getZoneCode() != null) sb.append(loc.getZoneCode()).append(" / ");
        if (loc.getRackCode() != null) sb.append(loc.getRackCode()).append(" / ");
        if (loc.getFloorNo() != null) sb.append(loc.getFloorNo()).append("층 / ");
        if (loc.getCode() != null) sb.append(loc.getCode());
        else if (sb.length() == 0) sb.append(loc.getId());
        else if (sb.toString().endsWith(" / ")) sb.setLength(sb.length() - 3);
        return sb.toString();
    }

    private String bestEffortUserName(UUID userId, String adminHeader) {
        if (userId == null) return null;
        try { UserResDto u = accountServiceClient.getUser(userId, adminHeader);
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
    private Map<UUID, LocationResDto> bestEffortBatchLocations(List<UUID> ids, String h) {
        if (ids.isEmpty()) return Map.of();
        try { return masterServiceClient.getLocations(ids, h).stream()
            .collect(Collectors.toMap(LocationResDto::getId, Function.identity())); }
        catch (Exception e) { return Map.of(); }
    }
}
