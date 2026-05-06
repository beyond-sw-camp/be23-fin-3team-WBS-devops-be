package com.beyond.wbs.instruction.render.etc;

import com.beyond.wbs.common.client.AccountServiceClient;
import com.beyond.wbs.common.client.MasterServiceClient;
import com.beyond.wbs.common.client.dto.ClientResDto;
import com.beyond.wbs.common.client.dto.LocationResDto;
import com.beyond.wbs.common.client.dto.ProductResDto;
import com.beyond.wbs.common.client.dto.StoreResDto;
import com.beyond.wbs.common.client.dto.SupplierResDto;
import com.beyond.wbs.common.client.dto.UserResDto;
import com.beyond.wbs.common.client.dto.WarehouseResDto;
import com.beyond.wbs.document.instruction.domain.InstructionDocumentType;
import com.beyond.wbs.document.instruction.render.InstructionDocumentRenderContext;
import com.beyond.wbs.document.instruction.render.InstructionDocumentRenderer;
import com.beyond.wbs.document.instruction.render.exception.InstructionRenderException;
import com.beyond.wbs.etcinout.domain.Direction;
import com.beyond.wbs.etcinout.domain.EtcInoutOrder;
import com.beyond.wbs.etcinout.domain.EtcInoutOrderItem;
import com.beyond.wbs.etcinout.domain.EtcInoutStatus;
import com.beyond.wbs.etcinout.domain.IoType;
import com.beyond.wbs.etcinout.domain.ItemCondition;
import com.beyond.wbs.etcinout.repository.EtcInoutOrderItemRepository;
import com.beyond.wbs.etcinout.repository.EtcInoutOrderRepository;
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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class EtcInoutOrderPdfRenderer
        implements InstructionDocumentRenderer<EtcInoutOrderPdfData> {

    private static final Map<EtcInoutStatus, String> STATUS_LABEL = Map.of(
        EtcInoutStatus.draft,     "처리중",
        EtcInoutStatus.completed, "완료",
        EtcInoutStatus.cancelled, "취소"
    );

    private static final Map<IoType, String> IO_TYPE_LABEL = Map.of(
        IoType.sample_in,  "샘플입고",
        IoType.adjust_in,  "조정입고",
        IoType.dispose_in, "폐기입고",
        IoType.dispose_out,"폐기출고",
        IoType.sample_out, "샘플출고",
        IoType.adjust_out, "조정출고",
        IoType.etc_in,     "기타입고",
        IoType.etc_out,    "기타출고"
    );

    private static final Map<ItemCondition, String> CONDITION_LABEL = Map.of(
        ItemCondition.normal,  "정상",
        ItemCondition.defect,  "불량",
        ItemCondition.expired, "유통기한경과"
    );

    private final EtcInoutOrderRepository orderRepository;
    private final EtcInoutOrderItemRepository itemRepository;
    private final MasterServiceClient masterServiceClient;
    private final AccountServiceClient accountServiceClient;
    private final TemplateEngine templateEngine;
    private final InstructionPdfFontRegistry fontRegistry;

    @Override
    public InstructionDocumentType supportedType() {
        return InstructionDocumentType.ETC_INOUT_ORDER;
    }

    @Override
    public EtcInoutOrderPdfData loadData(UUID sourceId, UUID clientId) {
        EtcInoutOrder order = orderRepository.findById(sourceId)
            .orElseThrow(() -> new InstructionRenderException("기타입출고지시서 미존재: " + sourceId));
        if (!order.getClientId().equals(clientId)) {
            throw new InstructionRenderException("회사 불일치");
        }
        List<EtcInoutOrderItem> items = itemRepository.findByEtcOrderId(sourceId);
        String h = clientId.toString();
        String adminHeader = order.getCreatedBy().toString();

        String warehouseName = bestEffort(() -> masterServiceClient.getWarehouse(order.getWarehouseId(), h),
            WarehouseResDto::getName, "창고");
        String partnerName = "-";
        String partnerLabel = "-";
        if (order.getDirection() == Direction.in && order.getSupplierId() != null) {
            partnerLabel = "협력사";
            partnerName = bestEffort(() -> masterServiceClient.getSupplier(order.getSupplierId(), h),
                SupplierResDto::getName, "협력사");
        } else if (order.getDirection() == Direction.out && order.getStoreId() != null) {
            partnerLabel = "출고처";
            partnerName = bestEffort(() -> masterServiceClient.getStore(order.getStoreId(), h),
                StoreResDto::getName, "출고처");
        }

        Map<UUID, ProductResDto> productMap = bestEffortBatchProducts(
            items.stream().map(EtcInoutOrderItem::getProductId).distinct().toList(), h);
        Map<UUID, LocationResDto> locMap = bestEffortBatchLocations(
            items.stream().map(EtcInoutOrderItem::getLocationId).filter(Objects::nonNull).distinct().toList(), h);

        String clientName = bestEffortQuiet(() -> accountServiceClient.getClient(clientId), ClientResDto::getName);
        String createdByName = bestEffortUserName(order.getCreatedBy(), adminHeader);

        List<EtcInoutOrderPdfData.Line> lines = items.stream().map(it -> {
            ProductResDto p = productMap.get(it.getProductId());
            LocationResDto loc = it.getLocationId() != null ? locMap.get(it.getLocationId()) : null;
            return EtcInoutOrderPdfData.Line.builder()
                .sku(p != null && p.getSku() != null ? p.getSku() : "-")
                .productName(p != null && p.getName() != null ? p.getName() : "(상품 미조회)")
                .locationName(loc != null ? formatLocation(loc) : "-")
                .qty(Optional.ofNullable(it.getQty()).orElse(0))
                .processedQty(Optional.ofNullable(it.getProcessedQty()).orElse(0))
                .lotNo(it.getLotNo())
                .conditionLabel(it.getCondition() != null
                    ? CONDITION_LABEL.getOrDefault(it.getCondition(), "-") : "-")
                .note(it.getNote())
                .build();
        }).toList();

        int totalQty = lines.stream().mapToInt(EtcInoutOrderPdfData.Line::getQty).sum();

        return EtcInoutOrderPdfData.builder()
            .clientName(clientName)
            .sourceNo(order.getOrderNo())
            .warehouseName(warehouseName)
            .ioTypeLabel(IO_TYPE_LABEL.getOrDefault(order.getIoType(), "-"))
            .directionLabel(order.getDirection() == Direction.in ? "입고" : "출고")
            .partnerName(partnerName)
            .partnerLabel(partnerLabel)
            .note(order.getNote())
            .statusLabel(STATUS_LABEL.getOrDefault(order.getStatus(), "-"))
            .createdByName(createdByName)
            .createdAt(order.getCreatedAt())
            .completedAt(order.getCompletedAt())
            .items(lines)
            .totalQty(totalQty)
            .build();
    }

    @Override
    public byte[] render(EtcInoutOrderPdfData data, InstructionDocumentRenderContext context) {
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
