package com.logistics.proyect.group5.repository;

import java.math.BigDecimal;

public interface InventoryStatsProjection {
    Long getTotalProducts();
    Long getTotalUnits();
    BigDecimal getTotalValue();
    Long getLowStockProducts();
    Long getOutOfStockProducts();
}
