package com.logistics.proyect.group5.service;

import com.logistics.proyect.group5.dto.CreateOrderRequest;
import com.logistics.proyect.group5.dto.OrderResponse;
import com.logistics.proyect.group5.model.Order;
import com.logistics.proyect.group5.model.OrderItem;
import com.logistics.proyect.group5.model.Product;
import com.logistics.proyect.group5.model.User;
import com.logistics.proyect.group5.repository.OrderRepository;
import com.logistics.proyect.group5.repository.ProductRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request, User user) {

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("El pedido debe contener productos");
        }

        String paymentMethod = request.getPaymentMethod()
                .trim()
                .toUpperCase(Locale.ROOT);

        Set<String> allowedMethods = Set.of(
                "TARJETA", "YAPE", "EFECTIVO", "TRANSFERENCIA"
        );

        if (!allowedMethods.contains(paymentMethod)) {
            throw new IllegalArgumentException("Método de pago no válido");
        }

        Set<UUID> productIds = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;

        Order order = Order.builder()
                .user(user)
                .orderNumber(orderRepository.generateOrderNumber())
                .total(BigDecimal.ZERO)
                .status("PENDING")
                .paymentMethod(paymentMethod)
                .paymentStatus("PENDING")
                .items(new ArrayList<>())
                .build();

        for (CreateOrderRequest.OrderItemRequest itemRequest : request.getItems()) {

            if (!productIds.add(itemRequest.getProductId())) {
                throw new IllegalArgumentException(
                        "El pedido contiene un producto repetido"
                );
            }

            Product product = productRepository.findByIdForUpdate(itemRequest.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No existe el producto: " + itemRequest.getProductId()
                    ));

            if (!product.isActive()) {
                throw new IllegalArgumentException(
                        "El producto no está disponible: " + product.getName()
                );
            }

            if (product.getStock() < itemRequest.getQuantity()) {
                throw new IllegalArgumentException(
                        "Stock insuficiente para: " + product.getName()
                );
            }

            BigDecimal unitPrice = product.getPrice();
            BigDecimal subtotal = unitPrice.multiply(
                    BigDecimal.valueOf(itemRequest.getQuantity())
            );

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .quantity(itemRequest.getQuantity())
                    .unitPrice(unitPrice)
                    .subtotal(subtotal)
                    .build();

            order.getItems().add(orderItem);
            total = total.add(subtotal);
        }

        order.setTotal(total);

        Order savedOrder = orderRepository.save(order);

        return toResponse(savedOrder);
    }

    @Transactional
    public OrderResponse confirmPayment(UUID orderId) {

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el pedido: " + orderId
                ));

        // Si ya se confirmó, no volver a descontar el stock.
        if ("PAID".equalsIgnoreCase(order.getPaymentStatus())) {
            return toResponse(order);
        }

        if (!"PENDING".equalsIgnoreCase(order.getPaymentStatus())
                || !"PENDING".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalArgumentException(
                    "El pedido no está pendiente de confirmación"
            );
        }

        // Bloquear productos en orden estable para reducir conflictos.
        List<OrderItem> items = order.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getProduct().getId()))
                .toList();

        for (OrderItem item : items) {

            Product product = productRepository.findByIdForUpdate(
                    item.getProduct().getId()
            ).orElseThrow(() -> new IllegalArgumentException(
                    "No existe el producto: " + item.getProduct().getId()
            ));

            if (!product.isActive()) {
                throw new IllegalArgumentException(
                        "El producto no está disponible: " + product.getName()
                );
            }

            if (product.getStock() < item.getQuantity()) {
                throw new IllegalArgumentException(
                        "Stock insuficiente para: " + product.getName()
                );
            }

            product.setStock(product.getStock() - item.getQuantity());
            productRepository.save(product);
        }

        order.setPaymentStatus("PAID");
        order.setStatus("CONFIRMED");

        Order confirmedOrder = orderRepository.save(order);

        return toResponse(confirmedOrder);
    }

    private OrderResponse toResponse(Order order) {

        List<OrderResponse.OrderItemResponse> items = order.getItems()
                .stream()
                .map(item -> OrderResponse.OrderItemResponse.builder()
                        .productId(item.getProduct().getId())
                        .productName(item.getProduct().getName())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .subtotal(item.getSubtotal())
                        .build())
                .toList();

        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .total(order.getTotal())
                .status(order.getStatus())
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus())
                .createdAt(order.getCreatedAt())
                .items(items)
                .build();
    }
}
