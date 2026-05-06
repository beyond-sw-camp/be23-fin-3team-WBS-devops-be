package com.beyond.wbs.document.instruction.render;

import com.beyond.wbs.document.instruction.domain.InstructionDocumentType;

import java.util.UUID;

/**
 * 지시서 PDF 생성 추상화.
 *
 * 새 docType 추가 시: 이 인터페이스 구현체 1개 + Thymeleaf 템플릿 1개를 추가하면 된다.
 * 컨슈머가 supportedType()을 키로 한 Map<docType, Renderer>로 dispatch한다.
 *
 * @param <T> 템플릿에 바인딩될 도메인 데이터 DTO
 */
public interface InstructionDocumentRenderer<T> {

    InstructionDocumentType supportedType();

    /**
     * 도메인 저장소에서 데이터를 모아 템플릿 모델 DTO로 조립한다.
     * 각 구현체는 자기 도메인 Repository만 의존하면 된다.
     */
    T loadData(UUID sourceId, UUID clientId);

    /**
     * 모델 DTO + 발행 메타로 PDF byte[]를 생성한다.
     * Thymeleaf로 HTML 렌더 → Flying Saucer가 ITextRenderer로 PDF 변환.
     */
    byte[] render(T data, InstructionDocumentRenderContext context);
}
