package com.vietmart.order.client;

import com.vietmart.order.dto.ProductInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceClientRTTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private ProductServiceClientRT productServiceClientRT;

    private static final String PRODUCT_SERVICE_URL = "http://product-service/api/products/{id}";

    @Test
    @DisplayName("Test 1: Gọi thành công (Happy Path) - Trả về thông tin sản phẩm")
    void test_getById_Success() {
        // Arrange
        Long productId = 100L;
        ProductInfo mockProduct = ProductInfo.builder()
                .id(productId)
                .name("Laptop Dell XPS")
                .price(new BigDecimal("25000000"))
                .status("AVAILABLE")
                .build();

        when(restTemplate.getForObject(eq(PRODUCT_SERVICE_URL), eq(ProductInfo.class), eq(productId)))
                .thenReturn(mockProduct);

        // Act
        ProductInfo result = productServiceClientRT.getById(productId);

        // Assert
        assertNotNull(result);
        assertEquals(productId, result.getId());
        assertEquals("Laptop Dell XPS", result.getName());
        assertEquals("AVAILABLE", result.getStatus());

        verify(restTemplate).getForObject(eq(PRODUCT_SERVICE_URL), eq(ProductInfo.class), eq(productId));
    }

    @Test
    @DisplayName("Test 2: Mô phỏng Timeout (ResourceAccessException) - Trả về Fallback DTO")
    void test_getById_Timeout_ReturnsFallback() {
        // Arrange
        Long productId = 200L;
        when(restTemplate.getForObject(eq(PRODUCT_SERVICE_URL), eq(ProductInfo.class), eq(productId)))
                .thenThrow(new ResourceAccessException("Read timed out"));

        // Act
        ProductInfo result = productServiceClientRT.getById(productId);

        // Assert
        assertNotNull(result);
        assertEquals(productId, result.getId());
        assertEquals("Unknown Product (Fallback)", result.getName());
        assertEquals("UNAVAILABLE", result.getStatus());

        verify(restTemplate).getForObject(eq(PRODUCT_SERVICE_URL), eq(ProductInfo.class), eq(productId));
    }

    @Test
    @DisplayName("Test 3 (Bổ sung): Mô phỏng lỗi 404 NOT FOUND - Trả về null")
    void test_getById_NotFound_ReturnsNull() {
        // Arrange
        Long productId = 300L;
        when(restTemplate.getForObject(eq(PRODUCT_SERVICE_URL), eq(ProductInfo.class), eq(productId)))
                .thenThrow(HttpClientErrorException.NotFound.create(null, null, null, null, null));

        // Act
        ProductInfo result = productServiceClientRT.getById(productId);

        // Assert
        assertNull(result);

        verify(restTemplate).getForObject(eq(PRODUCT_SERVICE_URL), eq(ProductInfo.class), eq(productId));
    }
}
