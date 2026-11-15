package com.logistics.proyect.group5.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    @NotEmpty(message = "El pedido debe contener productos")
    @Valid
    private List<OrderItemRequest> items;

    @NotBlank(message = "El mÃ©todo de pago es obligatorio")
    private String paymentMethod;

    @NotNull(message = "Los datos del cliente son obligatorios")
    @Valid
    private CustomerRequest customer;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerRequest {

        @NotBlank(message = "El nombre del cliente es obligatorio")
        private String fullName;

        @NotBlank(message = "El correo electrÃ³nico es obligatorio")
        @Email(message = "El correo electrÃ³nico no tiene un formato vÃ¡lido")
        private String email;

        @NotBlank(message = "El telÃ©fono es obligatorio")
        private String phone;

        @NotBlank(message = "La direcciÃ³n es obligatoria")
        private String address;

        @NotBlank(message = "La ciudad es obligatoria")
        private String city;

        private String notes;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemRequest {

        @NotNull(message = "El ID del producto es obligatorio")
        private UUID productId;

        @NotNull(message = "La cantidad es obligatoria")
        @Min(value = 1, message = "La cantidad debe ser mayor que cero")
        private Integer quantity;
    }
}
