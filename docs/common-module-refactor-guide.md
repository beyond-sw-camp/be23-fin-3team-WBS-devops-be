# Common Module Refactor Guide

## Current Direction

The `common` module should stay focused on shared infrastructure and cross-cutting concerns.
Service-specific business behavior should live in the owning module.

## Keep In `common`

| Area | Reason |
|---|---|
| `domain/BaseTimeEntity` | Shared JPA base type across services |
| `dtos/CommonErrorDto`, `exception/CommonExceptionHandler` | Unified error contract |
| `auth/*` | Cross-cutting permission check annotations and aspect |
| `audit/AuditLog`, `audit/AuditLogAspect`, `audit/AuditLogEventPublisher`, `kafka/event/*` | Shared audit and event infrastructure |
| `code/CodeGenerator`, `code/NumberingUtil`, `code/MasterCodePolicy`, `code/enums/RegionCode` | Shared numbering and code policy |
| `redis/RedisConfig`, `s3/AwsS3Config`, `search/ElasticsearchConfig`, `search/ElasticsearchProperties` | Infra client/config wiring |
| `common/SystemUser`, `converter/UuidBinaryConverter` | Cross-service utility types |

## Keep In Owning Service

| Target | Move / Keep | Why |
|---|---|---|
| `master` | `warehouse/domain/WarehouseType` | Warehouse type is a master-domain concept |
| `stock` | `instruction/s3/InstructionDocumentS3Uploader` | Upload key rules and instruction bucket are stock-specific |
| `stock` | `document/instruction/*` candidates | Instruction document issuance is currently consumed almost entirely by stock |
| `search-service` | centralized query API and search documents | Unified read model should stay outside the shared jar |

## Changes Applied In This Refactor

1. Removed duplicated `common.code.enums.WarehouseType`.
2. Expanded `master.warehouse.domain.WarehouseType` to carry code metadata directly.
3. Changed `CodeGenerator.generateWarehouseCode(...)` to accept the warehouse type code string.
4. Moved `InstructionDocumentS3Uploader` from `common` to `stock`.
5. Updated `AwsS3Config` to expose both `S3Client` and `S3Presigner`.

## S3 Rule

- `common` owns AWS client construction.
- `stock` owns instruction document upload rules and bucket-specific behavior.
- Service-specific S3 workflows should not be added back into `common`.
