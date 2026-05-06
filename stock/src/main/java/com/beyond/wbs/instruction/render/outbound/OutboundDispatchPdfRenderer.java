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
import com.beyond.wbs.outbounds.domain.OutboundDispatch;
import com.beyond.wbs.outbounds.domain.OutboundDispatchItems;
import com.beyond.wbs.outbounds.domain.OutboundOrders;
import com.beyond.wbs.outbounds.repository.OutboundDispatchItemRepository;
import com.beyond.wbs.outbounds.repository.OutboundDispatchRepository;
import com.beyond.wbs.outbounds.repository.OutboundOrderRepository;
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
public class OutboundDispatchPdfRenderer
        implements InstructionDocumentRenderer<OutboundDispatchPdfData> {

    private final OutboundDispatchRepository outboundDispatchRepository;
    private final OutboundDispatchItemRepository outboundDispatchItemRepository;
    private final OutboundOrderRepository outboundOrderRepository;
    private final MasterServiceClient masterServiceClient;
    private final AccountServiceClient accountServiceClient;
    private final TemplateEngine templateEngine;
    private final InstructionPdfFontRegistry fontRegistry;

    @Override
    public InstructionDocumentType supportedType() {
        return InstructionDocumentType.OUTBOUND_DISPATCH;
    }

    @Override
    public OutboundDispatchPdfData loadData(UUID sourceId, UUID clientId) {
        OutboundDispatch d = outboundDispatchRepository.findByIdAndClientId(sourceId, clientId)
            .orElseThrow(() -> new InstructionRenderException("출고전표 미존재: " + sourceId));

        OutboundOrders order = outboundOrderRepository.findById(d.getOutboundOrdersId()).orElse(null);
        List<OutboundDispatchItems> items = outboundDispatchItemRepository.findByDispatchId(sourceId);
        String h = clientId.toString();
        String adminHeader = d.getDispatchedBy().toString();

        String warehouseName = bestEffort(() -> masterServiceClient.getWarehouse(d.getWarehouseId(), h),
            WarehouseResDto::getName, "창고");
        String storeName = order != null && order.getStoreId() != null
            ? bestEffort(() -> masterServiceClient.getStore(order.getStoreId(), h),
                         StoreResDto::getName, "출고처")
            : "-";
        Map<UUID, ProductResDto> productMap = bestEffortBatchProducts(
            items.stream().map(OutboundDispatchItems::getProductId).distinct().toList(), h);

        String clientName = bestEffortQuiet(() -> accountServiceClient.getClient(clientId), ClientResDto::getName);
        String dispatchedByName = bestEffortUserName(d.getDispatchedBy(), adminHeader);

        List<OutboundDispatchPdfData.Line> lines = items.stream().map(it -> {
            ProductResDto p = productMap.get(it.getProductId());
            return OutboundDispatchPdfData.Line.builder()
                .sku(p != null && p.getSku() != null ? p.getSku() : "-")
                .productName(p != null && p.getName() != null ? p.getName() : "(상품 미조회)")
                .qty(Optional.ofNullable(it.getQty()).orElse(0))
                .lotNo(it.getLotNo())
                .build();
        }).toList();

        int totalQty = lines.stream().mapToInt(OutboundDispatchPdfData.Line::getQty).sum();

        return OutboundDispatchPdfData.builder()
            .clientName(clientName)
            .sourceNo(d.getDispatchNo())
            .warehouseName(warehouseName)
            .outboundOrderNo(order != null ? order.getOrderNo() : "-")
            .storeName(storeName)
            .dispatchedByName(dispatchedByName)
            .dispatchedAt(d.getDispatchedAt())
            .items(lines)
            .totalQty(totalQty)
            .build();
    }

    @Override
    public byte[] render(OutboundDispatchPdfData data, InstructionDocumentRenderContext context) {
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
}
