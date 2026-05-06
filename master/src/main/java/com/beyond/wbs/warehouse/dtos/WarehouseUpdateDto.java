package com.beyond.wbs.warehouse.dtos;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 창고 상세 화면 "정보 수정" 모달 요청 DTO.
 *
 * 모든 필드는 nullable — null 인 필드는 변경하지 않음 (부분 수정 지원).
 * code 는 PK 성격이라 의도적으로 제외.
 */
@Getter
@NoArgsConstructor
public class WarehouseUpdateDto {

    @Size(max = 100)
    private String name;

    @Size(max = 200)
    private String address;

    @Size(max = 50)
    private String managerName;

    @Size(max = 30)
    private String phone;

    private String notes;
}
