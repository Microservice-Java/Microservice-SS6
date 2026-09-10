package com.vietmart.inventory.client;

import com.vietmart.inventory.dto.StockInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockCheckClientTimeoutTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private StockCheckClient stockCheckClient;

    private static final String PRODUCT_STOCK_URL = "http://product-service/api/stock/{pid}";

    @Test
    @DisplayName("Test Timeout: Mô phỏng Server chậm 5s -> Trả về Fallback trong < 3s")
    void test_checkStock_Timeout_ReturnsFallbackWithinTimeoutLimit() {
        // Arrange: Mô phỏng server bị trễ 5 giây và bị cắt bởi Read Timeout
        Long productId = 555L;
        when(restTemplate.getForObject(eq(PRODUCT_STOCK_URL), eq(StockInfo.class), eq(productId)))
                .thenAnswer(invocation -> {
                    // Giả lập trễ 2500ms (quá readTimeout 2000ms) rồi ném ResourceAccessException
                    Thread.sleep(2200);
                    throw new ResourceAccessException("Read timed out after 2000ms");
                });

        // Act: Đo thời gian thực thi của client
        long startTime = System.currentTimeMillis();
        StockInfo result = stockCheckClient.checkStock(productId);
        long elapsedTime = System.currentTimeMillis() - startTime;

        // Assert: Xác nhận phản hồi về trong dưới 3000ms (thay vì 5000ms)
        logTimeAndResult(elapsedTime, result);

        assertTrue(elapsedTime < 3000, "Thời gian phản hồi phải dưới 3000ms, thực tế là: " + elapsedTime + "ms");
        assertNotNull(result);
        assertEquals(productId, result.getProductId());
        assertEquals(0, result.getAvailableQuantity());
        assertEquals("UNAVAILABLE", result.getStatus());
    }

    private void logTimeAndResult(long elapsedTime, StockInfo result) {
        System.out.println("====== TIMEOUT TEST RESULT ======");
        System.out.println("Execution Time: " + elapsedTime + " ms (Target: < 3000 ms)");
        System.out.println("Stock Status: " + result.getStatus());
        System.out.println("Available Quantity: " + result.getAvailableQuantity());
        System.out.println("=================================");
    }
}
