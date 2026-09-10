package com.vietmart.order.client;

import com.vietmart.order.dto.ProductInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class ProductClientFallbackFactory implements FallbackFactory<ProductClient> {

    @Override
    public ProductClient create(Throwable cause) {
        log.error("ProductClient fallback triggered. Reason: {}", cause.getMessage(), cause);

        return new ProductClient() {
            @Override
            public ProductInfo getById(Long id) {
                log.warn("Fallback triggered for getById({}) due to error: {}", id, cause.getMessage());
                return ProductInfo.builder()
                        .id(id)
                        .name("Unknown Product (Fallback)")
                        .price(null)
                        .status("UNAVAILABLE")
                        .build();
            }

            @Override
            public List<ProductInfo> getAll() {
                log.warn("Fallback triggered for getAll() due to error: {}", cause.getMessage());
                return Collections.emptyList();
            }
        };
    }
}
