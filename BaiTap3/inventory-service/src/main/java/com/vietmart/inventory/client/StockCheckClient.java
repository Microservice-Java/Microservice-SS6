package com.vietmart.inventory.client;

import com.vietmart.inventory.dto.StockInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class StockCheckClient {

    private final RestTemplate restTemplate;

    private static final String PRODUCT_STOCK_URL = "http://product-service/api/stock/{pid}";

    public StockInfo checkStock(Long productId) {
        try {
            log.info("Checking stock from product-service for productId: {}", productId);
            return restTemplate.getForObject(PRODUCT_STOCK_URL, StockInfo.class, productId);

        } catch (ResourceAccessException e) {
            log.error("Timeout or network failure when checking stock for productId: {}. Error: {}", productId, e.getMessage());
            return StockInfo.unavailable(productId);

        } catch (Exception e) {
            log.error("Unexpected error when checking stock for productId: {}. Error: {}", productId, e.getMessage());
            return StockInfo.unavailable(productId);
        }
    }
}
