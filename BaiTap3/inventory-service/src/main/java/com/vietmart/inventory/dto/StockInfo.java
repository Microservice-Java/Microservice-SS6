package com.vietmart.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockInfo {
    private Long productId;
    private Integer availableQuantity;
    private String status;

    public static StockInfo unavailable(Long productId) {
        return StockInfo.builder()
                .productId(productId)
                .availableQuantity(0)
                .status("UNAVAILABLE")
                .build();
    }
}
