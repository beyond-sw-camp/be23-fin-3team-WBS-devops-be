package com.beyond.wbs.inventory.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 로케이션 수용량 초과 예외.
 *
 * 적치 / 이동 / 조정 / 기타입출고 등에서 목적지 location 의 max_capacity 를
 * 초과하는 양을 추가하려 할 때 발생.
 *
 * HTTP 422 (Unprocessable Entity) 로 자동 응답된다.
 * 응답 body 에는 errorCode + message 가 담겨 프론트가 사용자에게 안내 가능.
 */
@Getter
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class LocationCapacityExceededException extends RuntimeException {

    public static final String ERROR_CODE = "LOCATION_CAPACITY_EXCEEDED";

    private final String errorCode = ERROR_CODE;

    public LocationCapacityExceededException(String message) {
        super(message);
    }

    /**
     * 메시지 포맷: "로케이션 수용량 초과 (현재 X/Y, 추가 N 불가)"
     */
    public static LocationCapacityExceededException of(int existingQty, int maxCapacity, int addingQty) {
        String msg = String.format(
                "로케이션 수용량 초과 (현재 %d/%d, 추가 %d 불가)",
                existingQty, maxCapacity, addingQty);
        return new LocationCapacityExceededException(msg);
    }
}
