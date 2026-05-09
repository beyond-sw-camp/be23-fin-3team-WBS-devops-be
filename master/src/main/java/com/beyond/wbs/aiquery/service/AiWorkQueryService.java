package com.beyond.wbs.aiquery.service;

import com.beyond.wbs.aiquery.controller.AiWorkQueryController.WorkQueryApiRequest;
import com.beyond.wbs.aiquery.controller.AiWorkQueryController.WorkQueryApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiWorkQueryService {

    private static final int DEFAULT_LIMIT = 8;

    private final JdbcTemplate jdbcTemplate;

    public WorkQueryApiResponse execute(String clientId, String userId, WorkQueryApiRequest request) {
        long startedAt = System.nanoTime();
        Intent intent = parseIntent(request == null ? null : request.intent());
        Map<String, String> slots = request == null || request.slots() == null ? Map.of() : request.slots();
        int limit = normalizeLimit(request == null ? 0 : request.limit());

        List<Map<String, Object>> rows = switch (intent) {
            case PRODUCT_INFO -> queryProducts(clientId, keyword(slots), limit);
            case WAREHOUSE_INFO -> queryWarehouses(clientId, keyword(slots), limit);
            case LOCATION_INFO -> queryLocations(clientId, keyword(slots), limit);
            case SUPPLIER_INFO -> querySuppliers(clientId, keyword(slots), limit);
            case STORE_INFO -> queryStores(clientId, keyword(slots), limit);
        };

        log.info("[AI_MASTER_WORK_QUERY_API] intent={}, rows={}, elapsedMs={}, clientId={}, userId={}, slots={}",
                intent, rows.size(), elapsedMs(startedAt), mask(clientId), mask(userId), slots);
        return new WorkQueryApiResponse(intent.name(), rows);
    }

    private List<Map<String, Object>> queryProducts(String clientId, String keyword, int limit) {
        String like = like(keyword);
        return jdbcTemplate.queryForList("""
                SELECT p.name AS product_name,
                       p.sku,
                       p.barcode,
                       p.is_active,
                       p.owner_type,
                       p.unit,
                       p.standard_price,
                       pg.name AS product_group_name,
                       pc.name AS category_name,
                       s.name AS supplier_name
                FROM products p
                LEFT JOIN product_groups pg ON pg.id = p.product_group_id
                LEFT JOIN product_categories pc ON pc.id = pg.category_id
                LEFT JOIN suppliers s ON s.id = p.supplier_id
                WHERE (? IS NULL OR p.client_id = UNHEX(REPLACE(?, '-', '')))
                  AND (? = '%%' OR p.name LIKE ? OR p.sku LIKE ? OR p.barcode LIKE ? OR pg.name LIKE ? OR pc.name LIKE ?)
                ORDER BY p.name ASC
                LIMIT ?
                """, blankToNull(clientId), blankToNull(clientId), like, like, like, like, like, like, limit);
    }

    private List<Map<String, Object>> queryWarehouses(String clientId, String keyword, int limit) {
        String like = like(keyword);
        return jdbcTemplate.queryForList("""
                SELECT name AS warehouse_name,
                       code AS warehouse_code,
                       warehouse_type,
                       is_active,
                       manager_name,
                       phone,
                       address
                FROM warehouses
                WHERE (? IS NULL OR client_id = UNHEX(REPLACE(?, '-', '')))
                  AND (? = '%%' OR name LIKE ? OR code LIKE ? OR warehouse_type LIKE ? OR manager_name LIKE ?)
                ORDER BY name ASC
                LIMIT ?
                """, blankToNull(clientId), blankToNull(clientId), like, like, like, like, like, limit);
    }

    private List<Map<String, Object>> queryLocations(String clientId, String keyword, int limit) {
        String like = like(keyword);
        return jdbcTemplate.queryForList("""
                SELECT w.name AS warehouse_name,
                       w.code AS warehouse_code,
                       z.name AS zone_name,
                       z.code AS zone_code,
                       z.zone_type,
                       r.name AS rack_name,
                       r.code AS rack_code,
                       l.code AS location_code,
                       l.barcode,
                       l.floor_no,
                       l.max_capacity,
                       l.is_active
                FROM locations l
                JOIN racks r ON r.id = l.rack_id
                JOIN zones z ON z.id = r.zone_id
                JOIN warehouses w ON w.id = z.warehouse_id
                WHERE (? IS NULL OR w.client_id = UNHEX(REPLACE(?, '-', '')))
                  AND (? = '%%'
                       OR w.name LIKE ? OR w.code LIKE ?
                       OR z.name LIKE ? OR z.code LIKE ?
                       OR r.name LIKE ? OR r.code LIKE ?
                       OR l.code LIKE ? OR l.barcode LIKE ?)
                ORDER BY w.name ASC, z.code ASC, r.code ASC, l.floor_no ASC
                LIMIT ?
                """, blankToNull(clientId), blankToNull(clientId), like,
                like, like, like, like, like, like, like, like, limit);
    }

    private List<Map<String, Object>> querySuppliers(String clientId, String keyword, int limit) {
        String like = like(keyword);
        return jdbcTemplate.queryForList("""
                SELECT name AS supplier_name,
                       code AS supplier_code,
                       biz_no,
                       ceo_name,
                       tel,
                       email,
                       esg_grade,
                       eco_certified,
                       is_active
                FROM suppliers
                WHERE (? IS NULL OR client_id = UNHEX(REPLACE(?, '-', '')))
                  AND (? = '%%' OR name LIKE ? OR code LIKE ? OR biz_no LIKE ? OR esg_grade LIKE ?)
                ORDER BY name ASC
                LIMIT ?
                """, blankToNull(clientId), blankToNull(clientId), like, like, like, like, like, limit);
    }

    private List<Map<String, Object>> queryStores(String clientId, String keyword, int limit) {
        String like = like(keyword);
        return jdbcTemplate.queryForList("""
                SELECT name AS store_name,
                       code AS store_code,
                       biz_no,
                       ceo_name,
                       tel,
                       email,
                       address,
                       auto_wave_enabled,
                       is_active
                FROM stores
                WHERE (? IS NULL OR client_id = UNHEX(REPLACE(?, '-', '')))
                  AND (? = '%%' OR name LIKE ? OR code LIKE ? OR biz_no LIKE ? OR tel LIKE ? OR email LIKE ?)
                ORDER BY name ASC
                LIMIT ?
                """, blankToNull(clientId), blankToNull(clientId), like, like, like, like, like, like, limit);
    }

    private Intent parseIntent(String value) {
        if (value == null || value.isBlank()) {
            return Intent.PRODUCT_INFO;
        }
        try {
            return Intent.valueOf(value);
        } catch (IllegalArgumentException e) {
            return Intent.PRODUCT_INFO;
        }
    }

    private String keyword(Map<String, String> slots) {
        return firstNonBlank(slots.get("keyword"), slots.get("product"), slots.get("warehouse"));
    }

    private String like(String value) {
        return "%" + (value == null ? "" : value.trim()) + "%";
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, 20);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String mask(String value) {
        if (value == null || value.isBlank() || value.length() < 8) {
            return value;
        }
        return value.substring(0, 8) + "...";
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private enum Intent {
        PRODUCT_INFO,
        WAREHOUSE_INFO,
        LOCATION_INFO,
        SUPPLIER_INFO,
        STORE_INFO
    }
}
