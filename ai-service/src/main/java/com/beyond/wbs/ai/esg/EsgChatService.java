package com.beyond.wbs.ai.esg;

import com.beyond.wbs.ai.chat.dto.ChatTurn;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EsgChatService {

    private static final BigDecimal DEFAULT_FREIGHT_CO2_KG_PER_KM = BigDecimal.valueOf(0.249);

    private final ChatClient chatClient;
    private final JdbcTemplate readonlyJdbc;

    public EsgChatService(
            ChatClient chatClient,
            @Qualifier("readonlyJdbcTemplate") JdbcTemplate readonlyJdbc) {
        this.chatClient = chatClient;
        this.readonlyJdbc = readonlyJdbc;
    }

    public EsgChatResponse analyze(String question, List<ChatTurn> history) {
        List<SupplierEsgSnapshot> snapshots = loadSnapshots();
        List<SupplierEsgSnapshot> selected = selectRelevant(question, snapshots);
        String answer = summarize(question, selected, history);
        return new EsgChatResponse(question, answer, selected);
    }

    private List<SupplierEsgSnapshot> loadSnapshots() {
        Map<String, OperationalMetric> metricBySupplier = loadOperationalMetrics();
        return loadSupplierSnapshots(metricBySupplier);
    }

    private Map<String, OperationalMetric> loadOperationalMetrics() {
        try {
            return readonlyJdbc.query("""
                        SELECT HEX(os.supplier_id) AS supplier_key,
                               COUNT(*) AS total_orders,
                               SUM(CASE
                                     WHEN os.received_at IS NOT NULL
                                      AND DATE(os.received_at) <= os.expected_date THEN 1
                                     ELSE 0
                                   END) AS on_time_orders,
                               SUM(os.received_qty) AS total_received_qty,
                               SUM(os.defect_qty) AS total_defect_qty,
                               MAX(os.received_at) AS last_received_at
                        FROM (
                            SELECT o.id,
                                   o.supplier_id,
                                   o.expected_date,
                                   MIN(r.received_at) AS received_at,
                                   COALESCE(SUM(oi.received_qty), 0) AS received_qty,
                                   COALESCE(SUM(oi.defect_qty), 0) AS defect_qty
                            FROM inbound_orders o
                            LEFT JOIN inbound_receipts r ON r.inbound_order_id = o.id
                            LEFT JOIN inbound_order_items oi ON oi.inbound_order_id = o.id
                            WHERE o.supplier_id IS NOT NULL
                            GROUP BY o.id, o.supplier_id, o.expected_date
                        ) os
                        GROUP BY os.supplier_id
                        """,
                    (rs, rowNum) -> toOperationalMetric(rs))
                    .stream()
                    .collect(Collectors.toMap(OperationalMetric::supplierKey, Function.identity()));
        } catch (DataAccessException e) {
            log.warn("ESG operational metric query failed. continue with empty metrics. reason={}", e.getMessage());
            return Map.of();
        }
    }

    private List<SupplierEsgSnapshot> loadSupplierSnapshots(Map<String, OperationalMetric> metricBySupplier) {
        try {
            return readonlyJdbc.query("""
                        SELECT HEX(s.id) AS supplier_key,
                               s.name,
                               s.code,
                               s.address,
                               s.esg_grade,
                               s.eco_certified,
                               s.esg_memo
                        FROM master_db.suppliers s
                        WHERE s.is_active = true
                        ORDER BY s.name
                        """,
                    (rs, rowNum) -> toSnapshot(
                            rs.getString("supplier_key"),
                            rs.getString("name"),
                            rs.getString("code"),
                            rs.getString("address"),
                            rs.getString("esg_grade"),
                            rs.getBoolean("eco_certified"),
                            rs.getString("esg_memo"),
                            metricBySupplier.get(rs.getString("supplier_key"))));
        } catch (DataAccessException e) {
            log.warn("ESG supplier query with ESG columns failed. fallback to base supplier columns. reason={}", e.getMessage());
            return readonlyJdbc.query("""
                            SELECT HEX(s.id) AS supplier_key,
                                   s.name,
                                   s.code,
                                   s.address
                            FROM master_db.suppliers s
                            WHERE s.is_active = true
                            ORDER BY s.name
                            """,
                    (rs, rowNum) -> toSnapshot(
                            rs.getString("supplier_key"),
                            rs.getString("name"),
                            rs.getString("code"),
                            rs.getString("address"),
                            null,
                            false,
                            null,
                            metricBySupplier.get(rs.getString("supplier_key"))));
        }
    }

    private OperationalMetric toOperationalMetric(ResultSet rs) throws SQLException {
        long totalOrders = rs.getLong("total_orders");
        long onTimeOrders = rs.getLong("on_time_orders");
        long totalReceivedQty = rs.getLong("total_received_qty");
        long totalDefectQty = rs.getLong("total_defect_qty");
        LocalDateTime lastReceivedAt = rs.getTimestamp("last_received_at") == null
                ? null
                : rs.getTimestamp("last_received_at").toLocalDateTime();
        return new OperationalMetric(
                rs.getString("supplier_key"),
                totalOrders,
                onTimeOrders,
                totalReceivedQty,
                totalDefectQty,
                lastReceivedAt
        );
    }

    private SupplierEsgSnapshot toSnapshot(
            String supplierKey,
            String name,
            String code,
            String address,
            String esgGrade,
            boolean ecoCertified,
            String esgMemo,
            OperationalMetric metric) {
        BigDecimal distanceKm = estimateDistanceKm(address);
        BigDecimal carbonKg = distanceKm == null
                ? null
                : distanceKm.multiply(DEFAULT_FREIGHT_CO2_KG_PER_KM).setScale(1, RoundingMode.HALF_UP);

        long totalOrders = metric == null ? 0 : metric.totalOrders();
        long onTimeOrders = metric == null ? 0 : metric.onTimeOrders();
        long totalReceivedQty = metric == null ? 0 : metric.totalReceivedQty();
        long totalDefectQty = metric == null ? 0 : metric.totalDefectQty();
        BigDecimal deliveryStabilityScore = totalOrders == 0
                ? null
                : BigDecimal.valueOf(onTimeOrders * 100.0 / totalOrders).setScale(1, RoundingMode.HALF_UP);
        long inspectedQty = totalReceivedQty + totalDefectQty;
        BigDecimal qualityIssueRate = inspectedQty == 0
                ? null
                : BigDecimal.valueOf(totalDefectQty * 100.0 / inspectedQty).setScale(1, RoundingMode.HALF_UP);

        return new SupplierEsgSnapshot(
                supplierKey,
                name,
                code,
                address,
                esgGrade,
                ecoCertified,
                esgMemo,
                totalOrders,
                onTimeOrders,
                deliveryStabilityScore,
                totalReceivedQty,
                totalDefectQty,
                qualityIssueRate,
                distanceKm,
                carbonKg,
                metric == null ? null : metric.lastReceivedAt()
        );
    }

    private List<SupplierEsgSnapshot> selectRelevant(String question, List<SupplierEsgSnapshot> snapshots) {
        String normalized = normalize(question);
        List<SupplierEsgSnapshot> named = snapshots.stream()
                .filter(s -> normalized.contains(normalize(s.name())) || normalized.contains(normalize(s.code())))
                .toList();
        if (!named.isEmpty()) {
            return named;
        }
        if (normalized.contains("낮") || normalized.contains("리스크") || normalized.contains("위험")) {
            return snapshots.stream()
                    .sorted(Comparator.comparing(this::riskScore).reversed())
                    .limit(5)
                    .toList();
        }
        if (normalized.contains("탄소") || normalized.contains("거리")) {
            return snapshots.stream()
                    .sorted(Comparator.comparing(
                            SupplierEsgSnapshot::estimatedCarbonEmissionKg,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(5)
                    .toList();
        }
        if (normalized.contains("인증") || normalized.contains("친환경")) {
            return snapshots.stream()
                    .filter(SupplierEsgSnapshot::ecoCertified)
                    .limit(5)
                    .toList();
        }
        return snapshots.stream()
                .sorted(Comparator.comparing(this::riskScore).reversed())
                .limit(5)
                .toList();
    }

    private String summarize(String question, List<SupplierEsgSnapshot> snapshots, List<ChatTurn> history) {
        if (snapshots.isEmpty()) {
            return "조회 가능한 협력사 ESG 참고 지표가 없습니다.";
        }
        String rows = snapshots.stream()
                .map(this::formatSnapshot)
                .collect(Collectors.joining("\n"));
        String prompt = """
                너는 WMS 관리자용 ESG 참고 지표 설명 도우미다.

                원칙:
                - 특정 협력사를 추천하거나 발주 결정을 대신하지 않는다.
                - 관리자가 입고 지시를 검토할 때 참고할 수 있게 설명한다.
                - ESG 등급/친환경 인증은 등록 정보이고, 납기/품질/탄소는 운영 데이터 기반 자동 산출값이라고 구분한다.
                - 데이터가 없는 항목은 추정하지 말고 "데이터 부족"이라고 말한다.
                - 한국어로 3~5문장, 필요하면 짧은 목록으로 답한다.

                사용자 질문: %s

                최근 대화:
                %s

                협력사 ESG 참고 지표:
                %s
                """.formatted(question, formatHistory(history), rows);
        try {
            return chatClient.prompt().user(prompt).call().content().trim();
        } catch (Exception e) {
            log.warn("ESG summary failed: {}", e.getMessage());
            return rows;
        }
    }

    private String formatSnapshot(SupplierEsgSnapshot s) {
        return "- %s(%s): ESG등급=%s, 친환경인증=%s, 납기안정성=%s, 품질이슈율=%s, 입고건수=%d, 불량수량=%d, 예상거리=%s, 예상탄소=%s, 메모=%s"
                .formatted(
                        s.name(),
                        s.code(),
                        valueOrDataLack(s.esgGrade()),
                        s.ecoCertified() ? "보유" : "없음",
                        valueOrDataLack(s.deliveryStabilityScore()),
                        valueOrDataLack(s.qualityIssueRate()),
                        s.totalInboundOrders(),
                        s.totalDefectQty(),
                        valueOrDataLack(s.estimatedDistanceKm()),
                        valueOrDataLack(s.estimatedCarbonEmissionKg()),
                        valueOrDataLack(s.esgMemo())
                );
    }

    private BigDecimal estimateDistanceKm(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        String normalized = address.replaceAll("\\s+", "");
        double km;
        if (normalized.contains("강남") || normalized.contains("마포") || normalized.contains("영등포") || normalized.contains("강서")) {
            km = 18.0;
        } else if (normalized.contains("금천") || normalized.contains("가산")) {
            km = 16.0;
        } else if (normalized.contains("성남") || normalized.contains("판교")) {
            km = 32.0;
        } else if (normalized.contains("파주")) {
            km = 48.0;
        } else if (normalized.contains("인천")) {
            km = 43.0;
        } else if (normalized.contains("경기")) {
            km = 38.0;
        } else if (normalized.contains("서울")) {
            km = 22.0;
        } else {
            km = 50.0;
        }
        return BigDecimal.valueOf(km).setScale(1, RoundingMode.HALF_UP);
    }

    private int riskScore(SupplierEsgSnapshot s) {
        int score = 0;
        if ("D".equalsIgnoreCase(s.esgGrade())) score += 40;
        if ("C".equalsIgnoreCase(s.esgGrade())) score += 25;
        if (!s.ecoCertified()) score += 10;
        if (s.deliveryStabilityScore() == null) score += 8;
        else score += Math.max(0, 90 - s.deliveryStabilityScore().intValue());
        if (s.qualityIssueRate() != null) score += s.qualityIssueRate().multiply(BigDecimal.TEN).intValue();
        if (s.estimatedCarbonEmissionKg() != null && s.estimatedCarbonEmissionKg().compareTo(BigDecimal.TEN) > 0) score += 8;
        return score;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "").trim();
    }

    private String formatHistory(List<ChatTurn> history) {
        if (history == null || history.isEmpty()) {
            return "(없음)";
        }
        return history.stream()
                .skip(Math.max(0, history.size() - 6))
                .map(t -> ("%s: %s").formatted(t.role(), t.content()))
                .collect(Collectors.joining("\n"));
    }

    private String valueOrDataLack(Object value) {
        if (value == null) {
            return "데이터 부족";
        }
        if (value instanceof String s && s.isBlank()) {
            return "데이터 부족";
        }
        return value.toString();
    }

    private record OperationalMetric(
            String supplierKey,
            long totalOrders,
            long onTimeOrders,
            long totalReceivedQty,
            long totalDefectQty,
            LocalDateTime lastReceivedAt) {
    }

    public record SupplierEsgSnapshot(
            String supplierKey,
            String name,
            String code,
            String address,
            String esgGrade,
            boolean ecoCertified,
            String esgMemo,
            long totalInboundOrders,
            long onTimeInboundOrders,
            BigDecimal deliveryStabilityScore,
            long totalReceivedQty,
            long totalDefectQty,
            BigDecimal qualityIssueRate,
            BigDecimal estimatedDistanceKm,
            BigDecimal estimatedCarbonEmissionKg,
            LocalDateTime lastReceivedAt) {
    }

    public record EsgChatResponse(
            String question,
            String answer,
            List<SupplierEsgSnapshot> snapshots) {
    }
}
