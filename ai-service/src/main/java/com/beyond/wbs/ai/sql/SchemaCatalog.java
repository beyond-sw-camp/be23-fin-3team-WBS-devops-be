package com.beyond.wbs.ai.sql;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * LLM에게 전달할 "쿼리 가능한 스키마"의 요약 텍스트.
 * 실제 DB 스키마의 부분집합만 노출 (민감 테이블 제외, 핵심 컬럼만).
 * 허용 테이블 화이트리스트도 같이 관리한다.
 */
@Component
public class SchemaCatalog {

    /**
     * 화이트리스트 — 집계/필터 대상(stock_db) + 이름 LOOKUP 전용(master_db).
     */
    public static final Set<String> ALLOWED_TABLES = Set.of(
            // stock_db — 집계·필터·그룹 주대상
            "inventories",
            "inventory_transactions",
            "outbound_orders",
            "outbound_order_items",
            "inbound_orders",
            "inbound_order_items",
            // master_db — JOIN 으로 이름 붙이기만. 집계 컬럼 없음
            "warehouses",
            "products",
            "locations"
    );

    /** LLM에게 주입할 스키마 설명 (토큰 절약 위해 핵심 컬럼만). */
    public String asPromptText() {
        return """
                데이터베이스: stock_db (MySQL, 기본), master_db (JOIN 전용 LOOKUP)
                중요:
                  - 집계/필터/그룹은 반드시 stock_db 테이블만 사용하라.
                  - master_db 테이블(warehouses/products/locations)은 이름 붙이기 JOIN 용도로만 사용. 스키마 prefix 필수: master_db.warehouses, master_db.products, master_db.locations
                  - id 컬럼은 BINARY(16) UUID. 이름으로 필터링하려면 master_db.* 의 name/code/sku 를 WHERE 에서 활용 가능.
                  - enum 컬럼은 아래 명시 값만 사용.

                [inventories] — 현재 재고 (product × location 단위)
                  available_qty INT        사용 가능 수량
                  reserved_qty  INT        예약 수량
                  incoming_qty  INT        입고 예정 수량
                  defect_qty    INT        불량 수량
                  total_qty     INT        총 수량
                  pending_qty   INT
                  product_id    BINARY(16)
                  warehouse_id  BINARY(16)
                  location_id   BINARY(16)
                  client_id     BINARY(16)
                  updated_at    DATETIME

                [inventory_transactions] — 재고 변동 이력
                  qty            INT        변동 수량
                  qty_before     INT
                  qty_after      INT
                  tx_type        ENUM('adjust','dispose','inbound','outbound','reserve','returned','transfer','unreserve')
                  ref_type       ENUM('etc_inout_order','inbound_order','manual','outbound_order','stock_count')
                  created_at     DATETIME
                  product_id     BINARY(16)
                  warehouse_id   BINARY(16)
                  location_id    BINARY(16)
                  inventory_id   BINARY(16)

                [outbound_orders] — 출고 주문 헤더
                  id             BINARY(16)  PK
                  order_no       VARCHAR(20)
                  status         ENUM('approved','cancelled','completed','draft','in_progress','partial')
                  scheduled_date DATE
                  approved_at    DATETIME
                  created_at     DATETIME
                  client_id      BINARY(16)
                  warehouse_id   BINARY(16)
                  store_id       BINARY(16)

                [outbound_order_items] — 출고 아이템
                  outbound_orders_id  BINARY(16)  → outbound_orders.id
                  product_id          BINARY(16)
                  ordered_qty         INT
                  picked_qty          INT
                  dispatched_qty      INT
                  reserved_qty        INT
                  unit_price          DECIMAL(15,2)
                  status              ENUM('completed','pending','picking','shortage')

                [inbound_orders] — 입고 주문 헤더
                  id             BINARY(16)  PK
                  order_no       VARCHAR(30)
                  status         ENUM('approved','cancelled','completed','draft','placing','received')
                  expected_date  DATE
                  created_at     DATETIME
                  supplier_id    BINARY(16)
                  warehouse_id   BINARY(16)
                  client_id      BINARY(16)

                [inbound_order_items] — 입고 아이템
                  inbound_order_id  BINARY(16)  → inbound_orders.id
                  product_id        BINARY(16)
                  ordered_qty       INT
                  received_qty      INT
                  defect_qty        INT
                  unit_price        DECIMAL(15,2)
                  status            ENUM('completed','pending','receiving','shortage')

                ==== master_db — 이름 붙이기 JOIN 전용 (집계 컬럼 없음) ====

                [master_db.warehouses]
                  id    BINARY(16)
                  code  VARCHAR(20)   예: "WH-SE-01"
                  name  VARCHAR(100)  예: "서울중앙 창고"

                [master_db.products]
                  id    BINARY(16)
                  sku   VARCHAR(50)   예: "USB-CABLE-C-1M"
                  name  VARCHAR(150)  예: "USB-C 케이블 1m"

                [master_db.locations]
                  id    BINARY(16)
                  code  VARCHAR(255)  예: "A-1-01-2"
                """;
    }
}
