package com.fintech.brokerage.service;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fintech.brokerage.entity.*;
import com.fintech.brokerage.enums.OrderSide;
import com.fintech.brokerage.enums.OrderStatus;
import com.fintech.brokerage.repo.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
public class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    public static final String TRY = "TRY";

    private final OrderRepository orderRepo;
    private final AssetRepository assetRepo;
    private final CustomerRepository customerRepo;
    
    public OrderService(OrderRepository orderRepo, AssetRepository assetRepo, CustomerRepository customerRepo) {
        this.orderRepo = orderRepo;
        this.assetRepo = assetRepo;
        this.customerRepo = customerRepo;
    }

    @Transactional
    public Order create(Customer customer, String assetName, OrderSide side, BigDecimal size, BigDecimal price) {
        log.info("Creating order: customer={}, asset={}, side={}, size={}, price={}", customer.getId(), assetName, side, size, price);
        if (side == OrderSide.BUY) {
            BigDecimal totalCost = price.multiply(size);
            Asset tryAsset = getOrCreateAsset(customer, TRY);
            ensureEnough(tryAsset.getUsableSize(), totalCost, "Insufficient TRY usable balance");
            tryAsset.setUsableSize(tryAsset.getUsableSize().subtract(totalCost));
            assetRepo.save(tryAsset);
        } else {
            Asset asset = getOrCreateAsset(customer, assetName);
            ensureEnough(asset.getUsableSize(), size, "Insufficient asset usable size");
            asset.setUsableSize(asset.getUsableSize().subtract(size));
            assetRepo.save(asset);
        }
        Order order = new Order(customer, assetName, side, size, price, OrderStatus.PENDING);
        return orderRepo.save(order);
    }

    @Transactional
    public void cancel(UUID orderId) {
        Order o = orderRepo.findById(orderId).orElseThrow(() -> new NoSuchElementException("Order not found"));
        if (o.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException("Only PENDING orders can be canceled");
        }
        if (o.getOrderSide() == OrderSide.BUY) {
            BigDecimal total = o.getPrice().multiply(o.getSize());
            Asset tryAsset = getOrCreateAsset(o.getCustomerId(), TRY);
            tryAsset.setUsableSize(tryAsset.getUsableSize().add(total));
            assetRepo.save(tryAsset);
        } else {
            Asset asset = getOrCreateAsset(o.getCustomerId(), o.getAssetName());
            asset.setUsableSize(asset.getUsableSize().add(o.getSize()));
            assetRepo.save(asset);
        }
        o.setStatus(OrderStatus.CANCELED);
        orderRepo.save(o);
        log.info("Canceled order {}", orderId);
    }

    @Transactional
    public void match(UUID orderId) {
        Order o = orderRepo.findById(orderId).orElseThrow(() -> new NoSuchElementException("Order not found"));

        if (o.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException("Only PENDING orders can be matched");
        }

        Customer customer = o.getCustomerId();

        if (o.getOrderSide() == OrderSide.BUY) {
            BigDecimal total = o.getPrice().multiply(o.getSize());

            Asset tryAsset = getOrCreateAsset(customer, TRY);
            tryAsset.setSize(tryAsset.getSize().subtract(total));
            assetRepo.save(tryAsset);

            Asset asset = getOrCreateAsset(customer, o.getAssetName());
            asset.setSize(asset.getSize().add(o.getSize()));
            asset.setUsableSize(asset.getUsableSize().add(o.getSize()));
            assetRepo.save(asset);
        } else {
            BigDecimal total = o.getPrice().multiply(o.getSize());

            Asset asset = getOrCreateAsset(customer, o.getAssetName());
            asset.setSize(asset.getSize().subtract(o.getSize()));
            assetRepo.save(asset);

            Asset tryAsset = getOrCreateAsset(customer, TRY);
            tryAsset.setSize(tryAsset.getSize().add(total));
            tryAsset.setUsableSize(tryAsset.getUsableSize().add(total));
            assetRepo.save(tryAsset);
        }

        o.setStatus(OrderStatus.MATCHED);
        orderRepo.save(o);
    }

    public List<Order> list(Customer customer, Instant from, Instant to, OrderStatus status, String assetName) {
        return orderRepo.search(customer, from, to, status, assetName);
    }

    public List<Asset> listAssets(UUID customerId) {
    	Customer customer = customerRepo.findById(customerId).orElseThrow(() -> new EntityNotFoundException("Customer not found with id: " + customerId));

        return assetRepo.findAllByCustomerId(customer);
    }

    private Asset getOrCreateAsset(Customer customer, String assetName) {
        return assetRepo.findByCustomerIdAndAssetName(customer, assetName)
                .orElseGet(() -> assetRepo.save(new Asset(customer, assetName, BigDecimal.ZERO, BigDecimal.ZERO)));
    }

    private void ensureEnough(BigDecimal available, BigDecimal required, String msg) {
        if (available.compareTo(required) < 0) throw new IllegalStateException(msg);
    }
}
