package com.beyond.wbs.code;

import com.beyond.wbs.code.domain.Sequence;
import com.beyond.wbs.code.repository.SequenceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * sequence 테이블에서 데이터를 꺼내서 쓰는 로직
 * 번호 채번 유틸
 *
 * DB sequences 테이블에서 비관적 락으로 순번을 관리하고,
 * CodeGenerator 의 포맷 메서드로 번호를 생성한다.
 *
 * 왜 락이 필요한가:
 *   동시에 2명이 출고지시서를 승인하면 같은 번호가 나올 수 있음.
 *   비관적 락으로 "한 명이 번호 받을 때까지 다른 사람은 대기"하게 해서 중복 방지.
 *
 * 모든 번호에 날짜(yyyyMMdd)가 포함되며 순번은 5자리 zero padding.
 */
@Component
public class NumberingUtil {

    private final SequenceRepository sequenceRepository;

    @Autowired
    public NumberingUtil(SequenceRepository sequenceRepository) {
        this.sequenceRepository = sequenceRepository;
    }

    /**
     * DB 시퀀스에서 다음 순번을 가져온다 (비관적 락으로 동시성 보호)
     * row 가 없으면 0으로 초기화된 행을 생성한 뒤 1부터 발번.
     */
    @Transactional
    public int nextSeq(String type) {
        Sequence sequence = sequenceRepository.findByTypeWithLock(type)
                .orElseGet(() -> sequenceRepository.save(new Sequence(null, type, 0L)));
        sequence.setCurrentVal(sequence.getCurrentVal() + 1);
        sequenceRepository.save(sequence);
        return sequence.getCurrentVal().intValue();
    }

    // ============================================================
    // 출고
    // ============================================================

    /** 출고지시서 번호: OB-20260415-00001 */
    public String generateOrderNo() {
        return CodeGenerator.generateOutboundOrderNo(nextSeq("order"));
    }

    /** 피킹리스트 번호: PK-20260415-00001 */
    public String generatePickingNo() {
        return CodeGenerator.generatePickingNo(nextSeq("picking"));
    }

    /** 출고전표 번호: DS-20260415-00001 */
    public String generateDispatchNo() {
        return CodeGenerator.generateDispatchNo(nextSeq("dispatch"));
    }

    // ============================================================
    // 입고
    // ============================================================

    /** 입고지시서 번호: IB-20260415-00001 */
    public String generateInboundOrderNo() {
        return CodeGenerator.generateInboundOrderNo(nextSeq("inbound"));
    }

    /** 적치지시서 번호: PL-20260415-00001 */
    public String generatePlacementNo() {
        return CodeGenerator.generatePlacementNo(nextSeq("placement"));
    }

    /** 입고전표 번호: RC-20260415-00001 */
    public String generateReceiptNo() {
        return CodeGenerator.generateReceiptNo(nextSeq("receipt"));
    }

    // ============================================================
    // 이동
    // ============================================================

    /** 이동지시서 번호: TR-20260415-00001 */
    public String generateTransferOrderNo() {
        return CodeGenerator.generateTransferOrderNo(nextSeq("transfer"));
    }

    /** 이동클러스터 번호: TC-20260415-00001 */
    public String generateTransferClusterNo() {
        return CodeGenerator.generateTransferClusterNo(nextSeq("transfer_cluster"));
    }

    // ============================================================
    // 기타
    // ============================================================

    /** 기타입출고 번호: ETC-20260415-00001 */
    public String generateEtcInoutNo() {
        return CodeGenerator.generateEtcInoutNo(nextSeq("etc_inout"));
    }

    /** 재고실사 번호: SC-20260415-00001 */
    public String generateStockCountNo() {
        return CodeGenerator.generateStockCountNo(nextSeq("stock_count"));
    }

    /**
     * 하위 호환용 (기존 generate("transfer", "TR") 호출 대응)
     */
    @Transactional
    public String generate(String type, String prefix) {
        return CodeGenerator.generateTransferOrderNo(nextSeq(type));
    }
}
