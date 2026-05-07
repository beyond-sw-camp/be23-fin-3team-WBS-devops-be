package com.beyond.wbs.aiquery.service;

import com.beyond.wbs.aiquery.controller.AiWorkQueryController.WorkQueryApiRequest;
import com.beyond.wbs.aiquery.controller.AiWorkQueryController.WorkQueryApiResponse;
import com.beyond.wbs.common.client.AccountServiceClient;
import com.beyond.wbs.common.client.dto.AccountUserListResDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiWorkQueryService {

    private static final int DEFAULT_LIMIT = 8;
    private static final Pattern KOREAN_MONTH_DAY = Pattern.compile("(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일");

    private final JdbcTemplate jdbcTemplate;
    private final AccountServiceClient accountServiceClient;

    public WorkQueryApiResponse execute(String clientId, String userId, WorkQueryApiRequest request) {
        long startedAt = System.nanoTime();
        Intent intent = parseIntent(request == null ? null : request.intent());
        Map<String, String> slots = request == null || request.slots() == null ? Map.of() : request.slots();
        int limit = normalizeLimit(request == null ? 0 : request.limit());

        List<Map<String, Object>> rows = switch (intent) {
            case MY_PICKING_TASKS -> queryMyPickingTasks(clientId, userId, limit);
            case INVENTORY_LOCATION -> queryInventoryLocation(clientId, slot(slots, "product"), slot(slots, "warehouse"), limit);
            case LOW_STOCK -> queryLowStock(clientId, limit);
            case INBOUND_STATUS -> queryInboundStatus(clientId, limit);
            case OUTBOUND_STATUS -> queryOutboundStatus(clientId, limit);
            case PENDING_WORK -> queryPendingWork(clientId, slot(slots, "date"), limit);
        };

        rows = enrichAssignedUsers(userId, rows);

        log.info("[AI_WORK_QUERY_API] intent={}, rows={}, elapsedMs={}, clientId={}, userId={}, slots={}",
                intent, rows.size(), elapsedMs(startedAt), mask(clientId), mask(userId), slots);
        return new WorkQueryApiResponse(intent.name(), rows);
    }

    private List<Map<String, Object>> queryMyPickingTasks(String clientId, String userId, int limit) {
        if (isBlank(userId)) {
            return List.of(Map.of("message", "로그인 사용자 정보를 확인할 수 없어 담당자 기준 조회를 할 수 없습니다."));
        }
        return jdbcTemplate.queryForList("""
                SELECT pl.picking_no,
                       w.name AS warehouse_name,
                       pl.status,
                       pl.created_at,
                       LOWER(CONCAT(SUBSTR(HEX(pl.assigned_to), 1, 8), '-', SUBSTR(HEX(pl.assigned_to), 9, 4), '-', SUBSTR(HEX(pl.assigned_to), 13, 4), '-', SUBSTR(HEX(pl.assigned_to), 17, 4), '-', SUBSTR(HEX(pl.assigned_to), 21, 12))) AS assigned_to,
                       COUNT(pli.id) AS item_count,
                       COALESCE(SUM(pli.qty), 0) AS required_qty,
                       COALESCE(SUM(pli.picked_qty), 0) AS picked_qty
                FROM picking_lists pl
                JOIN picking_list_items pli ON pli.picking_list_id = pl.id
                JOIN master_db.warehouses w ON w.id = pl.warehouse_id
                WHERE pl.assigned_to = UNHEX(REPLACE(?, '-', ''))
                  AND (? IS NULL OR pl.client_id = UNHEX(REPLACE(?, '-', '')))
                  AND pl.status IN ('pending', 'in_progress')
                GROUP BY pl.id, pl.picking_no, w.name, pl.status, pl.created_at, pl.assigned_to
                ORDER BY pl.created_at ASC
                LIMIT ?
                """, userId, blankToNull(clientId), blankToNull(clientId), limit);
    }

    private List<Map<String, Object>> queryInventoryLocation(String clientId, String product, String warehouse, int limit) {
        String like = "%" + (isBlank(product) ? "" : product) + "%";
        String warehouseLike = "%" + (isBlank(warehouse) ? "" : warehouse) + "%";
        return jdbcTemplate.queryForList("""
                SELECT p.name AS product_name,
                       p.sku,
                       w.name AS warehouse_name,
                       z.code AS zone_code,
                       z.name AS zone_name,
                       r.code AS rack_code,
                       l.code AS location_code,
                       i.available_qty,
                       i.reserved_qty,
                       i.incoming_qty,
                       i.total_qty
                FROM inventories i
                JOIN master_db.products p ON p.id = i.product_id
                JOIN master_db.warehouses w ON w.id = i.warehouse_id
                LEFT JOIN master_db.locations l ON l.id = i.location_id
                LEFT JOIN master_db.racks r ON r.id = l.rack_id
                LEFT JOIN master_db.zones z ON z.id = r.zone_id
                WHERE (? IS NULL OR i.client_id = UNHEX(REPLACE(?, '-', '')))
                  AND (p.name LIKE ? OR p.sku LIKE ?)
                  AND (? = '%%' OR w.name LIKE ?)
                  AND i.total_qty > 0
                ORDER BY p.name ASC, w.name ASC, z.code ASC, r.code ASC, l.code ASC
                LIMIT ?
                """, blankToNull(clientId), blankToNull(clientId), like, like, warehouseLike, warehouseLike, limit);
    }

    private List<Map<String, Object>> queryLowStock(String clientId, int limit) {
        return jdbcTemplate.queryForList("""
                SELECT p.name AS product_name,
                       p.sku,
                       w.name AS warehouse_name,
                       SUM(i.available_qty) AS available_qty,
                       SUM(i.reserved_qty) AS reserved_qty,
                       SUM(i.incoming_qty) AS incoming_qty,
                       SUM(i.total_qty) AS total_qty
                FROM inventories i
                JOIN master_db.products p ON p.id = i.product_id
                JOIN master_db.warehouses w ON w.id = i.warehouse_id
                WHERE (? IS NULL OR i.client_id = UNHEX(REPLACE(?, '-', '')))
                GROUP BY p.id, p.name, p.sku, w.name
                HAVING SUM(i.available_qty) <= 5
                ORDER BY available_qty ASC, total_qty ASC
                LIMIT ?
                """, blankToNull(clientId), blankToNull(clientId), limit);
    }

    private List<Map<String, Object>> queryInboundStatus(String clientId, int limit) {
        return jdbcTemplate.queryForList("""
                SELECT io.order_no,
                       w.name AS warehouse_name,
                       io.status,
                       io.expected_date,
                       COUNT(ioi.id) AS item_count,
                       COALESCE(SUM(ioi.ordered_qty), 0) AS ordered_qty,
                       COALESCE(SUM(ioi.received_qty), 0) AS received_qty
                FROM inbound_orders io
                LEFT JOIN inbound_order_items ioi ON ioi.inbound_order_id = io.id
                JOIN master_db.warehouses w ON w.id = io.warehouse_id
                WHERE (? IS NULL OR io.client_id = UNHEX(REPLACE(?, '-', '')))
                  AND io.status IN ('draft', 'approved', 'received', 'placing')
                GROUP BY io.id, io.order_no, w.name, io.status, io.expected_date
                ORDER BY io.expected_date ASC, io.created_at ASC
                LIMIT ?
                """, blankToNull(clientId), blankToNull(clientId), limit);
    }

    private List<Map<String, Object>> queryOutboundStatus(String clientId, int limit) {
        return jdbcTemplate.queryForList("""
                SELECT oo.order_no,
                       w.name AS warehouse_name,
                       oo.status,
                       oo.scheduled_date,
                       COUNT(ooi.id) AS item_count,
                       COALESCE(SUM(ooi.ordered_qty), 0) AS ordered_qty,
                       COALESCE(SUM(ooi.picked_qty), 0) AS picked_qty,
                       COALESCE(SUM(ooi.dispatched_qty), 0) AS dispatched_qty
                FROM outbound_orders oo
                LEFT JOIN outbound_order_items ooi ON ooi.outbound_orders_id = oo.id
                JOIN master_db.warehouses w ON w.id = oo.warehouse_id
                WHERE (? IS NULL OR oo.client_id = UNHEX(REPLACE(?, '-', '')))
                  AND oo.status IN ('draft', 'approved', 'in_progress', 'partial')
                GROUP BY oo.id, oo.order_no, w.name, oo.status, oo.scheduled_date
                ORDER BY oo.scheduled_date ASC, oo.created_at ASC
                LIMIT ?
                """, blankToNull(clientId), blankToNull(clientId), limit);
    }

    private List<Map<String, Object>> queryPendingWork(String clientId, String date, int limit) {
        LocalDate targetDate = normalizeDate(date);
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.addAll(jdbcTemplate.queryForList("""
                SELECT '입고 검수' AS work_type,
                       io.order_no AS document_no,
                       w.name AS warehouse_name,
                       io.status,
                       io.expected_date AS scheduled_date,
                       LOWER(CONCAT(SUBSTR(HEX(io.assigned_to), 1, 8), '-', SUBSTR(HEX(io.assigned_to), 9, 4), '-', SUBSTR(HEX(io.assigned_to), 13, 4), '-', SUBSTR(HEX(io.assigned_to), 17, 4), '-', SUBSTR(HEX(io.assigned_to), 21, 12))) AS assigned_to
                FROM inbound_orders io
                JOIN master_db.warehouses w ON w.id = io.warehouse_id
                WHERE (? IS NULL OR io.client_id = UNHEX(REPLACE(?, '-', '')))
                  AND io.status IN ('approved')
                  AND (? IS NULL OR io.expected_date = ?)
                ORDER BY io.expected_date ASC
                LIMIT ?
                """, blankToNull(clientId), blankToNull(clientId), targetDate, targetDate, limit));
        rows.addAll(jdbcTemplate.queryForList("""
                SELECT '적치' AS work_type,
                       po.placement_no AS document_no,
                       w.name AS warehouse_name,
                       po.status,
                       DATE(po.created_at) AS scheduled_date,
                       LOWER(CONCAT(SUBSTR(HEX(po.assigned_to), 1, 8), '-', SUBSTR(HEX(po.assigned_to), 9, 4), '-', SUBSTR(HEX(po.assigned_to), 13, 4), '-', SUBSTR(HEX(po.assigned_to), 17, 4), '-', SUBSTR(HEX(po.assigned_to), 21, 12))) AS assigned_to
                FROM placement_orders po
                JOIN master_db.warehouses w ON w.id = po.warehouse_id
                WHERE (? IS NULL OR po.client_id = UNHEX(REPLACE(?, '-', '')))
                  AND po.status IN ('pending', 'in_progress')
                  AND (? IS NULL OR DATE(po.created_at) = ?)
                ORDER BY po.created_at ASC
                LIMIT ?
                """, blankToNull(clientId), blankToNull(clientId), targetDate, targetDate, limit));
        rows.addAll(jdbcTemplate.queryForList("""
                SELECT '피킹' AS work_type,
                       pl.picking_no AS document_no,
                       w.name AS warehouse_name,
                       pl.status,
                       DATE(pl.created_at) AS scheduled_date,
                       LOWER(CONCAT(SUBSTR(HEX(pl.assigned_to), 1, 8), '-', SUBSTR(HEX(pl.assigned_to), 9, 4), '-', SUBSTR(HEX(pl.assigned_to), 13, 4), '-', SUBSTR(HEX(pl.assigned_to), 17, 4), '-', SUBSTR(HEX(pl.assigned_to), 21, 12))) AS assigned_to
                FROM picking_lists pl
                JOIN master_db.warehouses w ON w.id = pl.warehouse_id
                WHERE (? IS NULL OR pl.client_id = UNHEX(REPLACE(?, '-', '')))
                  AND pl.status IN ('pending', 'in_progress')
                  AND (? IS NULL OR DATE(pl.created_at) = ?)
                ORDER BY pl.created_at ASC
                LIMIT ?
                """, blankToNull(clientId), blankToNull(clientId), targetDate, targetDate, limit));
        rows.addAll(jdbcTemplate.queryForList("""
                SELECT '출고 완료' AS work_type,
                       oo.order_no AS document_no,
                       w.name AS warehouse_name,
                       oo.status,
                       oo.scheduled_date,
                       LOWER(CONCAT(SUBSTR(HEX(oo.assigned_to), 1, 8), '-', SUBSTR(HEX(oo.assigned_to), 9, 4), '-', SUBSTR(HEX(oo.assigned_to), 13, 4), '-', SUBSTR(HEX(oo.assigned_to), 17, 4), '-', SUBSTR(HEX(oo.assigned_to), 21, 12))) AS assigned_to
                FROM outbound_orders oo
                JOIN master_db.warehouses w ON w.id = oo.warehouse_id
                WHERE (? IS NULL OR oo.client_id = UNHEX(REPLACE(?, '-', '')))
                  AND oo.status IN ('approved', 'in_progress', 'partial')
                  AND (? IS NULL OR oo.scheduled_date = ?)
                ORDER BY oo.scheduled_date ASC
                LIMIT ?
                """, blankToNull(clientId), blankToNull(clientId), targetDate, targetDate, limit));
        rows.addAll(jdbcTemplate.queryForList("""
                SELECT '재고 실사' AS work_type,
                       sco.order_no AS document_no,
                       w.name AS warehouse_name,
                       sco.status,
                       DATE(sco.created_at) AS scheduled_date,
                       LOWER(CONCAT(SUBSTR(HEX(sco.assigned_to), 1, 8), '-', SUBSTR(HEX(sco.assigned_to), 9, 4), '-', SUBSTR(HEX(sco.assigned_to), 13, 4), '-', SUBSTR(HEX(sco.assigned_to), 17, 4), '-', SUBSTR(HEX(sco.assigned_to), 21, 12))) AS assigned_to
                FROM stock_count_orders sco
                JOIN master_db.warehouses w ON w.id = sco.warehouse_id
                WHERE (? IS NULL OR sco.client_id = UNHEX(REPLACE(?, '-', '')))
                  AND sco.status IN ('in_progress')
                  AND (? IS NULL OR DATE(sco.created_at) = ?)
                ORDER BY sco.created_at ASC
                LIMIT ?
                """, blankToNull(clientId), blankToNull(clientId), targetDate, targetDate, limit));
        rows.addAll(jdbcTemplate.queryForList("""
                SELECT '이동' AS work_type,
                       tr.order_no AS document_no,
                       CONCAT(fw.name, ' → ', tw.name) AS warehouse_name,
                       tr.status,
                       tr.expected_date AS scheduled_date,
                       LOWER(CONCAT(SUBSTR(HEX(tr.assigned_to), 1, 8), '-', SUBSTR(HEX(tr.assigned_to), 9, 4), '-', SUBSTR(HEX(tr.assigned_to), 13, 4), '-', SUBSTR(HEX(tr.assigned_to), 17, 4), '-', SUBSTR(HEX(tr.assigned_to), 21, 12))) AS assigned_to
                FROM transfer_orders tr
                JOIN master_db.warehouses fw ON fw.id = tr.from_warehouse_id
                JOIN master_db.warehouses tw ON tw.id = tr.to_warehouse_id
                WHERE (? IS NULL OR tr.client_id = UNHEX(REPLACE(?, '-', '')))
                  AND tr.status IN ('approved', 'in_progress', 'partial')
                  AND (? IS NULL OR tr.expected_date = ?)
                ORDER BY tr.expected_date ASC
                LIMIT ?
                """, blankToNull(clientId), blankToNull(clientId), targetDate, targetDate, limit));
        rows.addAll(jdbcTemplate.queryForList("""
                SELECT '기타 입출고' AS work_type,
                       eo.order_no AS document_no,
                       w.name AS warehouse_name,
                       eo.status,
                       DATE(eo.created_at) AS scheduled_date,
                       LOWER(CONCAT(SUBSTR(HEX(eo.assigned_to), 1, 8), '-', SUBSTR(HEX(eo.assigned_to), 9, 4), '-', SUBSTR(HEX(eo.assigned_to), 13, 4), '-', SUBSTR(HEX(eo.assigned_to), 17, 4), '-', SUBSTR(HEX(eo.assigned_to), 21, 12))) AS assigned_to
                FROM etc_inout_orders eo
                JOIN master_db.warehouses w ON w.id = eo.warehouse_id
                WHERE (? IS NULL OR eo.client_id = UNHEX(REPLACE(?, '-', '')))
                  AND eo.status IN ('approved')
                  AND (? IS NULL OR DATE(eo.created_at) = ?)
                ORDER BY eo.created_at ASC
                LIMIT ?
                """, blankToNull(clientId), blankToNull(clientId), targetDate, targetDate, limit));
        return rows.stream()
                .limit(limit)
                .toList();
    }

    private List<Map<String, Object>> enrichAssignedUsers(String requesterId, List<Map<String, Object>> rows) {
        Set<String> assignedIds = new HashSet<>();
        for (Map<String, Object> row : rows) {
            Object value = row.get("assigned_to");
            if (value != null && !value.toString().isBlank()) {
                assignedIds.add(value.toString());
            }
        }
        if (assignedIds.isEmpty()) {
            return rows;
        }

        try {
            String requester = isBlank(requesterId)
                    ? "01935c00-0000-7200-8000-000000000001"
                    : requesterId;
            List<AccountUserListResDto> users = accountServiceClient.getUsers(requester);
            Map<String, AccountUserListResDto> userMap = new HashMap<>();
            for (AccountUserListResDto user : users) {
                if (user.getId() != null) {
                    userMap.put(user.getId().toString(), user);
                }
            }

            for (Map<String, Object> row : rows) {
                Object assignedTo = row.get("assigned_to");
                if (assignedTo == null) {
                    continue;
                }
                AccountUserListResDto user = userMap.get(assignedTo.toString());
                if (user != null) {
                    row.put("assigned_to_name", user.getName());
                    row.put("assigned_to_login_id", user.getLoginId());
                }
            }
        } catch (Exception e) {
            log.warn("[AI_WORK_QUERY_API] assignee enrichment failed: {}", e.getMessage());
        }
        return rows;
    }

    private Intent parseIntent(String value) {
        if (value == null || value.isBlank()) {
            return Intent.PENDING_WORK;
        }
        try {
            return Intent.valueOf(value);
        } catch (IllegalArgumentException e) {
            return Intent.PENDING_WORK;
        }
    }

    private String slot(Map<String, String> slots, String key) {
        String value = slots.get(key);
        return value == null ? "" : value.trim();
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, 20);
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value;
    }

    private LocalDate normalizeDate(String value) {
        if (isBlank(value)) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("today".equals(normalized)) {
            return LocalDate.now();
        }
        Matcher matcher = KOREAN_MONTH_DAY.matcher(normalized);
        if (matcher.find()) {
            int month = Integer.parseInt(matcher.group(1));
            int day = Integer.parseInt(matcher.group(2));
            return LocalDate.of(LocalDate.now().getYear(), month, day);
        }
        try {
            return LocalDate.parse(normalized);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String mask(String value) {
        if (isBlank(value) || value.length() < 8) {
            return value;
        }
        return value.substring(0, 8) + "...";
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private enum Intent {
        MY_PICKING_TASKS,
        PENDING_WORK,
        INBOUND_STATUS,
        OUTBOUND_STATUS,
        INVENTORY_LOCATION,
        LOW_STOCK
    }
}
