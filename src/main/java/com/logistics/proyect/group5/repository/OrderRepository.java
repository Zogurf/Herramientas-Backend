package com.logistics.proyect.group5.repository;

import com.logistics.proyect.group5.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
}
