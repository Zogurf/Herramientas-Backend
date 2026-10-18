package com.logistics.proyect.group5.service;

import com.logistics.proyect.group5.dto.TrackingRequest;
import com.logistics.proyect.group5.dto.TrackingResponse;
import com.logistics.proyect.group5.exception.ResourceNotFoundException;
import com.logistics.proyect.group5.model.Order;
import com.logistics.proyect.group5.model.TrackingEvent;
import com.logistics.proyect.group5.repository.OrderRepository;
import com.logistics.proyect.group5.repository.TrackingEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TrackingService {

    private final TrackingEventRepository trackingEventRepository;
    private final OrderRepository orderRepository;

    @Transactional
    public TrackingResponse registerEvent(TrackingRequest request) {
        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado con ID: " + request.getOrderId()));

        TrackingEvent event = TrackingEvent.builder()
                .order(order)
                .status(request.getStatus())
                .location(request.getLocation())
                .description(request.getDescription())
                .build();

        TrackingEvent savedEvent = trackingEventRepository.save(event);
        return mapToResponse(savedEvent);
    }

    @Transactional(readOnly = true)
    public List<TrackingResponse> getTimeline(UUID orderId) {
        if (!orderRepository.existsById(orderId)) {
            throw new ResourceNotFoundException("Pedido no encontrado con ID: " + orderId);
        }

        return trackingEventRepository.findByOrderIdOrderByEventDateDesc(orderId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private TrackingResponse mapToResponse(TrackingEvent event) {
        return TrackingResponse.builder()
                .id(event.getId())
                .orderId(event.getOrder().getId())
                .status(event.getStatus())
                .location(event.getLocation())
                .description(event.getDescription())
                .eventDate(event.getEventDate())
                .build();
    }
}