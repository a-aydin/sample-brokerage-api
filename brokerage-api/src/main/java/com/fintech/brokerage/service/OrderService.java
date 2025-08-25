package com.fintech.brokerage.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fintech.brokerage.entity.Customer;
import com.fintech.brokerage.entity.Order;
import com.fintech.brokerage.enums.OrderSide;
import com.fintech.brokerage.enums.OrderStatus;

public interface OrderService {

	public Order create(Customer customer, String assetName, OrderSide side, BigDecimal size, BigDecimal price);
	public void cancel(UUID orderId);
	public void match(UUID orderId);
	public List<Order> list(Customer customer, Instant from, Instant to, OrderStatus status, String assetName);
}
