package com.beyond.wbs.ai.forecast;

import com.beyond.wbs.ai.chat.dto.ChatTurn;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class InventoryForecastService {

    private static final int DEFAULT_HORIZON_DAYS = 14;
    private static final int HISTORY_DAYS = 30;

    private final JdbcTemplate readonlyJdbc;

    public InventoryForecastService(@Qualifier("readonlyJdbcTemplate") JdbcTemplate readonlyJdbc) {
        this.readonlyJdbc = readonlyJdbc;
    }

    public ForecastResponse analyze(String question, List<ChatTurn> history) {
        LocalDate today = LocalDate.now();
        LocalDate targetDate = parseTargetDate(question, today);
        long horizonDays = Math.max(1, ChronoUnit.DAYS.between(today, targetDate));

        Map<String, ForecastAccumulator> rows = new LinkedHashMap<>();
        loadCurrentStock(rows);
        loadPlannedInbound(rows, targetDate);
        loadPlannedOutbound(rows, targetDate);
        loadErpPlannedInbound(rows, targetDate);
        loadErpPlannedOutbound(rows, targetDate);
        loadHistoricalOutbound(rows);

        List<ForecastRow> forecasts = rows.values().stream()
                .map(row -> row.toForecast(horizonDays))
                .filter(row -> matchesQuestion(question, row))
                .sorted(Comparator
                        .comparing(InventoryForecastService::riskRank).reversed()
                        .thenComparing(ForecastRow::projectedStock))
                .limit(10)
                .toList();

        String answer = buildAnswer(today, targetDate, horizonDays, forecasts);
        return new ForecastResponse(question, targetDate.toString(), horizonDays, answer, forecasts);
    }

    private void loadCurrentStock(Map<String, ForecastAccumulator> rows) {
        readonlyJdbc.query("""
                    SELECT HEX(i.product_id) AS product_key,
                           COALESCE(p.sku, '-') AS sku,
                           COALESCE(p.name, '미등록 상품') AS product_name,
                           HEX(i.warehouse_id) AS warehouse_key,
                           COALESCE(w.name, '미등록 창고') AS warehouse_name,
                           COALESCE(SUM(i.available_qty), 0) AS current_available,
                           COALESCE(SUM(i.reserved_qty), 0) AS current_reserved,
                           COALESCE(SUM(i.incoming_qty), 0) AS current_incoming,
                           COALESCE(SUM(i.total_qty), 0) AS current_total
                    FROM inventories i
                    LEFT JOIN master_db.products p ON p.id = i.product_id
                    LEFT JOIN master_db.warehouses w ON w.id = i.warehouse_id
                    GROUP BY i.product_id, p.sku, p.name, i.warehouse_id, w.name
                    """, rs -> {
            ForecastAccumulator row = row(rows, rs.getString("product_key"), rs.getString("sku"),
                    rs.getString("product_name"), rs.getString("warehouse_key"), rs.getString("warehouse_name"));
            row.currentAvailable = rs.getLong("current_available");
            row.currentReserved = rs.getLong("current_reserved");
            row.currentIncoming = rs.getLong("current_incoming");
            row.currentTotal = rs.getLong("current_total");
        });
    }

    private void loadPlannedInbound(Map<String, ForecastAccumulator> rows, LocalDate targetDate) {
        queryOptional("""
                    SELECT HEX(oi.product_id) AS product_key,
                           COALESCE(p.sku, '-') AS sku,
                           COALESCE(p.name, '미등록 상품') AS product_name,
                           HEX(o.warehouse_id) AS warehouse_key,
                           COALESCE(w.name, '미등록 창고') AS warehouse_name,
                           COALESCE(SUM(GREATEST(oi.ordered_qty - COALESCE(oi.received_qty, 0) - COALESCE(oi.defect_qty, 0), 0)), 0) AS planned_inbound
                    FROM inbound_order_items oi
                    JOIN inbound_orders o ON o.id = oi.inbound_order_id
                    LEFT JOIN master_db.products p ON p.id = oi.product_id
                    LEFT JOIN master_db.warehouses w ON w.id = o.warehouse_id
                    WHERE o.expected_date BETWEEN CURDATE() AND ?
                      AND o.status IN ('draft', 'approved', 'placing', 'received')
                    GROUP BY oi.product_id, p.sku, p.name, o.warehouse_id, w.name
                    """, targetDate, rs -> {
            ForecastAccumulator row = row(rows, rs.getString("product_key"), rs.getString("sku"),
                    rs.getString("product_name"), rs.getString("warehouse_key"), rs.getString("warehouse_name"));
            row.plannedInbound = rs.getLong("planned_inbound");
        });
    }

    private void loadPlannedOutbound(Map<String, ForecastAccumulator> rows, LocalDate targetDate) {
        queryOptional("""
                    SELECT HEX(oi.product_id) AS product_key,
                           COALESCE(p.sku, '-') AS sku,
                           COALESCE(p.name, '미등록 상품') AS product_name,
                           HEX(o.warehouse_id) AS warehouse_key,
                           COALESCE(w.name, '미등록 창고') AS warehouse_name,
                           COALESCE(SUM(GREATEST(oi.ordered_qty - COALESCE(oi.dispatched_qty, 0), 0)), 0) AS planned_outbound
                    FROM outbound_order_items oi
                    JOIN outbound_orders o ON o.id = oi.outbound_orders_id
                    LEFT JOIN master_db.products p ON p.id = oi.product_id
                    LEFT JOIN master_db.warehouses w ON w.id = o.warehouse_id
                    WHERE o.scheduled_date BETWEEN CURDATE() AND ?
                      AND o.status IN ('draft', 'approved', 'in_progress', 'partial')
                    GROUP BY oi.product_id, p.sku, p.name, o.warehouse_id, w.name
                    """, targetDate, rs -> {
            ForecastAccumulator row = row(rows, rs.getString("product_key"), rs.getString("sku"),
                    rs.getString("product_name"), rs.getString("warehouse_key"), rs.getString("warehouse_name"));
            row.plannedOutbound = rs.getLong("planned_outbound");
        });
    }

    private void loadHistoricalOutbound(Map<String, ForecastAccumulator> rows) {
        queryOptional("""
                    SELECT HEX(t.product_id) AS product_key,
                           COALESCE(p.sku, '-') AS sku,
                           COALESCE(p.name, '미등록 상품') AS product_name,
                           HEX(t.warehouse_id) AS warehouse_key,
                           COALESCE(w.name, '미등록 창고') AS warehouse_name,
                           COALESCE(SUM(ABS(t.qty)), 0) / ? AS avg_daily_outbound
                    FROM inventory_transactions t
                    LEFT JOIN master_db.products p ON p.id = t.product_id
                    LEFT JOIN master_db.warehouses w ON w.id = t.warehouse_id
                    WHERE t.tx_type = 'outbound'
                      AND t.created_at >= DATE_SUB(CURDATE(), INTERVAL ? DAY)
                    GROUP BY t.product_id, p.sku, p.name, t.warehouse_id, w.name
                    """, HISTORY_DAYS, HISTORY_DAYS, rs -> {
            ForecastAccumulator row = row(rows, rs.getString("product_key"), rs.getString("sku"),
                    rs.getString("product_name"), rs.getString("warehouse_key"), rs.getString("warehouse_name"));
            row.avgDailyOutbound = rs.getDouble("avg_daily_outbound");
        });
    }

    private void loadErpPlannedInbound(Map<String, ForecastAccumulator> rows, LocalDate targetDate) {
        queryOptional("""
                    SELECT HEX(pi.product_id) AS product_key,
                           COALESCE(p.sku, '-') AS sku,
                           COALESCE(p.name, '미등록 상품') AS product_name,
                           COALESCE(SUM(pi.qty), 0) AS planned_inbound
                    FROM erp_purchase_order_items pi
                    JOIN erp_purchase_orders po ON po.id = pi.purchase_order_id
                    LEFT JOIN master_db.products p ON p.id = pi.product_id
                    WHERE po.scheduled_date BETWEEN CURDATE() AND ?
                      AND po.status = 'approved'
                    GROUP BY pi.product_id, p.sku, p.name
                    """, targetDate, rs -> {
            ForecastAccumulator row = row(rows, rs.getString("product_key"), rs.getString("sku"),
                    rs.getString("product_name"), "ERP", "ERP 예정");
            row.plannedInbound += rs.getLong("planned_inbound");
        });
    }

    private void loadErpPlannedOutbound(Map<String, ForecastAccumulator> rows, LocalDate targetDate) {
        queryOptional("""
                    SELECT HEX(si.product_id) AS product_key,
                           COALESCE(p.sku, '-') AS sku,
                           COALESCE(p.name, '미등록 상품') AS product_name,
                           COALESCE(SUM(si.qty), 0) AS planned_outbound
                    FROM erp_sales_order_items si
                    JOIN erp_sales_orders so ON so.id = si.sales_order_id
                    LEFT JOIN master_db.products p ON p.id = si.product_id
                    WHERE so.scheduled_date BETWEEN CURDATE() AND ?
                      AND so.status IN ('draft', 'approved')
                    GROUP BY si.product_id, p.sku, p.name
                    """, targetDate, rs -> {
            ForecastAccumulator row = row(rows, rs.getString("product_key"), rs.getString("sku"),
                    rs.getString("product_name"), "ERP", "ERP 예정");
            row.plannedOutbound += rs.getLong("planned_outbound");
        });
    }

    private void queryOptional(String sql, Object param, SqlConsumer consumer) {
        queryOptional(sql, new Object[]{param}, consumer);
    }

    private void queryOptional(String sql, Object param1, Object param2, SqlConsumer consumer) {
        queryOptional(sql, new Object[]{param1, param2}, consumer);
    }

    private void queryOptional(String sql, Object[] params, SqlConsumer consumer) {
        try {
            readonlyJdbc.query(sql, params, (RowCallbackHandler) rs -> consumer.accept(rs));
        } catch (DataAccessException e) {
            log.warn("forecast query skipped: {}", e.getMessage());
        }
    }

    private ForecastAccumulator row(
            Map<String, ForecastAccumulator> rows,
            String productKey,
            String sku,
            String productName,
            String warehouseKey,
            String warehouseName) {
        String key = productKey + ":" + warehouseKey;
        return rows.computeIfAbsent(key, ignored -> new ForecastAccumulator(
                productKey, sku, productName, warehouseKey, warehouseName));
    }

    private static LocalDate parseTargetDate(String question, LocalDate today) {
        String text = question == null ? "" : question;
        Matcher iso = Pattern.compile("(20\\d{2})[-./](\\d{1,2})[-./](\\d{1,2})").matcher(text);
        if (iso.find()) {
            return LocalDate.of(
                    Integer.parseInt(iso.group(1)),
                    Integer.parseInt(iso.group(2)),
                    Integer.parseInt(iso.group(3)));
        }
        Matcher days = Pattern.compile("(\\d{1,3})\\s*일").matcher(text);
        if (days.find()) {
            return today.plusDays(Integer.parseInt(days.group(1)));
        }
        if (text.contains("다음 주") || text.contains("일주일")) {
            return today.plusDays(7);
        }
        if (text.contains("다음 달") || text.contains("한 달")) {
            return today.plusDays(30);
        }
        return today.plusDays(DEFAULT_HORIZON_DAYS);
    }

    private static boolean matchesQuestion(String question, ForecastRow row) {
        String normalized = normalize(question);
        if (normalized.isBlank() || normalized.contains("전체") || normalized.contains("부족") || normalized.contains("위험")) {
            return true;
        }
        return normalized.contains(normalize(row.sku()))
                || normalized.contains(normalize(row.productName()))
                || normalized.contains(normalize(row.warehouseName()));
    }

    private static String buildAnswer(LocalDate today, LocalDate targetDate, long horizonDays, List<ForecastRow> rows) {
        if (rows.isEmpty()) {
            return "%s 기준으로 예측할 재고 데이터가 없습니다. 상품명이나 창고명을 조금 더 구체적으로 입력해 주세요."
                    .formatted(targetDate);
        }

        long shortageCount = rows.stream()
                .filter(row -> row.riskLevel().equals("부족 예상") || row.riskLevel().equals("부족 위험"))
                .count();
        String top = rows.stream()
                .limit(3)
                .map(row -> "%s(%s): 예상 %.0f개, %s"
                        .formatted(row.productName(), row.warehouseName(), row.projectedStock(), row.riskLevel()))
                .collect(Collectors.joining(" / "));

        return """
                %s부터 %s까지 %d일 기준으로 재고를 예측했습니다.
                계산식은 현재 가용재고 + 예정 입고 - 예정 출고 - 최근 %d일 평균 출고 추세입니다.
                부족 위험 항목은 %d건이며, 우선 확인 대상은 %s 입니다.
                """.formatted(today, targetDate, horizonDays, HISTORY_DAYS, shortageCount, top).trim();
    }

    private static int riskRank(ForecastRow row) {
        return switch (row.riskLevel()) {
            case "부족 예상" -> 4;
            case "부족 위험" -> 3;
            case "주의" -> 2;
            default -> 1;
        };
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    @FunctionalInterface
    private interface SqlConsumer {
        void accept(ResultSet rs) throws SQLException;
    }

    private static class ForecastAccumulator {
        private final String productKey;
        private final String sku;
        private final String productName;
        private final String warehouseKey;
        private final String warehouseName;
        private long currentAvailable;
        private long currentReserved;
        private long currentIncoming;
        private long currentTotal;
        private long plannedInbound;
        private long plannedOutbound;
        private double avgDailyOutbound;

        private ForecastAccumulator(String productKey, String sku, String productName, String warehouseKey, String warehouseName) {
            this.productKey = productKey;
            this.sku = sku;
            this.productName = productName;
            this.warehouseKey = warehouseKey;
            this.warehouseName = warehouseName;
        }

        private ForecastRow toForecast(long horizonDays) {
            double trendDemand = round(avgDailyOutbound * horizonDays);
            double expectedStock = currentAvailable + plannedInbound - plannedOutbound;
            double projectedStock = round(expectedStock - trendDemand);
            double safetyStock = Math.max(5, avgDailyOutbound * 3);
            String riskLevel;
            if (projectedStock < 0) {
                riskLevel = "부족 예상";
            } else if (expectedStock < 0) {
                riskLevel = "부족 위험";
            } else if (projectedStock <= safetyStock) {
                riskLevel = "주의";
            } else {
                riskLevel = "안정";
            }
            String recommendation = switch (riskLevel) {
                case "부족 예상" -> "예정 입고 앞당김 또는 추가 입고 지시 검토";
                case "부족 위험" -> "출고 승인 전 재고 확보 여부 확인";
                case "주의" -> "안전재고 근접. 출고 추세 모니터링";
                default -> "현재 계획 기준 안정";
            };
            return new ForecastRow(
                    productKey,
                    sku,
                    productName,
                    warehouseKey,
                    warehouseName,
                    currentAvailable,
                    currentReserved,
                    currentIncoming,
                    plannedInbound,
                    plannedOutbound,
                    round(avgDailyOutbound),
                    trendDemand,
                    round(expectedStock),
                    projectedStock,
                    riskLevel,
                    recommendation);
        }
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    public record ForecastResponse(
            String question,
            String targetDate,
            long horizonDays,
            String answer,
            List<ForecastRow> rows
    ) {
    }

    public record ForecastRow(
            String productKey,
            String sku,
            String productName,
            String warehouseKey,
            String warehouseName,
            long currentAvailable,
            long currentReserved,
            long currentIncoming,
            long plannedInbound,
            long plannedOutbound,
            double avgDailyOutbound,
            double trendDemand,
            double expectedStock,
            double projectedStock,
            String riskLevel,
            String recommendation
    ) {
    }
}
