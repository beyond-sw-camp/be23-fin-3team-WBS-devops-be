package com.beyond.wbs.instruction.render.picking;

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
import com.beyond.wbs.outbounds.domain.PickingList;
import com.beyond.wbs.outbounds.domain.PickingListItemStatus;
import com.beyond.wbs.outbounds.domain.PickingListItems;
import com.beyond.wbs.outbounds.domain.PickingListStatus;
import com.beyond.wbs.outbounds.repository.PickingListItemRepository;
import com.beyond.wbs.outbounds.repository.PickinglistRepository;
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
public class PickingListPdfRenderer
        implements InstructionDocumentRenderer<PickingListPdfData> {

    private static final Map<PickingListStatus, String> STATUS_LABEL = Map.of(
        PickingListStatus.pending,     "대기",
        PickingListStatus.in_progress, "진행중",
        PickingListStatus.completed,   "완료",
        PickingListStatus.partial,     "부분완료"
    );

    private static final Map<PickingListItemStatus, String> ITEM_STATUS_LABEL = Map.of(
        PickingListItemStatus.pending,   "대기",
        PickingListItemStatus.picking,   "피킹중",
        PickingListItemStatus.completed, "완료",
        PickingListItemStatus.shortage,  "부족"
    );

    private final PickinglistRepository pickinglistRepository;
    private final PickingListItemRepository pickingListItemRepository;
    private final MasterServiceClient masterServiceClient;
    private final AccountServiceClient accountServiceClient;
    private final TemplateEngine templateEngine;
    private final InstructionPdfFontRegistry fontRegistry;

    @Override
    public InstructionDocumentType supportedType() {
        return InstructionDocumentType.PICKING_LIST;
    }

    @Override
    public PickingListPdfData loadData(UUID sourceId, UUID clientId) {
        PickingList pl = pickinglistRepository.findById(sourceId)
            .orElseThrow(() -> new InstructionRenderException("피킹리스트 미존재: " + sourceId));
        if (!pl.getClientId().equals(clientId)) {
            throw new InstructionRenderException("회사 불일치");
        }
        List<PickingListItems> items = pickingListItemRepository.findByPickingListId(sourceId);
        String h = clientId.toString();
        String adminHeader = pl.getAssignedTo() != null
            ? pl.getAssignedTo().toString() : pl.getCreatedBy().toString();

        String warehouseName = bestEffort(() -> masterServiceClient.getWarehouse(pl.getWarehouseId(), h),
            WarehouseResDto::getName, "창고");
        Map<UUID, ProductResDto> productMap = bestEffortBatchProducts(
            items.stream().map(PickingListItems::getProductId).distinct().toList(), h);
        Map<UUID, LocationResDto> locMap = bestEffortBatchLocations(
            items.stream().map(PickingListItems::getLocationId).filter(Objects::nonNull).distinct().toList(), h);

        String clientName = bestEffortQuiet(() -> accountServiceClient.getClient(clientId), ClientResDto::getName);
        String assignedToName = bestEffortUserName(pl.getAssignedTo(), adminHeader);
        String createdByName = bestEffortUserName(pl.getCreatedBy(), adminHeader);

        List<PickingListPdfData.Line> lines = items.stream().map(it -> {
            ProductResDto p = productMap.get(it.getProductId());
            LocationResDto loc = it.getLocationId() != null ? locMap.get(it.getLocationId()) : null;
            return PickingListPdfData.Line.builder()
                .sku(p != null && p.getSku() != null ? p.getSku() : "-")
                .productName(p != null && p.getName() != null ? p.getName() : "(상품 미조회)")
                .locationName(loc != null ? formatLocation(loc) : "-")
                .qty(Optional.ofNullable(it.getQty()).orElse(0))
                .pickedQty(Optional.ofNullable(it.getPickedQty()).orElse(0))
                .lotNo(it.getLotNo())
                .statusLabel(ITEM_STATUS_LABEL.getOrDefault(it.getStatus(), "-"))
                .build();
        }).toList();

        int totalQty = lines.stream().mapToInt(PickingListPdfData.Line::getQty).sum();

        return PickingListPdfData.builder()
            .clientName(clientName)
            .sourceNo(pl.getPickingNo())
            .warehouseName(warehouseName)
            .statusLabel(STATUS_LABEL.getOrDefault(pl.getStatus(), "-"))
            .assignedToName(assignedToName)
            .createdByName(createdByName)
            .createdAt(pl.getCreatedAt())
            .items(lines)
            .totalQty(totalQty)
            .build();
    }

    @Override
    public byte[] render(PickingListPdfData data, InstructionDocumentRenderContext context) {
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
