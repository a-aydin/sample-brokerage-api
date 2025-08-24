package com.fintech.brokerage.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fintech.brokerage.enums.OrderSide;
import com.fintech.brokerage.enums.OrderStatus;

@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customerId;

    @Column(name = "asset_name", nullable = false)
    private String assetName;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_side", nullable = false)
    private OrderSide orderSide;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal size;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "create_date", nullable = false)
    private Instant createDate;

    protected Order() {}

    public Order(Customer customerId, String assetName, OrderSide orderSide, BigDecimal size, BigDecimal price, OrderStatus status) {
        this.customerId = customerId;
        this.assetName = assetName;
        this.orderSide = orderSide;
        this.size = size;
        this.price = price;
        this.status = status;
        this.createDate = Instant.now();
    }

    public UUID getId() { return id; }
    
    public Customer getCustomerId() { return customerId; }
    public void setCustomerId(Customer customerId) { this.customerId = customerId; }
    
    public String getAssetName() { return assetName; }
    public OrderSide getOrderSide() { return orderSide; }
    
    public BigDecimal getSize() { return size; }
    public BigDecimal getPrice() { return price; }
    
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    
    public Instant getCreateDate() { return createDate; }


}
