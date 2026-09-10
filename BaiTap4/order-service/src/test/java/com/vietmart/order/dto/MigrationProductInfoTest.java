package com.vietmart.order.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MigrationProductInfoTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Test Deserialization Format Cũ: JSON chứa trường 'name'")
    void test_deserialize_legacy_json_format() throws Exception {
        // Arrange: Chuỗi JSON format v1 từ product-service cũ
        String legacyJson = """
                {
                    "id": 101,
                    "name": "Tai nghe Bluetooth Sony",
                    "price": 2500000
                }
                """;

        // Act
        MigrationProductInfo productInfo = objectMapper.readValue(legacyJson, MigrationProductInfo.class);

        // Assert: Xác nhận trường name đọc được giá trị "Tai nghe Bluetooth Sony" (Không bị null)
        assertNotNull(productInfo);
        assertEquals(101L, productInfo.getId());
        assertEquals("Tai nghe Bluetooth Sony", productInfo.getName());
    }

    @Test
    @DisplayName("Test Deserialization Format Mới: JSON chứa trường 'productName'")
    void test_deserialize_new_json_format() throws Exception {
        // Arrange: Chuỗi JSON format v2 từ product-service mới
        String newJson = """
                {
                    "id": 101,
                    "productName": "Tai nghe Bluetooth Sony",
                    "price": 2500000
                }
                """;

        // Act
        MigrationProductInfo productInfo = objectMapper.readValue(newJson, MigrationProductInfo.class);

        // Assert: Xác nhận @JsonAlias giúp Jackson map "productName" vào biến "name" thành công (Không bị null)
        assertNotNull(productInfo);
        assertEquals(101L, productInfo.getId());
        assertEquals("Tai nghe Bluetooth Sony", productInfo.getName());
    }
}
