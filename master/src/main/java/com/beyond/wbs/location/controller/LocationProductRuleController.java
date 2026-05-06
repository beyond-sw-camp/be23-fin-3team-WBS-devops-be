package com.beyond.wbs.location.controller;

import com.beyond.wbs.location.dtos.LocationProductRuleCreateDto;
import com.beyond.wbs.location.dtos.LocationProductRuleResDto;
import com.beyond.wbs.location.service.LocationProductRuleService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import com.beyond.wbs.audit.AuditLog;

@RestController
@RequestMapping("/location/rule")
public class LocationProductRuleController {

    private final LocationProductRuleService ruleService;

    @Autowired
    public LocationProductRuleController(LocationProductRuleService ruleService) {
        this.ruleService = ruleService;
    }

    @AuditLog
    @PostMapping("/create")
    public ResponseEntity<?> create(@RequestBody @Valid LocationProductRuleCreateDto dto,
                                    @RequestHeader("X-Client-Id") UUID clientId) {
        UUID id = ruleService.create(dto, clientId);
        return ResponseEntity.status(HttpStatus.OK).body(id);
    }

    @AuditLog
    @GetMapping("/list/{locationId}")
    public ResponseEntity<?> findByLocationId(@PathVariable UUID locationId) {
        List<LocationProductRuleResDto> list = ruleService.findByLocationId(locationId);
        return ResponseEntity.status(HttpStatus.OK).body(list);
    }

    @AuditLog
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> delete(@PathVariable UUID id,
                                    @RequestHeader("X-Client-Id") UUID clientId) {
        ruleService.delete(id, clientId);
        return ResponseEntity.status(HttpStatus.OK).body("");
    }
}
