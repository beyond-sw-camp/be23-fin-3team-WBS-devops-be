package com.beyond.wbs.ai.workquery;

import com.beyond.wbs.ai.openai.OpenAiChatGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkQueryServiceTest {

    private StockWorkQueryClient stockWorkQueryClient;
    private WorkQueryService workQueryService;

    @BeforeEach
    void setUp() {
        OpenAiChatGateway openAiChatGateway = mock(OpenAiChatGateway.class);
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
}
