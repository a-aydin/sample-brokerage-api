package com.fintech.brokerage.controller.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

import com.fintech.brokerage.enums.OrderSide;

public class CreateOrderRequest {

    @NotNull(message = "Customer ID cannot be null")
    private final UUID customerId;

    @NotBlank(message = "Asset name cannot be blank")
    private final String assetName;

    @NotNull(message = "Order side cannot be null")
    private final OrderSide side;

    @NotNull(message = "Size cannot be null")
    @DecimalMin(value = "0.0001", inclusive = true, message = "Size must be greater than 0")
    private final BigDecimal size;

    @NotNull(message = "Price cannot be null")
    @DecimalMin(value = "0.0001", inclusive = true, message = "Price must be greater than 0")
    private final BigDecimal price;

    // Constructor for immutability and deserialization
    public CreateOrderRequest(UUID customerId, String assetName, OrderSide side, BigDecimal size, BigDecimal price) {
        this.customerId = customerId;
        this.assetName = assetName;
        this.side = side;
        this.size = size;
        this.price = price;
    }

    // Getter only, immutable DTO
    public UUID getCustomerId() { return customerId; }
    public String getAssetName() { return assetName; }
    public OrderSide getSide() { return side; }
    public BigDecimal getSize() { return size; }
    public BigDecimal getPrice() { return price; }

    // Optional: Builder pattern for easier construction in tests or future enhancements
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID customerId;
        private String assetName;
        private OrderSide side;
        private BigDecimal size;
        private BigDecimal price;

        public Builder customerId(UUID customerId) { this.customerId = customerId; return this; }
        public Builder assetName(String assetName) { this.assetName = assetName; return this; }
        public Builder side(OrderSide side) { this.side = side; return this; }
        public Builder size(BigDecimal size) { this.size = size; return this; }
        public Builder price(BigDecimal price) { this.price = price; return this; }

        public CreateOrderRequest build() {
            return new CreateOrderRequest(customerId, assetName, side, size, price);
        }
    }
}
