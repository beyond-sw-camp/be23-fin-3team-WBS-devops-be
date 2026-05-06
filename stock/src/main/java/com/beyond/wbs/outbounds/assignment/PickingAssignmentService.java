package com.beyond.wbs.outbounds.assignment;

import com.beyond.wbs.common.client.AccountServiceClient;
import com.beyond.wbs.common.client.MasterServiceClient;
import com.beyond.wbs.common.client.dto.AccountUserListResDto;
import com.beyond.wbs.common.client.dto.LocationResDto;
import com.beyond.wbs.inventory.domain.Inventory;
import com.beyond.wbs.inventory.repository.InventoryRepository;
import com.beyond.wbs.outbounds.domain.PickingListStatus;
import com.beyond.wbs.outbounds.repository.PickinglistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PickingAssignmentService {

    private static final EnumSet<PickingListStatus> ACTIVE_STATUSES =
            EnumSet.of(PickingListStatus.pending, PickingListStatus.in_progress);

    private final AccountServiceClient accountServiceClient;
    private final MasterServiceClient masterServiceClient;
    private final InventoryRepository inventoryRepository;
    private final PickinglistRepository pickinglistRepository;
    private final WorkerLastLocationRepository workerLastLocationRepository;

    public UUID assignForWave(UUID clientId, UUID requesterId, UUID warehouseId, List<UUID> productIds) {
        List<Candidate> candidates = loadCandidates(clientId, requesterId);
        if (candidates.isEmpty()) {
            log.warn("[PICKING_AUTO_ASSIGN] no_candidate clientId={}, fallback={}", mask(clientId), mask(requesterId));
            return requesterId;
        }
        TargetLocation target = resolveTargetLocation(clientId, warehouseId, productIds);

        Candidate selected = candidates.stream()
                .min(Comparator
                        .comparingInt((Candidate c) -> score(c, target, 0))
                        .thenComparing(Candidate::rolePriority)
                        .thenComparing(Candidate::name, Comparator.nullsLast(String::compareTo))
                        .thenComparing(c -> c.userId().toString()))
                .orElse(candidates.get(0));

        log.info("[PICKING_AUTO_ASSIGN] selected={}({}), activeTasks={}, targetZone={}, lastZone={}, score={}, clientId={}, requesterId={}",
                selected.name(), selected.roleCode(), selected.activeTasks(), target.zoneCode(),
                selected.lastZoneCode(), score(selected, target, 0), mask(clientId), mask(requesterId));
        return selected.userId();
    }

    public Map<UUID, UUID> assignByProducts(UUID clientId, UUID requesterId, UUID warehouseId, List<UUID> productIds) {
        List<Candidate> candidates = loadCandidates(clientId, requesterId);
        Map<UUID, UUID> result = new HashMap<>();
        if (candidates.isEmpty()) {
            log.warn("[PICKING_AUTO_ASSIGN] no_candidate products={}, fallback={}", productIds.size(), mask(requesterId));
            for (UUID productId : productIds) {
                result.put(productId, requesterId);
            }
            return result;
        }

        Map<UUID, Integer> planned = new HashMap<>();
        for (UUID productId : productIds) {
            TargetLocation target = resolveTargetLocation(clientId, warehouseId, List.of(productId));
            Candidate selected = candidates.stream()
                    .min(Comparator
                            .comparingInt((Candidate c) -> score(c, target, planned.getOrDefault(c.userId(), 0)))
                            .thenComparing(Candidate::rolePriority)
                            .thenComparing(Candidate::name, Comparator.nullsLast(String::compareTo))
                            .thenComparing(c -> c.userId().toString()))
                    .orElse(candidates.get(0));

            result.put(productId, selected.userId());
            planned.merge(selected.userId(), 1, Integer::sum);
            log.info("[PICKING_AUTO_ASSIGN] productId={}, targetZone={}, selected={}({}), activeTasks={}, lastZone={}, plannedAdd={}, score={}",
                    mask(productId), target.zoneCode(), selected.name(), selected.roleCode(), selected.activeTasks(),
                    selected.lastZoneCode(), planned.get(selected.userId()),
                    score(selected, target, planned.get(selected.userId())));
        }
        return result;
    }

    public void recordLastLocation(UUID clientId, UUID userId, UUID warehouseId, UUID locationId) {
        if (clientId == null || userId == null || warehouseId == null || locationId == null) {
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
        log.info("[PICKING_LAST_LOCATION] userId={}, warehouseId={}, zone={}, location={}",
                mask(userId), mask(warehouseId),
                location == null ? "-" : location.getZoneCode(),
                location == null ? mask(locationId) : location.getCode());
    }

    private List<Candidate> loadCandidates(UUID clientId, UUID requesterId) {
        try {
            List<AccountUserListResDto> users = accountServiceClient.getUsers(requesterId.toString());
            List<Candidate> candidates = new ArrayList<>();
            for (AccountUserListResDto user : users) {
                if (user.getId() == null || !isAssignableRole(user.getRoleCode())) {
                    continue;
                }
                long activeTasks = pickinglistRepository.countActiveWorkload(clientId, user.getId(), ACTIVE_STATUSES);
                WorkerLastLocation lastLocation = workerLastLocationRepository
                        .findByClientIdAndUserId(clientId, user.getId())
                        .orElse(null);
                candidates.add(new Candidate(
                        user.getId(),
                        user.getName(),
                        user.getRoleCode(),
                        rolePriority(user.getRoleCode()),
                        Math.toIntExact(activeTasks),
                        lastLocation == null ? null : lastLocation.getWarehouseId(),
                        lastLocation == null ? null : lastLocation.getZoneId(),
                        lastLocation == null ? null : lastLocation.getZoneCode(),
                        lastLocation == null ? null : lastLocation.getLocationId()
                ));
            }
            return candidates;
        } catch (Exception e) {
            log.warn("[PICKING_AUTO_ASSIGN] candidate_load_failed requesterId={}, reason={}",
                    mask(requesterId), e.getMessage());
            return List.of();
        }
    }

    private boolean isAssignableRole(String roleCode) {
        return "OPERATOR".equalsIgnoreCase(roleCode) || "MANAGER".equalsIgnoreCase(roleCode);
    }

    private int rolePriority(String roleCode) {
        if ("OPERATOR".equalsIgnoreCase(roleCode)) {
            return 0;
        }
        if ("MANAGER".equalsIgnoreCase(roleCode)) {
            return 1;
        }
        return 9;
    }

    private int score(Candidate candidate, TargetLocation target, int plannedTasks) {
        return (candidate.activeTasks() + plannedTasks) * 100
                + proximityPenalty(candidate, target)
                + candidate.rolePriority();
    }

    private int proximityPenalty(Candidate candidate, TargetLocation target) {
        if (target == null || target.locationId() == null) {
            return 40;
        }
        if (target.locationId().equals(candidate.lastLocationId())) {
            return 0;
        }
        if (target.zoneId() != null && target.zoneId().equals(candidate.lastZoneId())) {
            return 5;
        }
        if (target.zoneCode() != null && target.zoneCode().equals(candidate.lastZoneCode())) {
            return 8;
        }
        if (target.warehouseId() != null && target.warehouseId().equals(candidate.lastWarehouseId())) {
            return 25;
        }
        return 50;
    }

    private TargetLocation resolveTargetLocation(UUID clientId, UUID warehouseId, List<UUID> productIds) {
        for (UUID productId : productIds) {
            List<Inventory> reservedLocations = inventoryRepository
                    .findLocationsWithStock(productId, warehouseId).stream()
                    .filter(i -> i.getReservedQty() != null && i.getReservedQty() > 0)
                    .toList();
            if (reservedLocations.isEmpty()) {
                continue;
            }
            UUID locationId = reservedLocations.get(0).getLocationId();
            LocationResDto location = fetchLocation(clientId, locationId);
            if (location == null) {
                return new TargetLocation(warehouseId, null, null, locationId);
            }
            return new TargetLocation(warehouseId, location.getZoneId(), location.getZoneCode(), locationId);
        }
        return new TargetLocation(warehouseId, null, null, null);
    }

    private LocationResDto fetchLocation(UUID clientId, UUID locationId) {
        try {
            return masterServiceClient.getLocation(locationId, clientId.toString());
        } catch (Exception e) {
            log.warn("[PICKING_AUTO_ASSIGN] location_fetch_failed locationId={}, reason={}",
                    mask(locationId), e.getMessage());
            return null;
        }
    }

    private String mask(UUID value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.substring(0, 8) + "...";
    }

    private record Candidate(
            UUID userId,
            String name,
            String roleCode,
            int rolePriority,
            int activeTasks,
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
