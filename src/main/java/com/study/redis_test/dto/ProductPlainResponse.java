package com.study.redis_test.dto;

import com.study.redis_test.entity.ProductPlain;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductPlainResponse {
    
    private Long id;
    private String sku;
    private String name;
    private Long priceKrw;
    private Integer stockQty;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public static ProductPlainResponse from(ProductPlain product) {
        return ProductPlainResponse.builder()
                .id(product.getId())
                .sku(product.getSku())
                .name(product.getName())
                .priceKrw(product.getPriceKrw())
                .stockQty(product.getStockQty())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}