package com.vietmart.order.client;

import com.vietmart.order.dto.ProductInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductServiceClientRT {

    private final RestTemplate restTemplate;

    private static final String PRODUCT_SERVICE_URL = "http://product-service/api/products/{id}";

    public ProductInfo getById(Long productId) {
        try {
            log.info("Sending request to product-service for productId: {}", productId);
            return restTemplate.getForObject(PRODUCT_SERVICE_URL, ProductInfo.class, productId);

        } catch (HttpClientErrorException.NotFound e) {
            // Trường hợp 1: Sản phẩm không tồn tại (404 NOT FOUND)
            log.warn("Product not found with id: {}. Response: {}", productId, e.getMessage());
            return null;

        } catch (ResourceAccessException e) {
            // Trường hợp 2: Lỗi kết nối / Timeout (Connect Timeout hoặc Read Timeout)
            log.error("Timeout or connection failure calling product-service for productId: {}. Error: {}", productId, e.getMessage());
            return buildFallbackProductInfo(productId, "UNAVAILABLE");

        } catch (Exception e) {
            // Trường hợp 3: Lỗi hệ thống khác
            log.error("Unexpected error calling product-service for productId: {}. Error: {}", productId, e.getMessage());
            return buildFallbackProductInfo(productId, "ERROR");
        }
    }

    private ProductInfo buildFallbackProductInfo(Long productId, String status) {
        return ProductInfo.builder()
                .id(productId)
                .name("Unknown Product (Fallback)")
                .price(null)
                .status(status)
                .build();
    }
}
