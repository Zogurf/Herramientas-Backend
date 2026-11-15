package com.logistics.proyect.group5.repository;

import com.logistics.proyect.group5.model.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);

    @Query(value = """
            SELECT 'PED-' || LPAD(
                nextval('public.orders_number_seq')::TEXT,
                6,
                '0'
            )
            """, nativeQuery = true)
    String generateOrderNumber();
}
