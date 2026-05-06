package com.beyond.wbs.instruction.render.inventory;

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
import com.beyond.wbs.inventory.domain.StockCountItem;
import com.beyond.wbs.inventory.domain.StockCountItemStatus;
import com.beyond.wbs.inventory.domain.StockCountOrder;
import com.beyond.wbs.inventory.domain.StockCountStatus;
import com.beyond.wbs.inventory.repository.StockCountItemRepository;
import com.beyond.wbs.inventory.repository.StockCountOrderRepository;
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

@Component
@RequiredArgsConstructor
@Slf4j
public class StockCountOrderPdfRenderer
        implements InstructionDocumentRenderer<StockCountOrderPdfData> {

    private static final Map<StockCountStatus, String> STATUS_LABEL = Map.of(
        StockCountStatus.draft,       "초안",
        StockCountStatus.in_progress, "실사중",
        StockCountStatus.completed,   "완료",
        StockCountStatus.cancelled,   "취소"
    );

    private static final Map<StockCountItemStatus, String> ITEM_STATUS_LABEL = Map.of(
        StockCountItemStatus.pending,  "대기",
        StockCountItemStatus.counted,  "실사완료",
        StockCountItemStatus.adjusted, "조정완료"
    );

    private final StockCountOrderRepository countOrderRepository;
    private final StockCountItemRepository countItemRepository;
    private final MasterServiceClient masterServiceClient;
    private final AccountServiceClient accountServiceClient;
    private final TemplateEngine templateEngine;
    private final InstructionPdfFontRegistry fontRegistry;

    @Override
    public InstructionDocumentType supportedType() {
        return InstructionDocumentType.STOCK_COUNT_ORDER;
    }

    @Override
    public StockCountOrderPdfData loadData(UUID sourceId, UUID clientId) {
        StockCountOrder order = countOrderRepository.findById(sourceId)
            .orElseThrow(() -> new InstructionRenderException("실사지시서 미존재: " + sourceId));
        if (!order.getClientId().equals(clientId)) {
            throw new InstructionRenderException("회사 불일치");
        }
        List<StockCountItem> items = countItemRepository.findByCountOrderId(sourceId);
        String h = clientId.toString();
        String adminHeader = order.getApprovedBy() != null
            ? order.getApprovedBy().toString() : order.getCreatedBy().toString();

        String warehouseName = bestEffort(() -> masterServiceClient.getWarehouse(order.getWarehouseId(), h),
            WarehouseResDto::getName, "창고");
        Map<UUID, ProductResDto> productMap = bestEffortBatchProducts(
            items.stream().map(StockCountItem::getProductId).distinct().toList(), h);
        Map<UUID, LocationResDto> locMap = bestEffortBatchLocations(
            items.stream().map(StockCountItem::getLocationId).filter(Objects::nonNull).distinct().toList(), h);

        String clientName = bestEffortQuiet(() -> accountServiceClient.getClient(clientId), ClientResDto::getName);
        String createdByName = bestEffortUserName(order.getCreatedBy(), adminHeader);
        String approvedByName = order.getApprovedBy() != null
            ? bestEffortUserName(order.getApprovedBy(), adminHeader) : null;

        List<StockCountOrderPdfData.Line> lines = items.stream().map(it -> {
            ProductResDto p = productMap.get(it.getProductId());
            LocationResDto loc = it.getLocationId() != null ? locMap.get(it.getLocationId()) : null;
            return StockCountOrderPdfData.Line.builder()
                .sku(p != null && p.getSku() != null ? p.getSku() : "-")
                .productName(p != null && p.getName() != null ? p.getName() : "(상품 미조회)")
                .locationName(loc != null ? formatLocation(loc) : "-")
                .systemQty(Optional.ofNullable(it.getSystemQty()).orElse(0))
                .countQty(it.getCountQty())
                .diffQty(it.getDiffQty())
                .statusLabel(ITEM_STATUS_LABEL.getOrDefault(it.getStatus(), "-"))
                .build();
        }).toList();

        int totalSystem = lines.stream().mapToInt(StockCountOrderPdfData.Line::getSystemQty).sum();
        int totalCount = lines.stream().filter(l -> l.getCountQty() != null)
            .mapToInt(StockCountOrderPdfData.Line::getCountQty).sum();
        int totalDiff = lines.stream().filter(l -> l.getDiffQty() != null)
            .mapToInt(StockCountOrderPdfData.Line::getDiffQty).sum();

        return StockCountOrderPdfData.builder()
            .clientName(clientName)
            .sourceNo(order.getOrderNo())
            .warehouseName(warehouseName)
            .statusLabel(STATUS_LABEL.getOrDefault(order.getStatus(), "-"))
            .createdByName(createdByName)
            .approvedByName(approvedByName)
            .createdAt(order.getCreatedAt())
            .approvedAt(order.getApprovedAt())
            .note(order.getNote())
            .items(lines)
            .totalSystemQty(totalSystem)
            .totalCountQty(totalCount)
            .totalDiffQty(totalDiff)
            .build();
    }

    @Override
    public byte[] render(StockCountOrderPdfData data, InstructionDocumentRenderContext context) {
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
