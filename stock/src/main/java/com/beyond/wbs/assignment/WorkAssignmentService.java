package com.beyond.wbs.assignment;

import com.beyond.wbs.common.client.AccountServiceClient;
import com.beyond.wbs.common.client.MasterServiceClient;
import com.beyond.wbs.common.client.dto.AccountUserListResDto;
import com.beyond.wbs.common.client.dto.LocationResDto;
import com.beyond.wbs.outbounds.assignment.WorkerLastLocation;
import com.beyond.wbs.outbounds.assignment.WorkerLastLocationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkAssignmentService {

    private static final UUID FALLBACK_OPERATOR_ID =
            UUID.fromString("01935c00-0000-7200-8000-000000000001");

    private final AccountServiceClient accountServiceClient;
    private final MasterServiceClient masterServiceClient;
    private final WorkerLastLocationRepository workerLastLocationRepository;
    private final JdbcTemplate jdbcTemplate;

    public UUID assign(WorkTaskType taskType, UUID clientId, UUID requesterId) {
        return assign(taskType, clientId, requesterId, null, null);
    }

    public UUID assign(WorkTaskType taskType, UUID clientId, UUID requesterId,
                       UUID targetWarehouseId, UUID targetLocationId) {
        List<Candidate> candidates = loadCandidates(clientId, requesterId);
        if (candidates.isEmpty()) {
            UUID fallback = requesterId != null ? requesterId : FALLBACK_OPERATOR_ID;
            log.warn("[WORK_AUTO_ASSIGN] no_candidate taskType={}, fallback={}", taskType, mask(fallback));
            return fallback;
        }
        TargetLocation target = resolveTargetLocation(clientId, targetWarehouseId, targetLocationId);

        Candidate selected = candidates.stream()
                .min(Comparator
                        .comparingInt((Candidate c) -> proximityPenalty(c, target))
                        .thenComparingInt(Candidate::activeWorkload)
                        .thenComparingInt(Candidate::rolePriority)
                        .thenComparing(Candidate::name, Comparator.nullsLast(String::compareTo))
                        .thenComparing(c -> c.userId().toString()))
                .orElse(candidates.get(0));

        log.info("[WORK_AUTO_ASSIGN] taskType={}, selected={}({}), workload={}, targetWarehouse={}, targetLocation={}, lastWarehouse={}, lastLocation={}, clientId={}, requesterId={}",
                taskType, selected.name(), selected.roleCode(), selected.activeWorkload(),
                mask(target.warehouseId()), mask(target.locationId()), mask(selected.lastWarehouseId()),
                mask(selected.lastLocationId()), mask(clientId), mask(requesterId));
        return selected.userId();
    }

    public void recordLastLocation(UUID clientId, UUID userId, UUID warehouseId, UUID locationId) {
        if (clientId == null || userId == null || warehouseId == null) {
            return;
        }
        LocationResDto location = fetchLocation(clientId, locationId);
        WorkerLastLocation lastLocation = workerLastLocationRepository
                .findByClientIdAndUserId(clientId, userId)
                .orElseGet(() -> WorkerLastLocation.builder()
                        .clientId(clientId)
                        .userId(userId)
                        .warehouseId(warehouseId)
                        .build());
        lastLocation.updateLocation(
                warehouseId,
                location == null ? null : location.getZoneId(),
                location == null ? null : location.getZoneCode(),
                location == null ? null : location.getZoneName(),
                locationId,
                location == null ? null : location.getCode()
        );
        workerLastLocationRepository.save(lastLocation);
    }

    private List<Candidate> loadCandidates(UUID clientId, UUID requesterId) {
        try {
            List<AccountUserListResDto> users = accountServiceClient.getUsers(
                    requesterId == null ? FALLBACK_OPERATOR_ID.toString() : requesterId.toString());
            return users.stream()
                    .filter(user -> user.getId() != null)
                    .filter(user -> isAssignableRole(user.getRoleCode()))
                    .map(user -> {
                        WorkerLastLocation lastLocation = clientId == null ? null : workerLastLocationRepository
                                .findByClientIdAndUserId(clientId, user.getId())
                                .orElse(null);
                        return new Candidate(
                            user.getId(),
                            user.getName(),
                            user.getLoginId(),
                            user.getRoleCode(),
                            rolePriority(user.getRoleCode()),
                            activeWorkload(clientId, user.getId()),
                            lastLocation == null ? null : lastLocation.getWarehouseId(),
                            lastLocation == null ? null : lastLocation.getZoneId(),
                            lastLocation == null ? null : lastLocation.getZoneCode(),
                            lastLocation == null ? null : lastLocation.getLocationId());
                    })
                    .toList();
        } catch (Exception e) {
            log.warn("[WORK_AUTO_ASSIGN] account users load failed: {}", e.getMessage());
            return List.of();
        }
    }

    private int proximityPenalty(Candidate candidate, TargetLocation target) {
        if (target == null || target.warehouseId() == null) {
            return 40;
        }
        if (target.locationId() != null && target.locationId().equals(candidate.lastLocationId())) {
            return 0;
        }
        if (target.zoneId() != null && target.zoneId().equals(candidate.lastZoneId())) {
            return 5;
        }
        if (target.zoneCode() != null && target.zoneCode().equals(candidate.lastZoneCode())) {
            return 8;
        }
        if (target.warehouseId().equals(candidate.lastWarehouseId())) {
            return 25;
        }
        return 50;
    }

    private TargetLocation resolveTargetLocation(UUID clientId, UUID warehouseId, UUID locationId) {
        if (locationId == null) {
            return new TargetLocation(warehouseId, null, null, null);
        }
        LocationResDto location = fetchLocation(clientId, locationId);
        if (location == null) {
            return new TargetLocation(warehouseId, null, null, locationId);
        }
        return new TargetLocation(
                warehouseId,
                location.getZoneId(),
                location.getZoneCode(),
                locationId);
    }

    private LocationResDto fetchLocation(UUID clientId, UUID locationId) {
        if (clientId == null || locationId == null) {
            return null;
        }
        try {
            return masterServiceClient.getLocation(locationId, clientId.toString());
        } catch (Exception e) {
            log.warn("[WORK_AUTO_ASSIGN] location fetch failed locationId={}, err={}", mask(locationId), e.getMessage());
            return null;
        }
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
            int rolePriority,
            int activeWorkload,
            UUID lastWarehouseId,
            UUID lastZoneId,
            String lastZoneCode,
            UUID lastLocationId) {
    }

    private record TargetLocation(
            UUID warehouseId,
            UUID zoneId,
            String zoneCode,
            UUID locationId) {
    }
}
