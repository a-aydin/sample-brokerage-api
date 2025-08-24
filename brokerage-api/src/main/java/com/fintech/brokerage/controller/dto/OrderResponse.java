package com.fintech.brokerage.controller.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fintech.brokerage.entity.*;
import com.fintech.brokerage.enums.OrderSide;
import com.fintech.brokerage.enums.OrderStatus;

public class OrderResponse {
	
    private UUID id;
    private UUID customerId;
    private String assetName;
    private OrderSide side;
    private BigDecimal size;
    private BigDecimal price;
    private OrderStatus status;
    
    private Instant createDate;
    

    public OrderResponse(Order o) {
        this.id = o.getId();
        
        //The API requirements specified that customer information should be kept under the name customerId, so it was named that way.
        //However, naming the column customer and using customer.getId() would have been better.  
        this.customerId = o.getCustomerId().getId();
        
        this.assetName = o.getAssetName();
        this.side = o.getOrderSide();
        this.size = o.getSize();
        this.price = o.getPrice();
        this.status = o.getStatus();
        this.createDate = o.getCreateDate();
    }

    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public String getAssetName() { return assetName; }
    public OrderSide getSide() { return side; }
    public BigDecimal getSize() { return size; }
    public BigDecimal getPrice() { return price; }
    public OrderStatus getStatus() { return status; }
    public Instant getCreateDate() { return createDate; }
}