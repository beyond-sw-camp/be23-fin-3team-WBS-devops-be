package com.beyond.wbs.code;

import com.beyond.wbs.code.enums.RegionCode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 공통 코드 생성 유틸
 *
 * 모든 메서드가 static. 인스턴스 생성 불가.
 * 순번(seq)은 호출 측에서 관리 (DB 시퀀스 등).
 *
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * [마스터 코드]
 *   창고:    WH-SEL-NOR-001
 *   구역:    ZN-SEL-ELC-001
 *   랙:     RK-ZN-SEL-ELC-001-LGX-001
 *   로케이션: LC-RK-ZN-SEL-ELC-001-LGX-001-03
 *   상품:    PRD-20260415-001
 *
 * [지시서/전표 번호]
 *   입고:    IB-20260415-00001
 *   출고:    OB-20260415-00001
 *   이동:    TR-20260415-00001
 *   피킹:    PK-20260415-00001
 *   출고전표: DS-20260415-00001
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 */
public final class CodeGenerator {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    // 인스턴스 생성 방지
    private CodeGenerator() {}

    // ============================================================
    // 마스터 코드 생성
    // ============================================================

    /**
     * 창고 코드 생성
     *
     * @param region 지역 코드 (예: SEL)
     * @param warehouseTypeCode 창고 유형 코드 (예: NOR)
     * @param seq 순번
     * @return WH-SEL-NOR-001
     */
    public static String generateWarehouseCode(RegionCode region, String warehouseTypeCode, int seq) {
        return String.format("WH-%s-%s-%03d", region.getCode(), warehouseTypeCode, seq);
    }

    /**
     * 구역 코드 생성
     *
     * @param regionCode   지역 코드 (예: "SEL") — 창고의 지역에서 가져옴
     * @param categoryCode 카테고리 코드 (예: "ELC") — DB의 ProductCategory.code 에서 가져옴
     * @param seq          순번
     * @return ZN-SEL-ELC-001
     */
    public static String generateZoneCode(String regionCode, String categoryCode, int seq) {
        return String.format("ZN-%s-%s-%03d", regionCode, categoryCode, seq);
    }

    /**
     * 랙 코드 생성
     *
     * @param zoneCode   구역 코드 (예: ZN-SEL-ELC-001)
     * @param vendorCode 협력사 코드 (예: LGX)
     * @param seq        순번
     * @return RK-ZN-SEL-ELC-001-LGX-001
     */
    public static String generateRackCode(String zoneCode, String vendorCode, int seq) {
        return String.format("RK-%s-%s-%03d", zoneCode, vendorCode, seq);
    }

    /**
     * 로케이션 코드 생성
     *
     * @param rackCode 랙 코드 (예: RK-ZN-SEL-ELC-001-LGX-001)
     * @param level    층 번호
     * @return LC-RK-ZN-SEL-ELC-001-LGX-001-03
     */
    public static String generateLocationCode(String rackCode, int level) {
        return String.format("LC-%s-%02d", rackCode, level);
    }

    /**
     * 상품 코드 생성
     *
     * @param seq 순번
     * @return PRD-20260415-001
     */
    public static String generateProductCode(int seq) {
        return String.format("PRD-%s-%03d", LocalDate.now().format(DATE_FMT), seq);
    }

    /**
     * 상품 SKU 제안값 생성 — 카테고리별 순번.
     *
     * SKU 는 우리(창고운영자) 내부 식별자 성격이라 협력사에 종속되지 않는다.
     * 같은 상품을 여러 협력사에서 받아도 Product 레코드는 하나로 관리 가능.
     * 협력사 제공 코드는 별도 supplierSku 필드에 저장하여 ASN 매칭용으로 사용.
     *
     * 관리자가 상품 등록 시 "이런 코드 어때?" 로 제안하는 용도.
     * 관리자는 이 값을 그대로 쓰거나 직접 입력한 값으로 덮어쓸 수 있음.
     *
     * @param categoryCode 카테고리 코드 (예: ELC)
     * @param seq          순번
     * @return ELC-001
     */
    public static String generateProductSku(String categoryCode, int seq) {
        return String.format("%s-%03d", categoryCode, seq);
    }

    // ============================================================
    // 지시서 / 전표 번호 생성
    // ============================================================

    /**
     * 입고지시서 번호 생성
     *
     * @param seq 순번
     * @return IB-20260415-00001
     */
    public static String generateInboundOrderNo(int seq) {
        return String.format("IB-%s-%05d", LocalDate.now().format(DATE_FMT), seq);
    }

    /**
     * 출고지시서 번호 생성
     *
     * @param seq 순번
     * @return OB-20260415-00001
     */
    public static String generateOutboundOrderNo(int seq) {
        return String.format("OB-%s-%05d", LocalDate.now().format(DATE_FMT), seq);
    }

    /**
     * 이동지시서 번호 생성
     *
     * @param seq 순번
     * @return TR-20260415-00001
     */
    public static String generateTransferOrderNo(int seq) {
        return String.format("TR-%s-%05d", LocalDate.now().format(DATE_FMT), seq);
    }

    /**
     * 피킹리스트 번호 생성
     *
     * @param seq 순번
     * @return PK-20260415-00001
     */
    public static String generatePickingNo(int seq) {
        return String.format("PK-%s-%05d", LocalDate.now().format(DATE_FMT), seq);
    }

    /**
     * 출고전표 번호 생성
     *
     * @param seq 순번
     * @return DS-20260415-00001
     */
    public static String generateDispatchNo(int seq) {
        return String.format("DS-%s-%05d", LocalDate.now().format(DATE_FMT), seq);
    }

    /**
     * 기타입출고 번호 생성
     *
     * @param seq 순번
     * @return ETC-20260415-00001
     */
    public static String generateEtcInoutNo(int seq) {
        return String.format("ETC-%s-%05d", LocalDate.now().format(DATE_FMT), seq);
    }

    /**
     * 재고실사 번호 생성
     *
     * @param seq 순번
     * @return SC-20260415-00001
     */
    public static String generateStockCountNo(int seq) {
        return String.format("SC-%s-%05d", LocalDate.now().format(DATE_FMT), seq);
    }

    /**
     * 적치 지시서 번호 생성
     *
     * @param seq 순번
     * @return PL-20260415-00001
     */
    public static String generatePlacementNo(int seq) {
        return String.format("PL-%s-%05d", LocalDate.now().format(DATE_FMT), seq);
    }

    /**
     * 입고 전표 번호 생성
     *
     * @param seq 순번
     * @return RC-20260415-00001
     */
    public static String generateReceiptNo(int seq) {
        return String.format("RC-%s-%05d", LocalDate.now().format(DATE_FMT), seq);
    }

    /**
     * 이동 클러스터 번호 생성
     *
     * @param seq 순번
     * @return TC-20260415-00001
     */
    public static String generateTransferClusterNo(int seq) {
        return String.format("TC-%s-%05d", LocalDate.now().format(DATE_FMT), seq);
    }

    // ============================================================
    // 코드 파싱 유틸
    // ============================================================

    /**
     * 창고 코드에서 지역 코드 추출
     *
     * @param warehouseCode 예: WH-SEL-NOR-001
     * @return SEL
     */
    public static String parseRegionFromWarehouseCode(String warehouseCode) {
        // WH-SEL-NOR-001 → split by "-" → [WH, SEL, NOR, 001] → index 1
        String[] parts = warehouseCode.split("-");
        if (parts.length < 4) {
            throw new IllegalArgumentException("잘못된 창고 코드 형식: " + warehouseCode);
        }
        return parts[1];
    }

    /**
     * 창고 코드에서 창고 유형 추출
     *
     * @param warehouseCode 예: WH-SEL-NOR-001
     * @return NOR
     */
    public static String parseTypeFromWarehouseCode(String warehouseCode) {
        // WH-SEL-NOR-001 → split by "-" → [WH, SEL, NOR, 001] → index 2
        String[] parts = warehouseCode.split("-");
        if (parts.length < 4) {
            throw new IllegalArgumentException("잘못된 창고 코드 형식: " + warehouseCode);
        }
        return parts[2];
    }
}
