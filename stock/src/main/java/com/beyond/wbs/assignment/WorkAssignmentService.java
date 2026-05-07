package com.beyond.wbs.assignment;

import com.beyond.wbs.common.client.AccountServiceClient;
import com.beyond.wbs.common.client.dto.AccountUserListResDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkAssignmentService {

    private static final UUID FALLBACK_OPERATOR_ID =
            UUID.fromString("01935c00-0000-7200-8000-000000000001");

    private static final Map<WorkTaskType, List<String>> LOGIN_PREFERENCES = Map.of(
            WorkTaskType.INBOUND_INSPECTION, List.of("operator14", "operator8", "operator5", "operator12"),
            WorkTaskType.PLACEMENT, List.of("operator4", "operator11", "operator2"),
            WorkTaskType.PICKING, List.of("operator3", "operator10", "operator2"),
            WorkTaskType.OUTBOUND_DISPATCH, List.of("operator6", "operator13", "operator2"),
            WorkTaskType.STOCK_COUNT, List.of("operator9", "operator2", "operator14"),
            WorkTaskType.TRANSFER, List.of("operator2", "operator9", "operator1"),
            WorkTaskType.ETC_INOUT, List.of("operator1", "operator15", "operator2")
    );

    private final AccountServiceClient accountServiceClient;
    private final JdbcTemplate jdbcTemplate;

    public UUID assign(WorkTaskType taskType, UUID clientId, UUID requesterId) {
        List<Candidate> candidates = loadCandidates(requesterId);
        if (candidates.isEmpty()) {
            UUID fallback = requesterId != null ? requesterId : FALLBACK_OPERATOR_ID;
            log.warn("[WORK_AUTO_ASSIGN] no_candidate taskType={}, fallback={}", taskType, mask(fallback));
            return fallback;
        }

        Candidate selected = candidates.stream()
                .min(Comparator
                        .comparingInt((Candidate c) -> preferenceRank(taskType, c.loginId()))
                        .thenComparingInt(c -> activeWorkload(clientId, c.userId()))
                        .thenComparingInt(Candidate::rolePriority)
                        .thenComparing(Candidate::name, Comparator.nullsLast(String::compareTo))
                        .thenComparing(c -> c.userId().toString()))
                .orElse(candidates.get(0));

        log.info("[WORK_AUTO_ASSIGN] taskType={}, selected={}({}), loginId={}, workload={}, clientId={}, requesterId={}",
                taskType, selected.name(), selected.roleCode(), selected.loginId(),
                activeWorkload(clientId, selected.userId()), mask(clientId), mask(requesterId));
        return selected.userId();
    }

    private List<Candidate> loadCandidates(UUID requesterId) {
        try {
            List<AccountUserListResDto> users = accountServiceClient.getUsers(
                    requesterId == null ? FALLBACK_OPERATOR_ID.toString() : requesterId.toString());
            return users.stream()
                    .filter(user -> user.getId() != null)
                    .filter(user -> isAssignableRole(user.getRoleCode()))
                    .map(user -> new Candidate(
                            user.getId(),
                            user.getName(),
                            user.getLoginId(),
                            user.getRoleCode(),
                            rolePriority(user.getRoleCode())))
                    .toList();
        } catch (Exception e) {
            log.warn("[WORK_AUTO_ASSIGN] account users load failed: {}", e.getMessage());
            return List.of();
        }
    }

    private int preferenceRank(WorkTaskType taskType, String loginId) {
        List<String> preferences = LOGIN_PREFERENCES.getOrDefault(taskType, List.of());
        String normalized = loginId == null ? "" : loginId.toLowerCase(Locale.ROOT);
        int index = preferences.indexOf(normalized);
        return index < 0 ? 100 : index;
    }

    private int activeWorkload(UUID clientId, UUID userId) {
        if (clientId == null || userId == null) {
            return 0;
        }
        String client = clientId.toString();
        String user = userId.toString();
        try {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT
                        (SELECT COUNT(*) FROM inbound_orders
                         WHERE client_id = UNHEX(REPLACE(?, '-', ''))
                           AND assigned_to = UNHEX(REPLACE(?, '-', ''))
                           AND status IN ('approved'))
                      + (SELECT COUNT(*) FROM placement_orders
                         WHERE client_id = UNHEX(REPLACE(?, '-', ''))
                           AND assigned_to = UNHEX(REPLACE(?, '-', ''))
                           AND status IN ('pending', 'in_progress'))
                      + (SELECT COUNT(*) FROM picking_lists
                         WHERE client_id = UNHEX(REPLACE(?, '-', ''))
                           AND assigned_to = UNHEX(REPLACE(?, '-', ''))
                           AND status IN ('pending', 'in_progress'))
                      + (SELECT COUNT(*) FROM outbound_orders
                         WHERE client_id = UNHEX(REPLACE(?, '-', ''))
                           AND assigned_to = UNHEX(REPLACE(?, '-', ''))
                           AND status IN ('approved', 'in_progress', 'partial'))
                      + (SELECT COUNT(*) FROM stock_count_orders
                         WHERE client_id = UNHEX(REPLACE(?, '-', ''))
                           AND assigned_to = UNHEX(REPLACE(?, '-', ''))
                           AND status IN ('in_progress'))
                      + (SELECT COUNT(*) FROM transfer_orders
                         WHERE client_id = UNHEX(REPLACE(?, '-', ''))
                           AND assigned_to = UNHEX(REPLACE(?, '-', ''))
                           AND status IN ('approved', 'in_progress', 'partial'))
                      + (SELECT COUNT(*) FROM etc_inout_orders
                         WHERE client_id = UNHEX(REPLACE(?, '-', ''))
                           AND assigned_to = UNHEX(REPLACE(?, '-', ''))
                           AND status IN ('approved'))
                    """, Integer.class,
                    client, user,
                    client, user,
                    client, user,
                    client, user,
                    client, user,
                    client, user,
                    client, user);
            return count == null ? 0 : count;
        } catch (Exception e) {
            log.warn("[WORK_AUTO_ASSIGN] workload count failed userId={}, err={}", mask(userId), e.getMessage());
            return 0;
        }
    }

    private boolean isAssignableRole(String roleCode) {
        if (roleCode == null) {
            return false;
        }
        String normalized = roleCode.toUpperCase(Locale.ROOT);
        return normalized.contains("OPERATOR") || normalized.contains("MANAGER");
    }

    private int rolePriority(String roleCode) {
        if (roleCode == null) {
            return 9;
        }
        String normalized = roleCode.toUpperCase(Locale.ROOT);
        if (normalized.contains("OPERATOR")) {
            return 0;
        }
        if (normalized.contains("MANAGER")) {
            return 1;
        }
        return 9;
    }

    private String mask(UUID id) {
        if (id == null) {
            return null;
        }
        String value = id.toString();
        return value.substring(0, 8) + "...";
    }

    private record Candidate(
            UUID userId,
            String name,
            String loginId,
            String roleCode,
            int rolePriority) {
    }
}
