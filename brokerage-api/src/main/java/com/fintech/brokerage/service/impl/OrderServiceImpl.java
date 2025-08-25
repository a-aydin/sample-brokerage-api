package com.fintech.brokerage.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fintech.brokerage.entity.Asset;
import com.fintech.brokerage.entity.Customer;
import com.fintech.brokerage.entity.Order;
import com.fintech.brokerage.enums.AssetType;
import com.fintech.brokerage.enums.OrderSide;
import com.fintech.brokerage.enums.OrderStatus;
import com.fintech.brokerage.repo.OrderRepository;
import com.fintech.brokerage.service.AssetService;
import com.fintech.brokerage.service.OrderService;

import jakarta.transaction.Transactional;

@Service
public class OrderServiceImpl implements OrderService {
	private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

	private final OrderRepository orderRepo;
	private final AssetService assetService;

	public OrderServiceImpl(OrderRepository orderRepo, AssetService assetService) {
		this.orderRepo = orderRepo;
		this.assetService = assetService;
	}

	@Override
	@Transactional
	public Order create(Customer customer, String assetName, OrderSide side, BigDecimal size, BigDecimal price) {
		log.info("Creating order: customer={}, asset={}, side={}, size={}, price={}", customer.getId(), assetName, side,
				size, price);
		if (side == OrderSide.BUY) {
			BigDecimal totalCost = price.multiply(size);
			Asset tryAsset = assetService.getOrCreateAsset(customer, AssetType.TRY.getSymbol());
			ensureTryAssetEnough(tryAsset.getUsableSize(), totalCost, "Insufficient TRY usable balance");
			tryAsset.setUsableSize(tryAsset.getUsableSize().subtract(totalCost));
			assetService.createOrUpdateAsset(tryAsset);
		} else {
			Asset asset = assetService.getOrCreateAsset(customer, assetName);
			ensureTryAssetEnough(asset.getUsableSize(), size, "Insufficient asset usable size");
			asset.setUsableSize(asset.getUsableSize().subtract(size));
			assetService.createOrUpdateAsset(asset);
		}
		Order order = new Order(customer, assetName, side, size, price, OrderStatus.PENDING);
		return orderRepo.save(order);
	}

	@Override
	@Transactional
	public void cancel(UUID orderId) {
		Order o = orderRepo.findById(orderId).orElseThrow(() -> new NoSuchElementException("Order not found"));
		if (o.getStatus() != OrderStatus.PENDING) {
			throw new IllegalStateException("Only PENDING orders can be canceled");
		}
		if (o.getOrderSide() == OrderSide.BUY) {
			BigDecimal total = o.getPrice().multiply(o.getSize());
			Asset tryAsset = assetService.getOrCreateAsset(o.getCustomerId(), AssetType.TRY.getSymbol());
			tryAsset.setUsableSize(tryAsset.getUsableSize().add(total));
			assetService.createOrUpdateAsset(tryAsset);
		} else {
			Asset asset = assetService.getOrCreateAsset(o.getCustomerId(), o.getAssetName());
			asset.setUsableSize(asset.getUsableSize().add(o.getSize()));
			assetService.createOrUpdateAsset(asset);
		}
		o.setStatus(OrderStatus.CANCELED);
		orderRepo.save(o);
		log.info("Canceled order {}", orderId);
	}

	@Override
	@Transactional
	public void match(UUID orderId) {
		Order o = orderRepo.findById(orderId).orElseThrow(() -> new NoSuchElementException("Order not found"));

		if (o.getStatus() != OrderStatus.PENDING) {
			throw new IllegalStateException("Only PENDING orders can be matched");
		}

		Customer customer = o.getCustomerId();

		if (o.getOrderSide() == OrderSide.BUY) {
			BigDecimal total = o.getPrice().multiply(o.getSize());

			Asset tryAsset = assetService.getOrCreateAsset(customer, AssetType.TRY.getSymbol());
			tryAsset.setSize(tryAsset.getSize().subtract(total));
			assetService.createOrUpdateAsset(tryAsset);

			Asset asset = assetService.getOrCreateAsset(customer, o.getAssetName());
			asset.setSize(asset.getSize().add(o.getSize()));
			asset.setUsableSize(asset.getUsableSize().add(o.getSize()));
			assetService.createOrUpdateAsset(asset);
		} else {
			BigDecimal total = o.getPrice().multiply(o.getSize());

			Asset asset = assetService.getOrCreateAsset(customer, o.getAssetName());
			asset.setSize(asset.getSize().subtract(o.getSize()));
			assetService.createOrUpdateAsset(asset);

			Asset tryAsset = assetService.getOrCreateAsset(customer, AssetType.TRY.getSymbol());
			tryAsset.setSize(tryAsset.getSize().add(total));
			tryAsset.setUsableSize(tryAsset.getUsableSize().add(total));
			assetService.createOrUpdateAsset(tryAsset);
		}

		o.setStatus(OrderStatus.MATCHED);
		orderRepo.save(o);
		log.info("Matched order: customer={}, asset={}, side={}, size={}, price={}", customer.getId(), o.getAssetName(), o.getOrderSide(),
				o.getSize(), o.getPrice());
		
	}

	public List<Order> list(Customer customer, Instant from, Instant to, OrderStatus status, String assetName) {
		return orderRepo.search(customer, from, to, status, assetName);
	}

	private void ensureTryAssetEnough(BigDecimal available, BigDecimal required, String msg) {
		if (available.compareTo(required) < 0)
			throw new IllegalStateException(msg);
	}
}
