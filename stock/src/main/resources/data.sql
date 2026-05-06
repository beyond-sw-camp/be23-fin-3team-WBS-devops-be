-- =============================================
-- Stock Module Dummy Data
-- =============================================
-- 이 파일에는 ERP 발주서/수주서 + 채번 시퀀스만 시드한다.
-- 재고/입고/출고/이동/피킹 등 WMS 내부 데이터는 "발주서 불러오기"로부터
-- 실제 서비스 플로우를 따라 생성하라 (FK 불일치 · client_id/user_id 혼동 방지).
--
-- ─────────────────────────────────────────────────────────
-- [참조 UUID] master 모듈 data.sql 과 반드시 일치해야 함
-- ─────────────────────────────────────────────────────────
--  client_id                      : 01935c00-0000-7000-8000-000000000001
--
--  warehouse (서울중앙/정상)      : 01935c00-0000-7100-8000-000000000001
--  warehouse (인천반품/반품+불량) : 01935c00-0000-7100-8000-000000000002
--  warehouse (경기폐기/폐기)      : 01935c00-0000-7100-8000-000000000003
--
--  supplier (LogiX)               : 01935c00-0000-7500-8000-000000000001
--  supplier (TechSupply)          : 01935c00-0000-7500-8000-000000000002
--  supplier (FastDeliver)         : 01935c00-0000-7500-8000-000000000003
--
--  store (강남지점)               : 01935c00-0000-7600-8000-000000000001
--  store (부산지점)               : 01935c00-0000-7600-8000-000000000002
--
--  product 1 USB-C 케이블  (8,900)  : 01935c00-0000-7700-8000-000000000001  [LogiX]
--  product 2 USB 허브     (15,900)  : 01935c00-0000-7700-8000-000000000002  [LogiX]
--  product 3 기계식 키보드 (89,000) : 01935c00-0000-7700-8000-000000000003  [TechSupply]
--  product 4 무선 마우스  (25,900)  : 01935c00-0000-7700-8000-000000000004  [TechSupply]
--  product 5 27인치 모니터(350,000) : 01935c00-0000-7700-8000-000000000005  [FastDeliver]
--
-- ─────────────────────────────────────────────────────────
-- [사용자 UUID] account 모듈 InitialDataLoad.java 와 일치해야 함
-- !! 사용자 컬럼(created_by / assigned_to / approved_by 등)에는
--    반드시 아래 UUID 중 하나만 사용. client_id(7000)는 회사이지 사용자가 아님.
-- ─────────────────────────────────────────────────────────
--  admin1 관리자       : 01935c00-0000-8000-8000-000000000001
--  manager1 김매니저   : 01935c00-0000-8000-8000-000000000002
--  manager2 박입고     : 01935c00-0000-8000-8000-000000000003
--  manager3 최출고     : 01935c00-0000-8000-8000-000000000004
--  operator1 이오퍼레이터(자동배당): 01935c00-0000-7200-8000-000000000001
--  operator2 강현장    : 01935c00-0000-8000-8000-000000000011
--
-- ─────────────────────────────────────────────────────────
-- [시드 데이터 작성 규칙]
-- ─────────────────────────────────────────────────────────
-- 1) 지시서 번호 형식 (팀 합의)
--      - 발주서: PO-TEST-NNN
--      - 수주서: SO-TEST-NNN
--      - 입고지시서: IB-YYYYMMDD-NNNNN  (시드 금지 — 서비스가 채번)
--      - 출고지시서: OB-YYYYMMDD-NNNNN  (시드 금지 — 서비스가 채번)
--      - 이동지시서: TR-YYYYMMDD-NNNNN  (시드 금지 — 서비스가 채번)
--      - 피킹리스트: PK-YYYYMMDD-NNNNN  (시드 금지 — 서비스가 채번)
--
-- 2) 날짜 규칙
--      - 현재 날짜(개발 기준일 2026-04-23)보다 미래 날짜 사용
--      - order_date < scheduled_date 순서 유지
--      - 시간대는 Asia/Seoul 가정
--
-- 3) 상태값
--      - ERP 발주서(erp_purchase_orders.status): 시드는 `approved` 로 통일
--        · draft = ERP 내부 임시 상태, WMS 불러오기 모달에 뜨지 않음
--        · closed = WMS가 입고지시서로 변환 완료 (서비스가 자동 전환)
--      - ERP 수주서(erp_sales_orders.status): 시드는 `draft` 로 통일
--        · 수주서 불러오기 → 출고지시서 생성 시 서비스가 처리
--
-- 4) UUID 할당 규칙 (충돌 방지)
--      - 발주서 ID:  aa000001-0001-0001-0001-000000000NNN  (현재 015까지 사용)
--      - 발주서 품목 ID: ab000001-0001-0001-0001-000000000NNN (현재 020까지 사용)
--      - 수주서 ID (1~10번, 기존): a~f로 시작하는 fancy UUID (변경 금지)
--      - 수주서 ID (11번~, 신규): 50000000-0001-0001-0001-000000000NNN  (현재 025까지 사용)
--      - 수주서 품목 ID:
--          · 1~6번: a0000001~f0000001 접두
--          · 7~10번: 10000001~40000001 접두
--          · 11번~: 50000011 ~ 접두 (수주서 번호와 맞춤)
--
-- 5) 상품-협력사 매핑 (발주서 작성 시)
--      - LogiX       : product 1 (케이블), product 2 (허브)
--      - TechSupply  : product 3 (키보드), product 4 (마우스)
--      - FastDeliver : product 5 (모니터)
--
-- 6) 금지 사항
--      - ❌ 사용자 컬럼(*_by, assigned_to)에 client_id(7000) 넣지 말 것
--      - ❌ 재고/입고/출고/이동/피킹 테이블 직접 INSERT 금지
--      - ❌ 같은 so_no / po_no 재사용 금지
--      - ❌ UNHEX 값에 대소문자 섞인 UUID (MySQL UNHEX는 대소문자 무관하나 일관성 위해 소문자 고정)
-- =============================================

-- =============================================
-- 채번 테이블 초기 데이터
-- =============================================
INSERT INTO sequences (type, current_val)
VALUES ('order', 0),
       ('picking', 0),
       ('dispatch', 0),
       ('transfer', 0),
       ('transfer_cluster', 0),
       ('inbound', 0),
       ('placement', 0),
       ('receipt', 0),
       ('etc_inout', 0),
       ('stock_count', 0);

-- =============================================
-- ERP 수주서 더미 데이터 (10건)
-- 강남지점 5건 / 부산지점 5건
-- =============================================

-- 1번 수주서 (강남지점)
INSERT INTO erp_sales_orders (id, client_id, store_id, so_no, status, order_date, scheduled_date, shipping_address, note, created_at)
VALUES (
    UNHEX(REPLACE('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7000-8000-000000000001', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7600-8000-000000000001', '-', '')),
    'SO-TEST-001', 'draft', '2026-04-01', '2026-04-05',
    '서울시 강남구 테헤란로 123', NULL, NOW()
);

-- 2번 수주서 (강남지점)
INSERT INTO erp_sales_orders (id, client_id, store_id, so_no, status, order_date, scheduled_date, shipping_address, note, created_at)
VALUES (
    UNHEX(REPLACE('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7000-8000-000000000001', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7600-8000-000000000001', '-', '')),
    'SO-TEST-002', 'draft', '2026-04-01', '2026-04-06',
    '서울시 서초구 서초대로 456', NULL, NOW()
);

-- 3번 수주서 (강남지점)
INSERT INTO erp_sales_orders (id, client_id, store_id, so_no, status, order_date, scheduled_date, shipping_address, note, created_at)
VALUES (
    UNHEX(REPLACE('cccccccc-cccc-cccc-cccc-cccccccccccc', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7000-8000-000000000001', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7600-8000-000000000001', '-', '')),
    'SO-TEST-003', 'draft', '2026-04-02', '2026-04-07',
    '서울시 송파구 올림픽로 789', NULL, NOW()
);

-- 4번 수주서 (강남지점)
INSERT INTO erp_sales_orders (id, client_id, store_id, so_no, status, order_date, scheduled_date, shipping_address, note, created_at)
VALUES (
    UNHEX(REPLACE('dddddddd-dddd-dddd-dddd-dddddddddddd', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7000-8000-000000000001', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7600-8000-000000000001', '-', '')),
    'SO-TEST-004', 'draft', '2026-04-02', '2026-04-08',
    '서울시 마포구 월드컵로 100', NULL, NOW()
);

-- 5번 수주서 (강남지점)
INSERT INTO erp_sales_orders (id, client_id, store_id, so_no, status, order_date, scheduled_date, shipping_address, note, created_at)
VALUES (
    UNHEX(REPLACE('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7000-8000-000000000001', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7600-8000-000000000001', '-', '')),
    'SO-TEST-005', 'draft', '2026-04-03', '2026-04-09',
    '서울시 용산구 이태원로 200', NULL, NOW()
);

-- 6번 수주서 (부산지점)
INSERT INTO erp_sales_orders (id, client_id, store_id, so_no, status, order_date, scheduled_date, shipping_address, note, created_at)
VALUES (
    UNHEX(REPLACE('ffffffff-ffff-ffff-ffff-ffffffffffff', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7000-8000-000000000001', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7600-8000-000000000002', '-', '')),
    'SO-TEST-006', 'draft', '2026-04-03', '2026-04-10',
    '부산시 해운대구 해운대로 300', NULL, NOW()
);

-- 7번 수주서 (부산지점)
INSERT INTO erp_sales_orders (id, client_id, store_id, so_no, status, order_date, scheduled_date, shipping_address, note, created_at)
VALUES (
    UNHEX(REPLACE('11111111-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7000-8000-000000000001', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7600-8000-000000000002', '-', '')),
    'SO-TEST-007', 'draft', '2026-04-04', '2026-04-11',
    '부산시 부산진구 서면로 400', NULL, NOW()
);

-- 8번 수주서 (부산지점)
INSERT INTO erp_sales_orders (id, client_id, store_id, so_no, status, order_date, scheduled_date, shipping_address, note, created_at)
VALUES (
    UNHEX(REPLACE('22222222-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7000-8000-000000000001', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7600-8000-000000000002', '-', '')),
    'SO-TEST-008', 'draft', '2026-04-05', '2026-04-12',
    '부산시 동래구 충렬대로 500', NULL, NOW()
);

-- 9번 수주서 (부산지점)
INSERT INTO erp_sales_orders (id, client_id, store_id, so_no, status, order_date, scheduled_date, shipping_address, note, created_at)
VALUES (
    UNHEX(REPLACE('33333333-cccc-cccc-cccc-cccccccccccc', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7000-8000-000000000001', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7600-8000-000000000002', '-', '')),
    'SO-TEST-009', 'draft', '2026-04-06', '2026-04-13',
    '부산시 남구 대연로 600', NULL, NOW()
);

-- 10번 수주서 (부산지점)
INSERT INTO erp_sales_orders (id, client_id, store_id, so_no, status, order_date, scheduled_date, shipping_address, note, created_at)
VALUES (
    UNHEX(REPLACE('44444444-dddd-dddd-dddd-dddddddddddd', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7000-8000-000000000001', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7600-8000-000000000002', '-', '')),
    'SO-TEST-010', 'draft', '2026-04-07', '2026-04-14',
    '부산시 수영구 광안로 700', NULL, NOW()
);

-- =============================================
-- ERP 수주서 품목 더미 데이터
-- 각 수주서마다 2~3개 품목 (master 모듈 product 5개 조합)
-- =============================================

-- 1번 수주서 품목 (USB-C 케이블 + USB 허브 + 키보드)
INSERT INTO erp_sales_order_items (id, sales_order_id, product_id, qty, allocated_qty, dispatched_qty, unit_price, note)
VALUES (
    UNHEX(REPLACE('a0000001-0001-0001-0001-000000000001', '-', '')),
    UNHEX(REPLACE('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7700-8000-000000000001', '-', '')),
    100, 0, 0, 8900.00, NULL
);
INSERT INTO erp_sales_order_items (id, sales_order_id, product_id, qty, allocated_qty, dispatched_qty, unit_price, note)
VALUES (
    UNHEX(REPLACE('a0000001-0001-0001-0001-000000000002', '-', '')),
    UNHEX(REPLACE('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7700-8000-000000000002', '-', '')),
    80, 0, 0, 15900.00, NULL
);
INSERT INTO erp_sales_order_items (id, sales_order_id, product_id, qty, allocated_qty, dispatched_qty, unit_price, note)
VALUES (
    UNHEX(REPLACE('a0000001-0001-0001-0001-000000000003', '-', '')),
    UNHEX(REPLACE('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7700-8000-000000000003', '-', '')),
    50, 0, 0, 89000.00, NULL
);

-- 2번 수주서 품목 (케이블 + 마우스)
INSERT INTO erp_sales_order_items (id, sales_order_id, product_id, qty, allocated_qty, dispatched_qty, unit_price, note)
VALUES (
    UNHEX(REPLACE('b0000001-0001-0001-0001-000000000001', '-', '')),
    UNHEX(REPLACE('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7700-8000-000000000001', '-', '')),
    150, 0, 0, 8900.00, NULL
);
INSERT INTO erp_sales_order_items (id, sales_order_id, product_id, qty, allocated_qty, dispatched_qty, unit_price, note)
VALUES (
    UNHEX(REPLACE('b0000001-0001-0001-0001-000000000002', '-', '')),
    UNHEX(REPLACE('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7700-8000-000000000004', '-', '')),
    100, 0, 0, 25900.00, NULL
);

-- 3번 수주서 품목 (케이블 + 허브 + 모니터)
INSERT INTO erp_sales_order_items (id, sales_order_id, product_id, qty, allocated_qty, dispatched_qty, unit_price, note)
VALUES (
    UNHEX(REPLACE('c0000001-0001-0001-0001-000000000001', '-', '')),
    UNHEX(REPLACE('cccccccc-cccc-cccc-cccc-cccccccccccc', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7700-8000-000000000001', '-', '')),
    200, 0, 0, 8900.00, NULL
);
INSERT INTO erp_sales_order_items (id, sales_order_id, product_id, qty, allocated_qty, dispatched_qty, unit_price, note)
VALUES (
    UNHEX(REPLACE('c0000001-0001-0001-0001-000000000002', '-', '')),
    UNHEX(REPLACE('cccccccc-cccc-cccc-cccc-cccccccccccc', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7700-8000-000000000002', '-', '')),
    60, 0, 0, 15900.00, NULL
);
INSERT INTO erp_sales_order_items (id, sales_order_id, product_id, qty, allocated_qty, dispatched_qty, unit_price, note)
VALUES (
    UNHEX(REPLACE('c0000001-0001-0001-0001-000000000003', '-', '')),
    UNHEX(REPLACE('cccccccc-cccc-cccc-cccc-cccccccccccc', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7700-8000-000000000005', '-', '')),
    30, 0, 0, 350000.00, NULL
);

-- 4번 수주서 품목 (허브 + 마우스)
INSERT INTO erp_sales_order_items (id, sales_order_id, product_id, qty, allocated_qty, dispatched_qty, unit_price, note)
VALUES (
    UNHEX(REPLACE('d0000001-0001-0001-0001-000000000001', '-', '')),
    UNHEX(REPLACE('dddddddd-dddd-dddd-dddd-dddddddddddd', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7700-8000-000000000002', '-', '')),
    120, 0, 0, 15900.00, NULL
);
INSERT INTO erp_sales_order_items (id, sales_order_id, product_id, qty, allocated_qty, dispatched_qty, unit_price, note)
VALUES (
    UNHEX(REPLACE('d0000001-0001-0001-0001-000000000002', '-', '')),
    UNHEX(REPLACE('dddddddd-dddd-dddd-dddd-dddddddddddd', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7700-8000-000000000004', '-', '')),
    70, 0, 0, 25900.00, NULL
);

-- 5번 수주서 품목 (케이블 + 허브 + 모니터)
INSERT INTO erp_sales_order_items (id, sales_order_id, product_id, qty, allocated_qty, dispatched_qty, unit_price, note)
VALUES (
    UNHEX(REPLACE('e0000001-0001-0001-0001-000000000001', '-', '')),
    UNHEX(REPLACE('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7700-8000-000000000001', '-', '')),
    180, 0, 0, 8900.00, NULL
);
INSERT INTO erp_sales_order_items (id, sales_order_id, product_id, qty, allocated_qty, dispatched_qty, unit_price, note)
VALUES (
    UNHEX(REPLACE('e0000001-0001-0001-0001-000000000002', '-', '')),
    UNHEX(REPLACE('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7700-8000-000000000002', '-', '')),
    90, 0, 0, 15900.00, NULL
);
INSERT INTO erp_sales_order_items (id, sales_order_id, product_id, qty, allocated_qty, dispatched_qty, unit_price, note)
VALUES (
    UNHEX(REPLACE('e0000001-0001-0001-0001-000000000003', '-', '')),
    UNHEX(REPLACE('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7700-8000-000000000005', '-', '')),
    40, 0, 0, 350000.00, NULL
);

-- 6번 수주서 품목 (케이블 + 키보드)
INSERT INTO erp_sales_order_items (id, sales_order_id, product_id, qty, allocated_qty, dispatched_qty, unit_price, note)
VALUES (
    UNHEX(REPLACE('f0000001-0001-0001-0001-000000000001', '-', '')),
    UNHEX(REPLACE('ffffffff-ffff-ffff-ffff-ffffffffffff', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7700-8000-000000000001', '-', '')),
    70, 0, 0, 8900.00, NULL
);
INSERT INTO erp_sales_order_items (id, sales_order_id, product_id, qty, allocated_qty, dispatched_qty, unit_price, note)
VALUES (
    UNHEX(REPLACE('f0000001-0001-0001-0001-000000000002', '-', '')),
    UNHEX(REPLACE('ffffffff-ffff-ffff-ffff-ffffffffffff', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7700-8000-000000000003', '-', '')),
    90, 0, 0, 89000.00, NULL
);

-- 7번 수주서 품목 (케이블 + 허브 + 마우스)
INSERT INTO erp_sales_order_items (id, sales_order_id, product_id, qty, allocated_qty, dispatched_qty, unit_price, note)
VALUES (
    UNHEX(REPLACE('10000001-0001-0001-0001-000000000001', '-', '')),
    UNHEX(REPLACE('11111111-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7700-8000-000000000001', '-', '')),
    130, 0, 0, 8900.00, NULL
);
INSERT INTO erp_sales_order_items (id, sales_order_id, product_id, qty, allocated_qty, dispatched_qty, unit_price, note)
VALUES (
    UNHEX(REPLACE('10000001-0001-0001-0001-000000000002', '-', '')),
    UNHEX(REPLACE('11111111-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7700-8000-000000000002', '-', '')),
    50, 0, 0, 15900.00, NULL
);
INSERT INTO erp_sales_order_items (id, sales_order_id, product_id, qty, allocated_qty, dispatched_qty, unit_price, note)
VALUES (
    UNHEX(REPLACE('10000001-0001-0001-0001-000000000003', '-', '')),
    UNHEX(REPLACE('11111111-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7700-8000-000000000004', '-', '')),
    20, 0, 0, 25900.00, NULL
);

-- 8번 수주서 품목 (케이블 + 키보드)
INSERT INTO erp_sales_order_items (id, sales_order_id, product_id, qty, allocated_qty, dispatched_qty, unit_price, note)
VALUES (
    UNHEX(REPLACE('20000001-0001-0001-0001-000000000001', '-', '')),
    UNHEX(REPLACE('22222222-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7700-8000-000000000001', '-', '')),
    60, 0, 0, 8900.00, NULL
);
INSERT INTO erp_sales_order_items (id, sales_order_id, product_id, qty, allocated_qty, dispatched_qty, unit_price, note)
VALUES (
    UNHEX(REPLACE('20000001-0001-0001-0001-000000000002', '-', '')),
    UNHEX(REPLACE('22222222-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7700-8000-000000000003', '-', '')),
    140, 0, 0, 89000.00, NULL
);

-- 9번 수주서 품목 (케이블 + 허브 + 모니터)
INSERT INTO erp_sales_order_items (id, sales_order_id, product_id, qty, allocated_qty, dispatched_qty, unit_price, note)
VALUES (
    UNHEX(REPLACE('30000001-0001-0001-0001-000000000001', '-', '')),
    UNHEX(REPLACE('33333333-cccc-cccc-cccc-cccccccccccc', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7700-8000-000000000001', '-', '')),
    110, 0, 0, 8900.00, NULL
);
INSERT INTO erp_sales_order_items (id, sales_order_id, product_id, qty, allocated_qty, dispatched_qty, unit_price, note)
VALUES (
    UNHEX(REPLACE('30000001-0001-0001-0001-000000000002', '-', '')),
    UNHEX(REPLACE('33333333-cccc-cccc-cccc-cccccccccccc', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7700-8000-000000000002', '-', '')),
    70, 0, 0, 15900.00, NULL
);
INSERT INTO erp_sales_order_items (id, sales_order_id, product_id, qty, allocated_qty, dispatched_qty, unit_price, note)
VALUES (
    UNHEX(REPLACE('30000001-0001-0001-0001-000000000003', '-', '')),
    UNHEX(REPLACE('33333333-cccc-cccc-cccc-cccccccccccc', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7700-8000-000000000005', '-', '')),
    55, 0, 0, 350000.00, NULL
);

-- 10번 수주서 품목 (마우스 + 모니터)
INSERT INTO erp_sales_order_items (id, sales_order_id, product_id, qty, allocated_qty, dispatched_qty, unit_price, note)
VALUES (
    UNHEX(REPLACE('40000001-0001-0001-0001-000000000001', '-', '')),
    UNHEX(REPLACE('44444444-dddd-dddd-dddd-dddddddddddd', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7700-8000-000000000004', '-', '')),
    85, 0, 0, 25900.00, NULL
);
INSERT INTO erp_sales_order_items (id, sales_order_id, product_id, qty, allocated_qty, dispatched_qty, unit_price, note)
VALUES (
    UNHEX(REPLACE('40000001-0001-0001-0001-000000000002', '-', '')),
    UNHEX(REPLACE('44444444-dddd-dddd-dddd-dddddddddddd', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7700-8000-000000000005', '-', '')),
    100, 0, 0, 350000.00, NULL
);


-- =============================================
-- ERP 발주서 더미 데이터 (5건, 입고용)
-- 협력사별로 발주서 생성 (approved 상태 = 입고 가능)
-- =============================================

-- 1번 발주서 (LogiX, USB-C 케이블 + USB 허브)
INSERT INTO erp_purchase_orders (id, client_id, supplier_id, po_no, status, order_date, scheduled_date, note, created_at)
VALUES (
    UNHEX(REPLACE('aa000001-0001-0001-0001-000000000001', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7000-8000-000000000001', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7500-8000-000000000001', '-', '')),
    'PO-TEST-001', 'approved', '2026-04-01', '2026-04-08', NULL, NOW()
);

-- 2번 발주서 (LogiX, USB 허브)
INSERT INTO erp_purchase_orders (id, client_id, supplier_id, po_no, status, order_date, scheduled_date, note, created_at)
VALUES (
    UNHEX(REPLACE('aa000001-0001-0001-0001-000000000002', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7000-8000-000000000001', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7500-8000-000000000001', '-', '')),
    'PO-TEST-002', 'approved', '2026-04-02', '2026-04-09', NULL, NOW()
);

-- 3번 발주서 (TechSupply, 키보드 + 마우스)
INSERT INTO erp_purchase_orders (id, client_id, supplier_id, po_no, status, order_date, scheduled_date, note, created_at)
VALUES (
    UNHEX(REPLACE('aa000001-0001-0001-0001-000000000003', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7000-8000-000000000001', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7500-8000-000000000002', '-', '')),
    'PO-TEST-003', 'approved', '2026-04-03', '2026-04-10', NULL, NOW()
);

-- 4번 발주서 (TechSupply, 마우스)
INSERT INTO erp_purchase_orders (id, client_id, supplier_id, po_no, status, order_date, scheduled_date, note, created_at)
VALUES (
    UNHEX(REPLACE('aa000001-0001-0001-0001-000000000004', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7000-8000-000000000001', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7500-8000-000000000002', '-', '')),
    'PO-TEST-004', 'approved', '2026-04-04', '2026-04-11', NULL, NOW()
);

-- 5번 발주서 (자사, 모니터)
INSERT INTO erp_purchase_orders (id, client_id, supplier_id, po_no, status, order_date, scheduled_date, note, created_at)
VALUES (
    UNHEX(REPLACE('aa000001-0001-0001-0001-000000000005', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7000-8000-000000000001', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7500-8000-000000000099', '-', '')),
    'PO-TEST-005', 'approved', '2026-04-05', '2026-04-12', NULL, NOW()
);

-- =============================================
-- ERP 발주서 품목
-- =============================================

-- 1번 발주서 품목 (USB-C 케이블 200개 + USB 허브 100개)
INSERT INTO erp_purchase_order_items (id, purchase_order_id, product_id, qty, unit_price, note)
VALUES (
    UNHEX(REPLACE('ab000001-0001-0001-0001-000000000001', '-', '')),
    UNHEX(REPLACE('aa000001-0001-0001-0001-000000000001', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7700-8000-000000000001', '-', '')),
    200, 8900.00, NULL
);
INSERT INTO erp_purchase_order_items (id, purchase_order_id, product_id, qty, unit_price, note)
VALUES (
    UNHEX(REPLACE('ab000001-0001-0001-0001-000000000002', '-', '')),
    UNHEX(REPLACE('aa000001-0001-0001-0001-000000000001', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7700-8000-000000000002', '-', '')),
    100, 15900.00, NULL
);

-- 2번 발주서 품목 (USB 허브 150개)
INSERT INTO erp_purchase_order_items (id, purchase_order_id, product_id, qty, unit_price, note)
VALUES (
    UNHEX(REPLACE('ab000001-0001-0001-0001-000000000003', '-', '')),
    UNHEX(REPLACE('aa000001-0001-0001-0001-000000000002', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7700-8000-000000000002', '-', '')),
    150, 15900.00, NULL
);

-- 3번 발주서 품목 (키보드 80개 + 마우스 120개)
INSERT INTO erp_purchase_order_items (id, purchase_order_id, product_id, qty, unit_price, note)
VALUES (
    UNHEX(REPLACE('ab000001-0001-0001-0001-000000000004', '-', '')),
    UNHEX(REPLACE('aa000001-0001-0001-0001-000000000003', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7700-8000-000000000003', '-', '')),
    80, 89000.00, NULL
);
INSERT INTO erp_purchase_order_items (id, purchase_order_id, product_id, qty, unit_price, note)
VALUES (
    UNHEX(REPLACE('ab000001-0001-0001-0001-000000000005', '-', '')),
    UNHEX(REPLACE('aa000001-0001-0001-0001-000000000003', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7700-8000-000000000004', '-', '')),
    120, 25900.00, NULL
);

-- 4번 발주서 품목 (마우스 200개)
INSERT INTO erp_purchase_order_items (id, purchase_order_id, product_id, qty, unit_price, note)
VALUES (
    UNHEX(REPLACE('ab000001-0001-0001-0001-000000000006', '-', '')),
    UNHEX(REPLACE('aa000001-0001-0001-0001-000000000004', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7700-8000-000000000004', '-', '')),
    200, 25900.00, NULL
);

-- 5번 발주서 품목 (모니터 30개)
INSERT INTO erp_purchase_order_items (id, purchase_order_id, product_id, qty, unit_price, note)
VALUES (
    UNHEX(REPLACE('ab000001-0001-0001-0001-000000000007', '-', '')),
    UNHEX(REPLACE('aa000001-0001-0001-0001-000000000005', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7700-8000-000000000005', '-', '')),
    30, 350000.00, NULL
);

-- =============================================
-- ERP 발주서 추가 더미 데이터 (10건, 5~6월 입고 예정)
-- =============================================

-- 6번 발주서 (LogiX, USB-C 케이블)
INSERT INTO erp_purchase_orders (id, client_id, supplier_id, po_no, status, order_date, scheduled_date, note, created_at)
VALUES (
    UNHEX(REPLACE('aa000001-0001-0001-0001-000000000006', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7000-8000-000000000001', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7500-8000-000000000001', '-', '')),
    'PO-TEST-006', 'approved', '2026-04-22', '2026-04-30', NULL, NOW()
);

-- 7번 발주서 (LogiX, USB 허브)
INSERT INTO erp_purchase_orders (id, client_id, supplier_id, po_no, status, order_date, scheduled_date, note, created_at)
VALUES (
    UNHEX(REPLACE('aa000001-0001-0001-0001-000000000007', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7000-8000-000000000001', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7500-8000-000000000001', '-', '')),
    'PO-TEST-007', 'approved', '2026-04-25', '2026-05-05', NULL, NOW()
);

-- 8번 발주서 (TechSupply, 키보드 + 마우스)
INSERT INTO erp_purchase_orders (id, client_id, supplier_id, po_no, status, order_date, scheduled_date, note, created_at)
VALUES (
    UNHEX(REPLACE('aa000001-0001-0001-0001-000000000008', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7000-8000-000000000001', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7500-8000-000000000002', '-', '')),
    'PO-TEST-008', 'approved', '2026-05-01', '2026-05-10', NULL, NOW()
);

-- 9번 발주서 (TechSupply, 마우스)
INSERT INTO erp_purchase_orders (id, client_id, supplier_id, po_no, status, order_date, scheduled_date, note, created_at)
VALUES (
    UNHEX(REPLACE('aa000001-0001-0001-0001-000000000009', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7000-8000-000000000001', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7500-8000-000000000002', '-', '')),
    'PO-TEST-009', 'approved', '2026-05-05', '2026-05-13', NULL, NOW()
);

-- 10번 발주서 (자사, 모니터)
INSERT INTO erp_purchase_orders (id, client_id, supplier_id, po_no, status, order_date, scheduled_date, note, created_at)
VALUES (
    UNHEX(REPLACE('aa000001-0001-0001-0001-000000000010', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7000-8000-000000000001', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7500-8000-000000000099', '-', '')),
    'PO-TEST-010', 'approved', '2026-05-10', '2026-05-18', NULL, NOW()
);

-- 11번 발주서 (LogiX, USB-C 케이블 + USB 허브)
INSERT INTO erp_purchase_orders (id, client_id, supplier_id, po_no, status, order_date, scheduled_date, note, created_at)
VALUES (
    UNHEX(REPLACE('aa000001-0001-0001-0001-000000000011', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7000-8000-000000000001', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7500-8000-000000000001', '-', '')),
    'PO-TEST-011', 'approved', '2026-05-15', '2026-05-22', NULL, NOW()
);

-- 12번 발주서 (TechSupply, 키보드)
INSERT INTO erp_purchase_orders (id, client_id, supplier_id, po_no, status, order_date, scheduled_date, note, created_at)
VALUES (
    UNHEX(REPLACE('aa000001-0001-0001-0001-000000000012', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7000-8000-000000000001', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7500-8000-000000000002', '-', '')),
    'PO-TEST-012', 'approved', '2026-05-20', '2026-05-28', NULL, NOW()
);

-- 13번 발주서 (자사, 모니터)
INSERT INTO erp_purchase_orders (id, client_id, supplier_id, po_no, status, order_date, scheduled_date, note, created_at)
VALUES (
    UNHEX(REPLACE('aa000001-0001-0001-0001-000000000013', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7000-8000-000000000001', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7500-8000-000000000099', '-', '')),
    'PO-TEST-013', 'approved', '2026-05-25', '2026-06-02', NULL, NOW()
);

-- 14번 발주서 (LogiX, USB 허브)
INSERT INTO erp_purchase_orders (id, client_id, supplier_id, po_no, status, order_date, scheduled_date, note, created_at)
VALUES (
    UNHEX(REPLACE('aa000001-0001-0001-0001-000000000014', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7000-8000-000000000001', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7500-8000-000000000001', '-', '')),
    'PO-TEST-014', 'approved', '2026-06-01', '2026-06-08', NULL, NOW()
);

-- 15번 발주서 (TechSupply, 키보드 + 마우스)
INSERT INTO erp_purchase_orders (id, client_id, supplier_id, po_no, status, order_date, scheduled_date, note, created_at)
VALUES (
    UNHEX(REPLACE('aa000001-0001-0001-0001-000000000015', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7000-8000-000000000001', '-', '')),
    UNHEX(REPLACE('01935c00-0000-7500-8000-000000000002', '-', '')),
    'PO-TEST-015', 'approved', '2026-06-05', '2026-06-15', NULL, NOW()
);

-- =============================================
-- 추가 발주서 품목 (6~15번)
-- =============================================

-- 6번 (케이블 400)
INSERT INTO erp_purchase_order_items (id, purchase_order_id, product_id, qty, unit_price, note) VALUES
(UNHEX(REPLACE('ab000001-0001-0001-0001-000000000008','-','')), UNHEX(REPLACE('aa000001-0001-0001-0001-000000000006','-','')), UNHEX(REPLACE('01935c00-0000-7700-8000-000000000001','-','')), 400, 8900.00, NULL);

-- 7번 (허브 250)
INSERT INTO erp_purchase_order_items (id, purchase_order_id, product_id, qty, unit_price, note) VALUES
(UNHEX(REPLACE('ab000001-0001-0001-0001-000000000009','-','')), UNHEX(REPLACE('aa000001-0001-0001-0001-000000000007','-','')), UNHEX(REPLACE('01935c00-0000-7700-8000-000000000002','-','')), 250, 15900.00, NULL);

-- 8번 (키보드 150 + 마우스 200)
INSERT INTO erp_purchase_order_items (id, purchase_order_id, product_id, qty, unit_price, note) VALUES
(UNHEX(REPLACE('ab000001-0001-0001-0001-000000000010','-','')), UNHEX(REPLACE('aa000001-0001-0001-0001-000000000008','-','')), UNHEX(REPLACE('01935c00-0000-7700-8000-000000000003','-','')), 150, 89000.00, NULL),
(UNHEX(REPLACE('ab000001-0001-0001-0001-000000000011','-','')), UNHEX(REPLACE('aa000001-0001-0001-0001-000000000008','-','')), UNHEX(REPLACE('01935c00-0000-7700-8000-000000000004','-','')), 200, 25900.00, NULL);

-- 9번 (마우스 300)
INSERT INTO erp_purchase_order_items (id, purchase_order_id, product_id, qty, unit_price, note) VALUES
(UNHEX(REPLACE('ab000001-0001-0001-0001-000000000012','-','')), UNHEX(REPLACE('aa000001-0001-0001-0001-000000000009','-','')), UNHEX(REPLACE('01935c00-0000-7700-8000-000000000004','-','')), 300, 25900.00, NULL);

-- 10번 (모니터 40)
INSERT INTO erp_purchase_order_items (id, purchase_order_id, product_id, qty, unit_price, note) VALUES
(UNHEX(REPLACE('ab000001-0001-0001-0001-000000000013','-','')), UNHEX(REPLACE('aa000001-0001-0001-0001-000000000010','-','')), UNHEX(REPLACE('01935c00-0000-7700-8000-000000000005','-','')), 40, 350000.00, NULL);

-- 11번 (케이블 600 + 허브 350)
INSERT INTO erp_purchase_order_items (id, purchase_order_id, product_id, qty, unit_price, note) VALUES
(UNHEX(REPLACE('ab000001-0001-0001-0001-000000000014','-','')), UNHEX(REPLACE('aa000001-0001-0001-0001-000000000011','-','')), UNHEX(REPLACE('01935c00-0000-7700-8000-000000000001','-','')), 600, 8900.00, NULL),
(UNHEX(REPLACE('ab000001-0001-0001-0001-000000000015','-','')), UNHEX(REPLACE('aa000001-0001-0001-0001-000000000011','-','')), UNHEX(REPLACE('01935c00-0000-7700-8000-000000000002','-','')), 350, 15900.00, NULL);

-- 12번 (키보드 200)
INSERT INTO erp_purchase_order_items (id, purchase_order_id, product_id, qty, unit_price, note) VALUES
(UNHEX(REPLACE('ab000001-0001-0001-0001-000000000016','-','')), UNHEX(REPLACE('aa000001-0001-0001-0001-000000000012','-','')), UNHEX(REPLACE('01935c00-0000-7700-8000-000000000003','-','')), 200, 89000.00, NULL);

-- 13번 (모니터 70)
INSERT INTO erp_purchase_order_items (id, purchase_order_id, product_id, qty, unit_price, note) VALUES
(UNHEX(REPLACE('ab000001-0001-0001-0001-000000000017','-','')), UNHEX(REPLACE('aa000001-0001-0001-0001-000000000013','-','')), UNHEX(REPLACE('01935c00-0000-7700-8000-000000000005','-','')), 70, 350000.00, NULL);

-- 14번 (허브 450)
INSERT INTO erp_purchase_order_items (id, purchase_order_id, product_id, qty, unit_price, note) VALUES
(UNHEX(REPLACE('ab000001-0001-0001-0001-000000000018','-','')), UNHEX(REPLACE('aa000001-0001-0001-0001-000000000014','-','')), UNHEX(REPLACE('01935c00-0000-7700-8000-000000000002','-','')), 450, 15900.00, NULL);

-- 15번 (키보드 100 + 마우스 180)
INSERT INTO erp_purchase_order_items (id, purchase_order_id, product_id, qty, unit_price, note) VALUES
(UNHEX(REPLACE('ab000001-0001-0001-0001-000000000019','-','')), UNHEX(REPLACE('aa000001-0001-0001-0001-000000000015','-','')), UNHEX(REPLACE('01935c00-0000-7700-8000-000000000003','-','')), 100, 89000.00, NULL),
(UNHEX(REPLACE('ab000001-0001-0001-0001-000000000020','-','')), UNHEX(REPLACE('aa000001-0001-0001-0001-000000000015','-','')), UNHEX(REPLACE('01935c00-0000-7700-8000-000000000004','-','')), 180, 25900.00, NULL);

-- =============================================
-- ERP 수주서 추가 더미 데이터 (15건, 5~6월 출고 예정)
-- 강남지점(7600-0001) / 부산지점(7600-0002)
-- =============================================

-- 11번 수주서 (강남지점)
INSERT INTO erp_sales_orders (id, client_id, store_id, so_no, status, order_date, scheduled_date, shipping_address, note, created_at)
VALUES (UNHEX(REPLACE('50000000-0001-0001-0001-000000000011','-','')),
    UNHEX(REPLACE('01935c00-0000-7000-8000-000000000001','-','')),
    UNHEX(REPLACE('01935c00-0000-7600-8000-000000000001','-','')),
    'SO-TEST-011', 'draft', '2026-04-22', '2026-04-30',
    '서울시 강남구 역삼로 111', NULL, NOW());

-- 12번 수주서 (강남지점)
INSERT INTO erp_sales_orders (id, client_id, store_id, so_no, status, order_date, scheduled_date, shipping_address, note, created_at)
VALUES (UNHEX(REPLACE('50000000-0001-0001-0001-000000000012','-','')),
    UNHEX(REPLACE('01935c00-0000-7000-8000-000000000001','-','')),
    UNHEX(REPLACE('01935c00-0000-7600-8000-000000000001','-','')),
    'SO-TEST-012', 'draft', '2026-04-25', '2026-05-04',
    '서울시 서초구 강남대로 222', NULL, NOW());

-- 13번 수주서 (강남지점)
INSERT INTO erp_sales_orders (id, client_id, store_id, so_no, status, order_date, scheduled_date, shipping_address, note, created_at)
VALUES (UNHEX(REPLACE('50000000-0001-0001-0001-000000000013','-','')),
    UNHEX(REPLACE('01935c00-0000-7000-8000-000000000001','-','')),
    UNHEX(REPLACE('01935c00-0000-7600-8000-000000000001','-','')),
    'SO-TEST-013', 'draft', '2026-05-01', '2026-05-08',
    '서울시 송파구 잠실로 333', NULL, NOW());

-- 14번 수주서 (강남지점)
INSERT INTO erp_sales_orders (id, client_id, store_id, so_no, status, order_date, scheduled_date, shipping_address, note, created_at)
VALUES (UNHEX(REPLACE('50000000-0001-0001-0001-000000000014','-','')),
    UNHEX(REPLACE('01935c00-0000-7000-8000-000000000001','-','')),
    UNHEX(REPLACE('01935c00-0000-7600-8000-000000000001','-','')),
    'SO-TEST-014', 'draft', '2026-05-05', '2026-05-12',
    '서울시 영등포구 여의대로 444', NULL, NOW());

-- 15번 수주서 (강남지점)
INSERT INTO erp_sales_orders (id, client_id, store_id, so_no, status, order_date, scheduled_date, shipping_address, note, created_at)
VALUES (UNHEX(REPLACE('50000000-0001-0001-0001-000000000015','-','')),
    UNHEX(REPLACE('01935c00-0000-7000-8000-000000000001','-','')),
    UNHEX(REPLACE('01935c00-0000-7600-8000-000000000001','-','')),
    'SO-TEST-015', 'draft', '2026-05-08', '2026-05-15',
    '서울시 중구 을지로 555', NULL, NOW());

-- 16번 수주서 (부산지점)
INSERT INTO erp_sales_orders (id, client_id, store_id, so_no, status, order_date, scheduled_date, shipping_address, note, created_at)
VALUES (UNHEX(REPLACE('50000000-0001-0001-0001-000000000016','-','')),
    UNHEX(REPLACE('01935c00-0000-7000-8000-000000000001','-','')),
    UNHEX(REPLACE('01935c00-0000-7600-8000-000000000002','-','')),
    'SO-TEST-016', 'draft', '2026-05-10', '2026-05-18',
    '부산시 해운대구 센텀로 666', NULL, NOW());

-- 17번 수주서 (부산지점)
INSERT INTO erp_sales_orders (id, client_id, store_id, so_no, status, order_date, scheduled_date, shipping_address, note, created_at)
VALUES (UNHEX(REPLACE('50000000-0001-0001-0001-000000000017','-','')),
    UNHEX(REPLACE('01935c00-0000-7000-8000-000000000001','-','')),
    UNHEX(REPLACE('01935c00-0000-7600-8000-000000000002','-','')),
    'SO-TEST-017', 'draft', '2026-05-15', '2026-05-22',
    '부산시 부산진구 중앙대로 777', NULL, NOW());

-- 18번 수주서 (부산지점)
INSERT INTO erp_sales_orders (id, client_id, store_id, so_no, status, order_date, scheduled_date, shipping_address, note, created_at)
VALUES (UNHEX(REPLACE('50000000-0001-0001-0001-000000000018','-','')),
    UNHEX(REPLACE('01935c00-0000-7000-8000-000000000001','-','')),
    UNHEX(REPLACE('01935c00-0000-7600-8000-000000000002','-','')),
    'SO-TEST-018', 'draft', '2026-05-20', '2026-05-28',
    '부산시 동래구 명륜로 888', NULL, NOW());

-- 19번 수주서 (부산지점)
INSERT INTO erp_sales_orders (id, client_id, store_id, so_no, status, order_date, scheduled_date, shipping_address, note, created_at)
VALUES (UNHEX(REPLACE('50000000-0001-0001-0001-000000000019','-','')),
    UNHEX(REPLACE('01935c00-0000-7000-8000-000000000001','-','')),
    UNHEX(REPLACE('01935c00-0000-7600-8000-000000000002','-','')),
    'SO-TEST-019', 'draft', '2026-05-25', '2026-06-01',
    '부산시 남구 수영로 999', NULL, NOW());

-- 20번 수주서 (부산지점)
INSERT INTO erp_sales_orders (id, client_id, store_id, so_no, status, order_date, scheduled_date, shipping_address, note, created_at)
VALUES (UNHEX(REPLACE('50000000-0001-0001-0001-000000000020','-','')),
    UNHEX(REPLACE('01935c00-0000-7000-8000-000000000001','-','')),
    UNHEX(REPLACE('01935c00-0000-7600-8000-000000000002','-','')),
    'SO-TEST-020', 'draft', '2026-05-28', '2026-06-05',
    '부산시 금정구 범어사로 1010', NULL, NOW());

-- 21번 수주서 (강남지점)
INSERT INTO erp_sales_orders (id, client_id, store_id, so_no, status, order_date, scheduled_date, shipping_address, note, created_at)
VALUES (UNHEX(REPLACE('50000000-0001-0001-0001-000000000021','-','')),
    UNHEX(REPLACE('01935c00-0000-7000-8000-000000000001','-','')),
    UNHEX(REPLACE('01935c00-0000-7600-8000-000000000001','-','')),
    'SO-TEST-021', 'draft', '2026-06-01', '2026-06-08',
    '서울시 광진구 능동로 1111', NULL, NOW());

-- 22번 수주서 (강남지점)
INSERT INTO erp_sales_orders (id, client_id, store_id, so_no, status, order_date, scheduled_date, shipping_address, note, created_at)
VALUES (UNHEX(REPLACE('50000000-0001-0001-0001-000000000022','-','')),
    UNHEX(REPLACE('01935c00-0000-7000-8000-000000000001','-','')),
    UNHEX(REPLACE('01935c00-0000-7600-8000-000000000001','-','')),
    'SO-TEST-022', 'draft', '2026-06-05', '2026-06-12',
    '서울시 강동구 천호대로 1212', NULL, NOW());

-- 23번 수주서 (강남지점)
INSERT INTO erp_sales_orders (id, client_id, store_id, so_no, status, order_date, scheduled_date, shipping_address, note, created_at)
VALUES (UNHEX(REPLACE('50000000-0001-0001-0001-000000000023','-','')),
    UNHEX(REPLACE('01935c00-0000-7000-8000-000000000001','-','')),
    UNHEX(REPLACE('01935c00-0000-7600-8000-000000000001','-','')),
    'SO-TEST-023', 'draft', '2026-06-10', '2026-06-18',
    '서울시 노원구 동일로 1313', NULL, NOW());

-- 24번 수주서 (강남지점)
INSERT INTO erp_sales_orders (id, client_id, store_id, so_no, status, order_date, scheduled_date, shipping_address, note, created_at)
VALUES (UNHEX(REPLACE('50000000-0001-0001-0001-000000000024','-','')),
    UNHEX(REPLACE('01935c00-0000-7000-8000-000000000001','-','')),
    UNHEX(REPLACE('01935c00-0000-7600-8000-000000000001','-','')),
    'SO-TEST-024', 'draft', '2026-06-15', '2026-06-22',
    '서울시 성동구 왕십리로 1414', NULL, NOW());

-- 25번 수주서 (강남지점)
INSERT INTO erp_sales_orders (id, client_id, store_id, so_no, status, order_date, scheduled_date, shipping_address, note, created_at)
VALUES (UNHEX(REPLACE('50000000-0001-0001-0001-000000000025','-','')),
    UNHEX(REPLACE('01935c00-0000-7000-8000-000000000001','-','')),
    UNHEX(REPLACE('01935c00-0000-7600-8000-000000000001','-','')),
    'SO-TEST-025', 'draft', '2026-06-20', '2026-06-28',
    '서울시 관악구 관악로 1515', NULL, NOW());

-- =============================================
-- 추가 수주서 품목 (11~25번)
-- =============================================

-- 11번 (케이블 80 + 허브 40)
INSERT INTO erp_sales_order_items (id, sales_order_id, product_id, qty, allocated_qty, dispatched_qty, unit_price, note) VALUES
(UNHEX(REPLACE('50000011-0001-0001-0001-000000000001','-','')), UNHEX(REPLACE('50000000-0001-0001-0001-000000000011','-','')), UNHEX(REPLACE('01935c00-0000-7700-8000-000000000001','-','')), 80, 0, 0, 8900.00, NULL),
(UNHEX(REPLACE('50000011-0001-0001-0001-000000000002','-','')), UNHEX(REPLACE('50000000-0001-0001-0001-000000000011','-','')), UNHEX(REPLACE('01935c00-0000-7700-8000-000000000002','-','')), 40, 0, 0, 15900.00, NULL);

-- 12번 (마우스 100)
INSERT INTO erp_sales_order_items (id, sales_order_id, product_id, qty, allocated_qty, dispatched_qty, unit_price, note) VALUES
(UNHEX(REPLACE('50000012-0001-0001-0001-000000000001','-','')), UNHEX(REPLACE('50000000-0001-0001-0001-000000000012','-','')), UNHEX(REPLACE('01935c00-0000-7700-8000-000000000004','-','')), 100, 0, 0, 25900.00, NULL);

-- 13번 (키보드 50 + 마우스 60)
INSERT INTO erp_sales_order_items (id, sales_order_id, product_id, qty, allocated_qty, dispatched_qty, unit_price, note) VALUES
(UNHEX(REPLACE('50000013-0001-0001-0001-000000000001','-','')), UNHEX(REPLACE('50000000-0001-0001-0001-000000000013','-','')), UNHEX(REPLACE('01935c00-0000-7700-8000-000000000003','-','')), 50, 0, 0, 89000.00, NULL),
(UNHEX(REPLACE('50000013-0001-0001-0001-000000000002','-','')), UNHEX(REPLACE('50000000-0001-0001-0001-000000000013','-','')), UNHEX(REPLACE('01935c00-0000-7700-8000-000000000004','-','')), 60, 0, 0, 25900.00, NULL);

-- 14번 (모니터 12)
INSERT INTO erp_sales_order_items (id, sales_order_id, product_id, qty, allocated_qty, dispatched_qty, unit_price, note) VALUES
(UNHEX(REPLACE('50000014-0001-0001-0001-000000000001','-','')), UNHEX(REPLACE('50000000-0001-0001-0001-000000000014','-','')), UNHEX(REPLACE('01935c00-0000-7700-8000-000000000005','-','')), 12, 0, 0, 350000.00, NULL);

-- 15번 (허브 70 + 케이블 90)
INSERT INTO erp_sales_order_items (id, sales_order_id, product_id, qty, allocated_qty, dispatched_qty, unit_price, note) VALUES
(UNHEX(REPLACE('50000015-0001-0001-0001-000000000001','-','')), UNHEX(REPLACE('50000000-0001-0001-0001-000000000015','-','')), UNHEX(REPLACE('01935c00-0000-7700-8000-000000000002','-','')), 70, 0, 0, 15900.00, NULL),
(UNHEX(REPLACE('50000015-0001-0001-0001-000000000002','-','')), UNHEX(REPLACE('50000000-0001-0001-0001-000000000015','-','')), UNHEX(REPLACE('01935c00-0000-7700-8000-000000000001','-','')), 90, 0, 0, 8900.00, NULL);

-- 16번 (케이블 120 + 허브 60)
INSERT INTO erp_sales_order_items (id, sales_order_id, product_id, qty, allocated_qty, dispatched_qty, unit_price, note) VALUES
(UNHEX(REPLACE('50000016-0001-0001-0001-000000000001','-','')), UNHEX(REPLACE('50000000-0001-0001-0001-000000000016','-','')), UNHEX(REPLACE('01935c00-0000-7700-8000-000000000001','-','')), 120, 0, 0, 8900.00, NULL),
(UNHEX(REPLACE('50000016-0001-0001-0001-000000000002','-','')), UNHEX(REPLACE('50000000-0001-0001-0001-000000000016','-','')), UNHEX(REPLACE('01935c00-0000-7700-8000-000000000002','-','')), 60, 0, 0, 15900.00, NULL);

-- 17번 (마우스 140)
INSERT INTO erp_sales_order_items (id, sales_order_id, product_id, qty, allocated_qty, dispatched_qty, unit_price, note) VALUES
(UNHEX(REPLACE('50000017-0001-0001-0001-000000000001','-','')), UNHEX(REPLACE('50000000-0001-0001-0001-000000000017','-','')), UNHEX(REPLACE('01935c00-0000-7700-8000-000000000004','-','')), 140, 0, 0, 25900.00, NULL);

-- 18번 (키보드 90 + 모니터 18)
INSERT INTO erp_sales_order_items (id, sales_order_id, product_id, qty, allocated_qty, dispatched_qty, unit_price, note) VALUES
(UNHEX(REPLACE('50000018-0001-0001-0001-000000000001','-','')), UNHEX(REPLACE('50000000-0001-0001-0001-000000000018','-','')), UNHEX(REPLACE('01935c00-0000-7700-8000-000000000003','-','')), 90, 0, 0, 89000.00, NULL),
(UNHEX(REPLACE('50000018-0001-0001-0001-000000000002','-','')), UNHEX(REPLACE('50000000-0001-0001-0001-000000000018','-','')), UNHEX(REPLACE('01935c00-0000-7700-8000-000000000005','-','')), 18, 0, 0, 350000.00, NULL);

-- 19번 (허브 110)
INSERT INTO erp_sales_order_items (id, sales_order_id, product_id, qty, allocated_qty, dispatched_qty, unit_price, note) VALUES
(UNHEX(REPLACE('50000019-0001-0001-0001-000000000001','-','')), UNHEX(REPLACE('50000000-0001-0001-0001-000000000019','-','')), UNHEX(REPLACE('01935c00-0000-7700-8000-000000000002','-','')), 110, 0, 0, 15900.00, NULL);

-- 20번 (케이블 220 + 모니터 22)
INSERT INTO erp_sales_order_items (id, sales_order_id, product_id, qty, allocated_qty, dispatched_qty, unit_price, note) VALUES
(UNHEX(REPLACE('50000020-0001-0001-0001-000000000001','-','')), UNHEX(REPLACE('50000000-0001-0001-0001-000000000020','-','')), UNHEX(REPLACE('01935c00-0000-7700-8000-000000000001','-','')), 220, 0, 0, 8900.00, NULL),
(UNHEX(REPLACE('50000020-0001-0001-0001-000000000002','-','')), UNHEX(REPLACE('50000000-0001-0001-0001-000000000020','-','')), UNHEX(REPLACE('01935c00-0000-7700-8000-000000000005','-','')), 22, 0, 0, 350000.00, NULL);

-- 21번 (마우스 170)
INSERT INTO erp_sales_order_items (id, sales_order_id, product_id, qty, allocated_qty, dispatched_qty, unit_price, note) VALUES
(UNHEX(REPLACE('50000021-0001-0001-0001-000000000001','-','')), UNHEX(REPLACE('50000000-0001-0001-0001-000000000021','-','')), UNHEX(REPLACE('01935c00-0000-7700-8000-000000000004','-','')), 170, 0, 0, 25900.00, NULL);

-- 22번 (키보드 120 + 허브 90)
INSERT INTO erp_sales_order_items (id, sales_order_id, product_id, qty, allocated_qty, dispatched_qty, unit_price, note) VALUES
(UNHEX(REPLACE('50000022-0001-0001-0001-000000000001','-','')), UNHEX(REPLACE('50000000-0001-0001-0001-000000000022','-','')), UNHEX(REPLACE('01935c00-0000-7700-8000-000000000003','-','')), 120, 0, 0, 89000.00, NULL),
(UNHEX(REPLACE('50000022-0001-0001-0001-000000000002','-','')), UNHEX(REPLACE('50000000-0001-0001-0001-000000000022','-','')), UNHEX(REPLACE('01935c00-0000-7700-8000-000000000002','-','')), 90, 0, 0, 15900.00, NULL);

-- 23번 (모니터 28 + 케이블 170)
INSERT INTO erp_sales_order_items (id, sales_order_id, product_id, qty, allocated_qty, dispatched_qty, unit_price, note) VALUES
(UNHEX(REPLACE('50000023-0001-0001-0001-000000000001','-','')), UNHEX(REPLACE('50000000-0001-0001-0001-000000000023','-','')), UNHEX(REPLACE('01935c00-0000-7700-8000-000000000005','-','')), 28, 0, 0, 350000.00, NULL),
(UNHEX(REPLACE('50000023-0001-0001-0001-000000000002','-','')), UNHEX(REPLACE('50000000-0001-0001-0001-000000000023','-','')), UNHEX(REPLACE('01935c00-0000-7700-8000-000000000001','-','')), 170, 0, 0, 8900.00, NULL);

-- 24번 (허브 140)
INSERT INTO erp_sales_order_items (id, sales_order_id, product_id, qty, allocated_qty, dispatched_qty, unit_price, note) VALUES
(UNHEX(REPLACE('50000024-0001-0001-0001-000000000001','-','')), UNHEX(REPLACE('50000000-0001-0001-0001-000000000024','-','')), UNHEX(REPLACE('01935c00-0000-7700-8000-000000000002','-','')), 140, 0, 0, 15900.00, NULL);

-- 25번 (마우스 220 + 키보드 70)
INSERT INTO erp_sales_order_items (id, sales_order_id, product_id, qty, allocated_qty, dispatched_qty, unit_price, note) VALUES
(UNHEX(REPLACE('50000025-0001-0001-0001-000000000001','-','')), UNHEX(REPLACE('50000000-0001-0001-0001-000000000025','-','')), UNHEX(REPLACE('01935c00-0000-7700-8000-000000000004','-','')), 220, 0, 0, 25900.00, NULL),
(UNHEX(REPLACE('50000025-0001-0001-0001-000000000002','-','')), UNHEX(REPLACE('50000000-0001-0001-0001-000000000025','-','')), UNHEX(REPLACE('01935c00-0000-7700-8000-000000000003','-','')), 70, 0, 0, 89000.00, NULL);

-- =============================================
-- 작업자 마지막 위치 샘플
-- =============================================
-- 피킹 자동배정은 작업자의 마지막 창고/구역/로케이션을 참고해 가까운 작업자를 우선 배정한다.
-- FK 없이 account-service 사용자 UUID 와 master-service 위치 UUID 를 논리 참조한다.
INSERT INTO worker_last_locations (
    id,
    client_id,
    user_id,
    warehouse_id,
    zone_id,
    zone_code,
    zone_name,
    location_id,
    location_code,
    last_worked_at
) VALUES
(UNHEX(REPLACE('01935c00-0000-9000-8000-000000000001','-','')),
 UNHEX(REPLACE('01935c00-0000-7000-8000-000000000001','-','')),
 UNHEX(REPLACE('01935c00-0000-7200-8000-000000000001','-','')),
 UNHEX(REPLACE('01935c00-0000-7100-8000-000000000004','-','')),
 UNHEX(REPLACE('01935c00-0000-7200-8000-000000000017','-','')),
 'ZN-PUS-ELEC-017',
 '전자기기존',
 UNHEX(REPLACE('01935c00-0000-7400-8000-00000000002b','-','')),
 'LC-RK-ZN-PUS-ELEC-017-LGX-013-01',
 NOW() - INTERVAL 8 MINUTE),
(UNHEX(REPLACE('01935c00-0000-9000-8000-000000000002','-','')),
 UNHEX(REPLACE('01935c00-0000-7000-8000-000000000001','-','')),
 UNHEX(REPLACE('01935c00-0000-8000-8000-000000000011','-','')),
 UNHEX(REPLACE('01935c00-0000-7100-8000-000000000004','-','')),
 UNHEX(REPLACE('01935c00-0000-7200-8000-000000000017','-','')),
 'ZN-PUS-ELEC-017',
 '전자기기존',
 UNHEX(REPLACE('01935c00-0000-7400-8000-00000000002e','-','')),
 'LC-RK-ZN-PUS-ELEC-017-TECH-014-01',
 NOW() - INTERVAL 20 MINUTE),
(UNHEX(REPLACE('01935c00-0000-9000-8000-000000000003','-','')),
 UNHEX(REPLACE('01935c00-0000-7000-8000-000000000001','-','')),
 UNHEX(REPLACE('01935c00-0000-8000-8000-000000000012','-','')),
 UNHEX(REPLACE('01935c00-0000-7100-8000-000000000001','-','')),
 UNHEX(REPLACE('01935c00-0000-7200-8000-000000000014','-','')),
 'ZN-SEL-POWER-014',
 '전원/충전존',
 UNHEX(REPLACE('01935c00-0000-7400-8000-000000000022','-','')),
 'LC-RK-ZN-SEL-POWER-014-PCEL-010-01',
 NOW() - INTERVAL 12 MINUTE),
(UNHEX(REPLACE('01935c00-0000-9000-8000-000000000004','-','')),
 UNHEX(REPLACE('01935c00-0000-7000-8000-000000000001','-','')),
 UNHEX(REPLACE('01935c00-0000-8000-8000-000000000013','-','')),
 UNHEX(REPLACE('01935c00-0000-7100-8000-000000000001','-','')),
 UNHEX(REPLACE('01935c00-0000-7200-8000-000000000002','-','')),
 'ZN-SEL-ELEC-002',
 '전자기기존',
 UNHEX(REPLACE('01935c00-0000-7400-8000-000000000025','-','')),
 'LC-RK-ZN-SEL-ELEC-002-CK-011-01',
 NOW() - INTERVAL 35 MINUTE),
(UNHEX(REPLACE('01935c00-0000-9000-8000-000000000005','-','')),
 UNHEX(REPLACE('01935c00-0000-7000-8000-000000000001','-','')),
 UNHEX(REPLACE('01935c00-0000-8000-8000-000000000014','-','')),
 UNHEX(REPLACE('01935c00-0000-7100-8000-000000000002','-','')),
 UNHEX(REPLACE('01935c00-0000-7200-8000-000000000007','-','')),
 'ZN-ICN-GEN-007',
 '반품보관존',
 UNHEX(REPLACE('01935c00-0000-7400-8000-000000000028','-','')),
 'LC-RK-ZN-ICN-GEN-007-SELF-012-01',
 NOW() - INTERVAL 5 MINUTE)
ON DUPLICATE KEY UPDATE
    warehouse_id = VALUES(warehouse_id),
    zone_id = VALUES(zone_id),
    zone_code = VALUES(zone_code),
    zone_name = VALUES(zone_name),
    location_id = VALUES(location_id),
    location_code = VALUES(location_code),
    last_worked_at = VALUES(last_worked_at);
