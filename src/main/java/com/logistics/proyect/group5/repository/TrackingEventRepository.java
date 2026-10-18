package com.logistics.proyect.group5.repository;

import com.logistics.proyect.group5.model.TrackingEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TrackingEventRepository extends JpaRepository<TrackingEvent, UUID> {
    // Retorna la línea de tiempo ordenada del más reciente al más antiguo
    List<TrackingEvent> findByOrderIdOrderByEventDateDesc(UUID orderId);
}