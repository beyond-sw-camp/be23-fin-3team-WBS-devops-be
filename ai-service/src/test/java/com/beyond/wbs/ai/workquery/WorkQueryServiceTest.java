package com.beyond.wbs.ai.workquery;

import com.beyond.wbs.ai.openai.OpenAiChatGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkQueryServiceTest {

    private StockWorkQueryClient stockWorkQueryClient;
    private OpenAiChatGateway openAiChatGateway;
    private WorkQueryService workQueryService;

    @BeforeEach
    void setUp() {
        openAiChatGateway = mock(OpenAiChatGateway.class);
        stockWorkQueryClient = mock(StockWorkQueryClient.class);
        MasterWorkQueryClient masterWorkQueryClient = mock(MasterWorkQueryClient.class);
        AccountWorkQueryClient accountWorkQueryClient = mock(AccountWorkQueryClient.class);
        workQueryService = new WorkQueryService(
                openAiChatGateway,
                new ObjectMapper(),
                stockWorkQueryClient,
                masterWorkQueryClient,
                accountWorkQueryClient
        );
    }

    @Test
    void sendsMonthRangeForPendingWorkQuestion() {
        when(stockWorkQueryClient.execute(anyString(), anyString(), any()))
                .thenReturn(new StockWorkQueryClient.WorkQueryApiResponse("PENDING_WORK", List.of()));

        workQueryService.ask("5월에 할일 뭐야?", List.of(), null, "client-1", "user-1");

        ArgumentCaptor<StockWorkQueryClient.WorkQueryApiRequest> captor =
                ArgumentCaptor.forClass(StockWorkQueryClient.WorkQueryApiRequest.class);
        verify(stockWorkQueryClient).execute(anyString(), anyString(), captor.capture());
        LocalDate firstDay = LocalDate.of(LocalDate.now().getYear(), 5, 1);
        LocalDate lastDay = firstDay.withDayOfMonth(firstDay.lengthOfMonth());
        assertEquals("PENDING_WORK", captor.getValue().intent());
        assertEquals(firstDay.toString(), captor.getValue().slots().get("date_from"));
        assertEquals(lastDay.toString(), captor.getValue().slots().get("date_to"));
    }

    @Test
    void inventoryLocationUsesStrongPromptAndLlmAnswer() {
        when(stockWorkQueryClient.execute(anyString(), anyString(), any()))
                .thenReturn(new StockWorkQueryClient.WorkQueryApiResponse("INVENTORY_LOCATION", List.of(
                        Map.of(
                                "product_name", "USB-C 충전 케이블 1M",
                                "warehouse_name", "서울중앙창고",
                                "location_code", "LC-RK-ZN-SEL-AUDIO-013-SMAX-029-02",
                                "available_qty", 100,
                                "reserved_qty", 0
                        )
                )));
        when(openAiChatGateway.complete(anyString()))
                .thenReturn("USB-C 충전 케이블 1M은 서울중앙창고의 LC-RK-ZN-SEL-AUDIO-013-SMAX-029-02에 있습니다. 가용 재고는 100개입니다. 다른 위치에는 재고가 없습니다.");

        WorkQueryService.WorkQueryRoute route = new WorkQueryService.WorkQueryRoute(
                "STOCK",
                "INVENTORY_LOCATION",
                Map.of("product", "usb 케이블"),
                1.0,
                "test"
        );
        WorkQueryService.WorkQueryResponse response = workQueryService.askWithRoute(
                "usb 케이블 어디에 있어?", List.of(), null, "client-1", "user-1", route);

        assertEquals("USB-C 충전 케이블 1M은 서울중앙창고의 LC-RK-ZN-SEL-AUDIO-013-SMAX-029-02에 있습니다. 가용 재고는 100개입니다. 다른 위치에는 재고가 없습니다.",
                response.answer());
        verify(stockWorkQueryClient).execute(anyString(), anyString(), any());
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(openAiChatGateway).complete(promptCaptor.capture());
        assertTrue(promptCaptor.getValue().contains("100개가 가용합니다"));
        assertTrue(promptCaptor.getValue().contains("필요한 작업을 진행해야 합니다"));
        assertTrue(promptCaptor.getValue().contains("다른 위치에는 재고가 없습니다."));
        assertTrue(promptCaptor.getValue().contains("available_qty가 0인 행은"));
        assertTrue(promptCaptor.getValue().contains("위치 미지정"));
        assertTrue(promptCaptor.getValue().contains("최근 대화에서 이미 답한 위치를 제외"));
    }
}
