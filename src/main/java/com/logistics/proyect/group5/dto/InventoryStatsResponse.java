package com.logistics.proyect.group5.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class InventoryStatsResponse {

    long totalProducts;
    long totalUnits;
    BigDecimal totalValue;
    long lowStockProducts;
    long outOfStockProducts;
}
