package com.vietmart.order.client;

import com.vietmart.order.dto.ProductInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductClientFallbackFactoryTest {

    private ProductClientFallbackFactory fallbackFactory;

    @BeforeEach
    void setUp() {
        fallbackFactory = new ProductClientFallbackFactory();
    }

    @Test
    @DisplayName("Test Fallback getById: Trả về DTO dự phòng khi có ngoại lệ")
    void test_fallback_getById() {
        // Arrange
        RuntimeException cause = new RuntimeException("Service Unavailable Timeout");
        ProductClient fallbackClient = fallbackFactory.create(cause);

        // Act
        ProductInfo fallbackResult = fallbackClient.getById(99L);

        // Assert
        assertNotNull(fallbackResult);
        assertEquals(99L, fallbackResult.getId());
        assertEquals("Unknown Product (Fallback)", fallbackResult.getName());
        assertEquals("UNAVAILABLE", fallbackResult.getStatus());
    }

    @Test
    @DisplayName("Test Fallback getAll: Trả về danh sách rỗng khi có ngoại lệ")
    void test_fallback_getAll() {
        // Arrange
        RuntimeException cause = new RuntimeException("Connection Refused");
        ProductClient fallbackClient = fallbackFactory.create(cause);

        // Act
        List<ProductInfo> result = fallbackClient.getAll();

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
