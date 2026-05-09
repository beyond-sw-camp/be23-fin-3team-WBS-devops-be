package com.beyond.wbs.aiquery.service;

import com.beyond.wbs.aiquery.controller.AiWorkQueryController.WorkQueryApiRequest;
import com.beyond.wbs.aiquery.controller.AiWorkQueryController.WorkQueryApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiWorkQueryService {

    private static final int DEFAULT_LIMIT = 8;

    private final JdbcTemplate jdbcTemplate;

    public WorkQueryApiResponse execute(String clientId, String userId, WorkQueryApiRequest request) {
        long startedAt = System.nanoTime();
        Intent intent = parseIntent(request == null ? null : request.intent());
        Map<String, String> slots = request == null || request.slots() == null ? Map.of() : request.slots();
        int limit = normalizeLimit(request == null ? 0 : request.limit());

        List<Map<String, Object>> rows = switch (intent) {
            case USER_INFO -> queryUsers(clientId, keyword(slots), limit);
            case ROLE_INFO -> queryRoles(clientId, keyword(slots), limit);
            case CLIENT_INFO -> queryClients(keyword(slots), limit);
        };

        log.info("[AI_ACCOUNT_WORK_QUERY_API] intent={}, rows={}, elapsedMs={}, clientId={}, userId={}, slots={}",
                intent, rows.size(), elapsedMs(startedAt), mask(clientId), mask(userId), slots);
        return new WorkQueryApiResponse(intent.name(), rows);
    }

    private List<Map<String, Object>> queryUsers(String clientId, String keyword, int limit) {
        String like = like(keyword);
        return jdbcTemplate.queryForList("""
                SELECT u.name AS user_name,
                       u.is_active,
                       u.is_developer,
                       r.name AS role_name,
                       r.code AS role_code,
                       c.name AS client_name
                FROM `user` u
                LEFT JOIN role r ON r.id = u.role_id
                LEFT JOIN client c ON c.id = u.client_id
                WHERE (? IS NULL OR u.client_id = UNHEX(REPLACE(?, '-', '')))
                  AND (? = '%%' OR u.name LIKE ? OR u.login_id LIKE ? OR r.name LIKE ?)
                ORDER BY u.name ASC
                LIMIT ?
                """, blankToNull(clientId), blankToNull(clientId), like, like, like, like, limit);
    }

    private List<Map<String, Object>> queryRoles(String clientId, String keyword, int limit) {
        String like = like(keyword);
        return jdbcTemplate.queryForList("""
                SELECT r.name AS role_name,
                       r.code AS role_code,
                       r.description,
                       r.is_active,
                       r.is_system,
                       c.name AS client_name,
                       COUNT(rp.id) AS permission_count
                FROM role r
                LEFT JOIN client c ON c.id = r.client_id
                LEFT JOIN role_permission rp ON rp.role_id = r.id
                WHERE (? IS NULL OR r.client_id = UNHEX(REPLACE(?, '-', '')))
                  AND (? = '%%' OR r.name LIKE ? OR r.code LIKE ? OR r.description LIKE ?)
                GROUP BY r.id, r.name, r.code, r.description, r.is_active, r.is_system, c.name
                ORDER BY r.name ASC
                LIMIT ?
                """, blankToNull(clientId), blankToNull(clientId), like, like, like, like, limit);
    }

    private List<Map<String, Object>> queryClients(String keyword, int limit) {
        String like = like(keyword);
        return jdbcTemplate.queryForList("""
                SELECT name AS client_name,
                       biz_no,
                       is_active,
                       created_at,
                       updated_at
                FROM client
                WHERE ? = '%%' OR name LIKE ? OR biz_no LIKE ?
                ORDER BY name ASC
                LIMIT ?
                """, like, like, like, limit);
    }

    private Intent parseIntent(String value) {
        if (value == null || value.isBlank()) {
            return Intent.USER_INFO;
        }
        try {
            return Intent.valueOf(value);
        } catch (IllegalArgumentException e) {
            return Intent.USER_INFO;
        }
    }

    private String keyword(Map<String, String> slots) {
        return firstNonBlank(slots.get("keyword"), slots.get("product"), slots.get("warehouse"));
    }

    private String like(String value) {
        return "%" + (value == null ? "" : value.trim()) + "%";
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, 20);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String mask(String value) {
        if (value == null || value.isBlank() || value.length() < 8) {
            return value;
        }
        return value.substring(0, 8) + "...";
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private enum Intent {
        USER_INFO,
        ROLE_INFO,
        CLIENT_INFO
    }
}
