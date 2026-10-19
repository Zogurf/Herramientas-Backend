package com.logistics.proyect.group5.service;

import com.logistics.proyect.group5.dto.TrackingRequest;
import com.logistics.proyect.group5.exception.ResourceNotFoundException;
import com.logistics.proyect.group5.model.Order;
import com.logistics.proyect.group5.model.TrackingEvent;
import com.logistics.proyect.group5.repository.OrderRepository;
import com.logistics.proyect.group5.repository.TrackingEventRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TrackingServiceTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final TrackingEventRepository events = mock(TrackingEventRepository.class);
    private final TrackingService service = new TrackingService(events, orders);

    @Test
    void registersAnEventForAnExistingOrder() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder().id(orderId).build();
        TrackingRequest request = new TrackingRequest();
        request.setOrderId(orderId);
        request.setStatus("EN_TRANSITO");
        request.setLocation("Lima");
        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        when(events.save(any(TrackingEvent.class))).thenAnswer(call -> call.getArgument(0));

        var result = service.registerEvent(request);

        assertEquals(orderId, result.getOrderId());
        assertEquals("EN_TRANSITO", result.getStatus());
        assertEquals("Lima", result.getLocation());
    }

    @Test
    void rejectsTimelineForMissingOrder() {
        assertThrows(ResourceNotFoundException.class, () -> service.getTimeline(UUID.randomUUID()));
        verifyNoInteractions(events);
    }

    @Test
    void returnsTimelineForExistingOrder() {
        UUID orderId = UUID.randomUUID();
        when(orders.existsById(orderId)).thenReturn(true);
        when(events.findByOrderIdOrderByEventDateDesc(orderId)).thenReturn(List.of(
                TrackingEvent.builder().order(Order.builder().id(orderId).build())
                        .status("ENTREGADO").build()));

        var timeline = service.getTimeline(orderId);

        assertEquals(1, timeline.size());
        assertEquals(orderId, timeline.getFirst().getOrderId());
        assertEquals("ENTREGADO", timeline.getFirst().getStatus());
    }
}
