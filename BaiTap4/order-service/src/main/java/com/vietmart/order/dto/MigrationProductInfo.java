package com.vietmart.order.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigrationProductInfo {

    private Long id;

    // Sử dụng @JsonAlias để chấp nhận CẢ "name" (format cũ) lẫn "productName" (format mới)
    @JsonAlias({"productName", "name"})
    private String name;

    private BigDecimal price;
}
