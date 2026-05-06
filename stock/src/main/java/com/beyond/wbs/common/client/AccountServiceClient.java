package com.beyond.wbs.common.client;

import com.beyond.wbs.common.client.dto.ClientResDto;
import com.beyond.wbs.common.client.dto.AccountUserListResDto;
import com.beyond.wbs.common.client.dto.UserResDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;
import java.util.List;

/**
 * Account 모듈 Feign Client
 *
 * stock 모듈에서 account 모듈의 API를 호출할 때 사용.
 */
@FeignClient(name = "account-service")
public interface AccountServiceClient {

    @GetMapping("/admin/users/{userId}")
    UserResDto getUser(@PathVariable("userId") UUID userId,
                       @RequestHeader("X-User-Id") String adminId);

    @GetMapping("/admin/users")
    List<AccountUserListResDto> getUsers(@RequestHeader("X-User-Id") String adminId);

    /**
     * 회사(Client) 정보 조회 — 지시서 PDF의 헤더(회사명/사업자번호)에 사용.
     *
     * account-service의 /developer/clients/{id}는 원래 DEVELOPER 권한 필요하지만
     * Feign이 lb:// 로 게이트웨이를 우회하므로 service-to-service 호출은 통과한다.
     * 응답 본체에 users 리스트가 포함되어도 stock의 ClientResDto는 무시한다.
     */
    @GetMapping("/developer/clients/{clientId}")
    ClientResDto getClient(@PathVariable("clientId") UUID clientId);
}
