package com.beyond.wbs.statistic.service;

import com.beyond.wbs.common.client.MasterServiceClient;
import com.beyond.wbs.common.client.dto.StoreResDto;
import com.beyond.wbs.common.client.dto.SupplierResDto;
import com.beyond.wbs.common.client.dto.WarehouseResDto;
import com.beyond.wbs.inbounds.domain.InboundOrderStatus;
import com.beyond.wbs.inbounds.domain.InboundOrders;
import com.beyond.wbs.inbounds.repository.InboundOrderRepository;
import com.beyond.wbs.outbounds.domain.OutboundOrderStatus;
import com.beyond.wbs.outbounds.domain.OutboundOrders;
import com.beyond.wbs.outbounds.repository.OutboundOrderItemRepository;
import com.beyond.wbs.outbounds.repository.OutboundOrderRepository;
import com.beyond.wbs.statistic.dto.PendingOrderItemDto;
import com.beyond.wbs.statistic.dto.PendingOrderResponseDto;
import com.beyond.wbs.transfer.domain.TransferOrder;
import com.beyond.wbs.transfer.domain.TransferOrderStatus;
import com.beyond.wbs.transfer.repository.TransferOrderItemRepository;
import com.beyond.wbs.transfer.repository.TransferOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * "처리 필요 지시서" 패널 / "지시서 통합" 페이지용 통합 조립 서비스.
 *
 * 입고/출고/이동 의 미완료 지시서를 한 번에 모아 카테고리(DELAYED/TODAY/UPCOMING)
 * 분류 후 정렬해 반환한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PendingOrderService {

    private final InboundOrderRepository inboundOrderRepository;
    private final OutboundOrderRepository outboundOrderRepository;
    private final OutboundOrderItemRepository outboundOrderItemRepository;
    private final TransferOrderRepository transferOrderRepository;
    private final TransferOrderItemRepository transferOrderItemRepository;
    private final MasterServiceClient masterServiceClient;

    private static final List<InboundOrderStatus> INBOUND_OPEN = List.of(
            InboundOrderStatus.draft,
            InboundOrderStatus.approved,
            InboundOrderStatus.received,
            InboundOrderStatus.placing
    );
    private static final List<OutboundOrderStatus> OUTBOUND_OPEN = List.of(
            OutboundOrderStatus.draft,
            OutboundOrderStatus.approved,
            OutboundOrderStatus.in_progress
    );
    private static final List<TransferOrderStatus> TRANSFER_OPEN = List.of(
            TransferOrderStatus.draft,
            TransferOrderStatus.approved,
            TransferOrderStatus.in_progress
    );

    private static final List<InboundOrderStatus> INBOUND_ALL = List.of(InboundOrderStatus.values());
    private static final List<OutboundOrderStatus> OUTBOUND_ALL = List.of(OutboundOrderStatus.values());
    private static final List<TransferOrderStatus> TRANSFER_ALL = List.of(TransferOrderStatus.values());

    /**
     * 처리 필요 지시서 패널용 — limit 만큼 미리보기 + 전체 카테고리 카운트.
     * 노출 카테고리: DELAYED, TODAY, IN_PROGRESS, PENDING_APPROVAL
     * 미래 마감(UPCOMING)은 "처리 필요" 가 아니므로 응답에서 제외.
     * 정렬: DELAYED → TODAY → IN_PROGRESS → PENDING_APPROVAL (그 안에서 scheduledDate 빠른 순)
     */
    public PendingOrderResponseDto getPendingOrders(UUID clientId, int limit) {
        List<PendingOrderItemDto> all = collectAll(clientId, INBOUND_OPEN, OUTBOUND_OPEN, TRANSFER_OPEN);

        // UPCOMING 제외 — 처리 필요 지시서는 지연/오늘/진행중/승인대기만
        all = new ArrayList<>(all.stream()
                .filter(d -> !"UPCOMING".equals(d.getCategory()))
                .toList());

        long delayed = all.stream().filter(d -> "DELAYED".equals(d.getCategory())).count();
        long today = all.stream().filter(d -> "TODAY".equals(d.getCategory())).count();
        long inProgress = all.stream().filter(d -> "IN_PROGRESS".equals(d.getCategory())).count();
        long pendingApproval = all.stream().filter(d -> "PENDING_APPROVAL".equals(d.getCategory())).count();

        all.sort(defaultComparator());
        List<PendingOrderItemDto> top = all.size() > limit ? all.subList(0, limit) : all;

        return PendingOrderResponseDto.builder()
                .items(top)
                .summary(PendingOrderResponseDto.Summary.builder()
                        .total(all.size())
                        .delayed(delayed)
                        .today(today)
                        .upcoming(0L)
                        .inProgress(inProgress)
                        .pendingApproval(pendingApproval)
                        .build())
                .build();
    }

    /**
     * 통합 페이지용 — 필터/정렬 후 전체 반환 (페이징은 컨트롤러에서 적용).
     */
    public List<PendingOrderItemDto> getIntegratedOrders(UUID clientId,
                                                         String type,
                                                         String category,
                                                         String status) {
        // 통합 페이지는 모든 상태(완료/취소 포함)를 노출.
        List<PendingOrderItemDto> all = collectAll(clientId, INBOUND_ALL, OUTBOUND_ALL, TRANSFER_ALL);

        if (type != null && !"ALL".equalsIgnoreCase(type)) {
            all = all.stream().filter(d -> type.equalsIgnoreCase(d.getType())).toList();
        }
        if (category != null && !"ALL".equalsIgnoreCase(category)) {
            // 가상 카테고리 — 미처리(PENDING) = 지연/오늘마감/진행중/승인대기 합집합 (완료/취소 제외)
            if ("PENDING".equalsIgnoreCase(category)) {
                Set<String> pending = Set.of("DELAYED", "TODAY", "IN_PROGRESS", "PENDING_APPROVAL");
                all = all.stream().filter(d -> pending.contains(d.getCategory())).toList();
            } else {
                all = all.stream().filter(d -> category.equalsIgnoreCase(d.getCategory())).toList();
            }
        }
        if (status != null && !"ALL".equalsIgnoreCase(status)) {
            all = all.stream().filter(d -> status.equalsIgnoreCase(d.getStatus())).toList();
        }
        return new ArrayList<>(all.stream().sorted(defaultComparator()).toList());
    }

    private static Comparator<PendingOrderItemDto> defaultComparator() {
        return Comparator
                .<PendingOrderItemDto, Integer>comparing(d -> categoryPriority(d.getCategory()))
                .thenComparing(PendingOrderItemDto::getScheduledDate, Comparator.nullsLast(LocalDate::compareTo))
                .thenComparing(PendingOrderItemDto::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo));
    }

    // 지정 상태 셋의 지시서를 한 번에 모아 DTO 변환
    private List<PendingOrderItemDto> collectAll(UUID clientId,
                                                 List<InboundOrderStatus> inboundStatuses,
                                                 List<OutboundOrderStatus> outboundStatuses,
                                                 List<TransferOrderStatus> transferStatuses) {
        LocalDate today = LocalDate.now();
        List<PendingOrderItemDto> result = new ArrayList<>();

        // ── 입고 ──
        List<InboundOrders> inbounds = inboundOrderRepository
                .findByClientIdAndStatusInOrderByExpectedDateAsc(clientId, inboundStatuses);
        Map<UUID, long[]> inboundSum = batchInboundSummary(inbounds);
        Map<UUID, String> supplierNames = batchSupplierNames(inbounds, clientId);
        for (InboundOrders io : inbounds) {
            long[] s = inboundSum.getOrDefault(io.getId(), new long[]{0, 0});
            result.add(PendingOrderItemDto.builder()
                    .type("INBOUND")
                    .orderId(io.getId())
                    .orderNo(io.getOrderNo())
                    .partnerName(supplierNames.getOrDefault(io.getSupplierId(), "-"))
                    .status(io.getStatus().name())
                    .scheduledDate(io.getExpectedDate())
                    .category(categoryOf(io.getStatus().name(), io.getExpectedDate(), today))
                    .delayDays(delayDaysOf(io.getStatus().name(), io.getExpectedDate(), today))
                    .itemCount((int) s[0])
                    .totalQty((int) s[1])
                    .createdAt(io.getCreatedAt())
                    .assignedTo(io.getAssignedTo())
                    .build());
        }

        // ── 출고 ──
        List<OutboundOrders> outbounds = outboundOrderRepository
                .findByClientIdAndStatusInOrderByScheduledDateAsc(clientId, outboundStatuses);
        Map<UUID, long[]> outboundSum = batchOutboundSummary(outbounds);
        Map<UUID, String> storeNames = batchStoreNames(outbounds, clientId);
        for (OutboundOrders oo : outbounds) {
            long[] s = outboundSum.getOrDefault(oo.getId(), new long[]{0, 0});
            result.add(PendingOrderItemDto.builder()
                    .type("OUTBOUND")
                    .orderId(oo.getId())
                    .orderNo(oo.getOrderNo())
                    .partnerName(storeNames.getOrDefault(oo.getStoreId(), "-"))
                    .status(oo.getStatus().name())
                    .scheduledDate(oo.getScheduledDate())
                    .category(categoryOf(oo.getStatus().name(), oo.getScheduledDate(), today))
                    .delayDays(delayDaysOf(oo.getStatus().name(), oo.getScheduledDate(), today))
                    .itemCount((int) s[0])
                    .totalQty((int) s[1])
                    .createdAt(oo.getCreatedAt())
                    .assignedTo(oo.getAssignedTo())
                    .build());
        }

        // ── 이동 ──
        List<TransferOrder> transfers = transferOrderRepository
                .findByClientIdAndStatusInOrderByExpectedDateAsc(clientId, transferStatuses);
        Map<UUID, long[]> transferSum = batchTransferSummary(transfers);
        Map<UUID, String> warehouseNames = batchWarehouseNames(transfers, clientId);
        for (TransferOrder t : transfers) {
            long[] s = transferSum.getOrDefault(t.getId(), new long[]{0, 0});
            result.add(PendingOrderItemDto.builder()
                    .type("TRANSFER")
                    .orderId(t.getId())
                    .orderNo(t.getOrderNo())
                    .partnerName(warehouseNames.getOrDefault(t.getToWarehouseId(), "-"))
                    .status(t.getStatus().name())
                    .scheduledDate(t.getExpectedDate())
                    .category(categoryOf(t.getStatus().name(), t.getExpectedDate(), today))
                    .delayDays(delayDaysOf(t.getStatus().name(), t.getExpectedDate(), today))
                    .itemCount((int) s[0])
                    .totalQty((int) s[1])
                    .createdAt(t.getCreatedAt())
                    .assignedTo(t.getAssignedTo())
                    .build());
        }
        return result;
    }

    /** 승인대기(draft) 노출 임계값 — 마감 N일 이내 draft 만 패널에 노출. 그 외는 UPCOMING 으로 분류되어 응답에서 제외됨. */
    private static final long PENDING_APPROVAL_WITHIN_DAYS = 7;

    // 카테고리 분류
    //  - 종료 상태(completed/partial) → COMPLETED
    //  - 취소 → CANCELLED
    //  - 진행중(in_progress/received/placing) → IN_PROGRESS (상태 우선, 날짜 무관)
    //  - 승인대기(draft) + 마감 7일 이내 → PENDING_APPROVAL
    //  - 승인대기(draft) + 7일 초과 미래 → UPCOMING (응답에서 제외)
    //  - 그 외(approved 등) → 마감일 기준 DELAYED / TODAY / UPCOMING
    private static String categoryOf(String status, LocalDate scheduled, LocalDate today) {
        if (status != null) {
            String s = status.toLowerCase();
            if (s.equals("completed") || s.equals("partial")) return "COMPLETED";
            if (s.equals("cancelled")) return "CANCELLED";
            if (s.equals("in_progress") || s.equals("received") || s.equals("placing")) return "IN_PROGRESS";
            if (s.equals("draft")) {
                // 마감 임박(7일 이내) 또는 이미 지난(지연된) draft 만 PENDING_APPROVAL
                if (scheduled == null) return "PENDING_APPROVAL";
                long daysUntil = ChronoUnit.DAYS.between(today, scheduled);
                if (daysUntil <= PENDING_APPROVAL_WITHIN_DAYS) return "PENDING_APPROVAL";
                return "UPCOMING";
            }
        }
        if (scheduled == null) return "TODAY";
        if (scheduled.isBefore(today)) return "DELAYED";
        if (scheduled.isEqual(today)) return "TODAY";
        return "UPCOMING";
    }

    private static int delayDaysOf(String status, LocalDate scheduled, LocalDate today) {
        if (status != null) {
            String s = status.toLowerCase();
            if (s.equals("completed") || s.equals("partial") || s.equals("cancelled")) return 0;
        }
        if (scheduled == null || !scheduled.isBefore(today)) return 0;
        return (int) ChronoUnit.DAYS.between(scheduled, today);
    }

    private static int categoryPriority(String c) {
        return switch (c) {
            case "DELAYED" -> 0;
            case "TODAY" -> 1;
            case "IN_PROGRESS" -> 2;
            case "PENDING_APPROVAL" -> 3;
            case "UPCOMING" -> 4;
            case "COMPLETED" -> 5;
            case "CANCELLED" -> 6;
            default -> 7;
        };
    }

    // 배치 — items summary (N+1 방지)
    private Map<UUID, long[]> batchInboundSummary(List<InboundOrders> orders) {
        if (orders.isEmpty()) return Map.of();
        List<UUID> ids = orders.stream().map(InboundOrders::getId).toList();
        Map<UUID, long[]> map = new HashMap<>();
        for (Object[] row : inboundOrderRepository.findOrderSummaries(ids)) {
            map.put((UUID) row[0], new long[]{((Number) row[1]).longValue(), ((Number) row[2]).longValue()});
        }
        return map;
    }

    private Map<UUID, long[]> batchOutboundSummary(List<OutboundOrders> orders) {
        if (orders.isEmpty()) return Map.of();
        List<UUID> ids = orders.stream().map(OutboundOrders::getId).toList();
        Map<UUID, long[]> map = new HashMap<>();
        for (Object[] row : outboundOrderItemRepository.findOrderSummaries(ids)) {
            map.put((UUID) row[0], new long[]{((Number) row[1]).longValue(), ((Number) row[2]).longValue()});
        }
        return map;
    }

    private Map<UUID, long[]> batchTransferSummary(List<TransferOrder> orders) {
        if (orders.isEmpty()) return Map.of();
        List<UUID> ids = orders.stream().map(TransferOrder::getId).toList();
        Map<UUID, long[]> map = new HashMap<>();
        for (Object[] row : transferOrderItemRepository.findOrderSummaries(ids)) {
            map.put((UUID) row[0], new long[]{((Number) row[1]).longValue(), ((Number) row[2]).longValue()});
        }
        return map;
    }

    // 배치 — 협력사/거래처/창고 이름 (Feign 실패 시 "-" fallback)
    private Map<UUID, String> batchSupplierNames(List<InboundOrders> orders, UUID clientId) {
        Map<UUID, String> map = new HashMap<>();
        for (InboundOrders io : orders) {
            UUID sid = io.getSupplierId();
            if (sid == null || map.containsKey(sid)) continue;
            try {
                SupplierResDto s = masterServiceClient.getSupplier(sid, clientId.toString());
                map.put(sid, (s != null && s.getName() != null) ? s.getName() : "-");
            } catch (Exception e) {
                map.put(sid, "-");
            }
        }
        return map;
    }

    private Map<UUID, String> batchStoreNames(List<OutboundOrders> orders, UUID clientId) {
        Map<UUID, String> map = new HashMap<>();
        for (OutboundOrders oo : orders) {
            UUID storeId = oo.getStoreId();
            if (storeId == null || map.containsKey(storeId)) continue;
            try {
                StoreResDto s = masterServiceClient.getStore(storeId, clientId.toString());
                map.put(storeId, (s != null && s.getName() != null) ? s.getName() : "-");
            } catch (Exception e) {
                map.put(storeId, "-");
            }
        }
        return map;
    }

    private Map<UUID, String> batchWarehouseNames(List<TransferOrder> orders, UUID clientId) {
        Map<UUID, String> map = new HashMap<>();
        for (TransferOrder t : orders) {
            UUID wid = t.getToWarehouseId();
            if (wid == null || map.containsKey(wid)) continue;
            try {
                WarehouseResDto w = masterServiceClient.getWarehouse(wid, clientId.toString());
                map.put(wid, (w != null && w.getName() != null) ? w.getName() : "-");
            } catch (Exception e) {
                map.put(wid, "-");
            }
        }
        return map;
    }
}
