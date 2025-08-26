package com.fintech.brokerage.service.impl;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fintech.brokerage.entity.Asset;
import com.fintech.brokerage.entity.Customer;
import com.fintech.brokerage.entity.Order;
import com.fintech.brokerage.enums.AssetType;
import com.fintech.brokerage.enums.OrderSide;
import com.fintech.brokerage.enums.OrderStatus;
import com.fintech.brokerage.repo.OrderRepository;
import com.fintech.brokerage.service.AssetService;
import com.fintech.brokerage.service.OrderService;


@Service
public class OrderServiceImpl implements OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final OrderRepository orderRepo;
    private final AssetService assetService;

    public OrderServiceImpl(OrderRepository orderRepo, AssetService assetService) {
        this.orderRepo = orderRepo;
        this.assetService = assetService;
    }

    private static final int MAX_RANGE_DAYS = 365;
    private static final int DEFAULT_RANGE_DAYS = 30;

    @Override
    @Transactional
    public Order create(Customer customer, String assetName, OrderSide side, BigDecimal size, BigDecimal price) {
        // --- Validate inputs early (defensive programming) ---
        validateNewOrder(assetName, side, size, price);

        log.info("Creating order: customer={}, asset={}, side={}, size={}, price={}",
                 customer.getId(), assetName, side, size, price);

        if (side == OrderSide.BUY) {
            BigDecimal totalCost = price.multiply(size); // consider global scale policy
            Asset tryAsset = assetService.getOrCreateAsset(customer, AssetType.TRY.getSymbol());
            ensureEnough(tryAsset.getUsableSize(), totalCost, "Insufficient TRY usable balance");
            tryAsset.subFromUsable(totalCost);
            assetService.createOrUpdateAsset(tryAsset);
        } else {
            Asset asset = assetService.getOrCreateAsset(customer, assetName);
            ensureEnough(asset.getUsableSize(), size, "Insufficient asset usable size");
            asset.subFromUsable(size);
            assetService.createOrUpdateAsset(asset);
        }

        Order order = new Order(customer, assetName, side, size, price, OrderStatus.PENDING);
        return orderRepo.save(order);
    }

    @Override
    @Transactional
    public void cancel(UUID orderId) {
        Order o = orderRepo.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found"));

        // Atomically flip PENDING -> CANCELED. If changed==1, THIS call performs the refund exactly once.
        int changed = orderRepo.cancelIfPending(orderId);

        if (changed == 1) {
            if (o.getOrderSide() == OrderSide.BUY) {
                BigDecimal total = o.getPrice().multiply(o.getSize());
                Asset tryAsset = assetService.getOrCreateAsset(o.getCustomerId(), AssetType.TRY.getSymbol());
                tryAsset.addToUsable(total);
                assetService.createOrUpdateAsset(tryAsset);
            } else {
                Asset asset = assetService.getOrCreateAsset(o.getCustomerId(), o.getAssetName());
                asset.addToUsable(o.getSize());
                assetService.createOrUpdateAsset(asset);
            }
            log.info("Canceled order {}", orderId);
            return; // idempotent success
        }

        OrderStatus cur = orderRepo.findById(orderId)
                .map(Order::getStatus)
                .orElseThrow(() -> new NoSuchElementException("Order not found"));

        if (cur == OrderStatus.CANCELED) {
            log.info("Cancel no-op; order {} already CANCELED", orderId);
            return; // safe no-op
        }

        throw new IllegalStateException("Only PENDING orders can be canceled");
    }

    @Override
    @Transactional
    public void match(UUID orderId) {
        Order o = orderRepo.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found"));

        int changed = orderRepo.matchIfPending(orderId);
        if (changed == 0) {
            // Someone else canceled/matched already
            OrderStatus cur = orderRepo.findById(orderId)
                    .map(Order::getStatus)
                    .orElseThrow(() -> new NoSuchElementException("Order not found"));

            if (cur == OrderStatus.MATCHED) {
                log.info("Match no-op; order {} already MATCHED", orderId);
                return;
            }
            throw new IllegalStateException("Only PENDING orders can be matched");
        }

        Customer customer = o.getCustomerId();
        if (o.getOrderSide() == OrderSide.BUY) {
            BigDecimal total = o.getPrice().multiply(o.getSize());

            Asset tryAsset = assetService.getOrCreateAsset(customer, AssetType.TRY.getSymbol());
            tryAsset.subFromTotal(total); // money leaves total TRY balance
            assetService.createOrUpdateAsset(tryAsset);

            Asset asset = assetService.getOrCreateAsset(customer, o.getAssetName());
            asset.addToTotal(o.getSize());
            asset.addToUsable(o.getSize()); // newly acquired shares are usable
            assetService.createOrUpdateAsset(asset);
        } else {
            BigDecimal total = o.getPrice().multiply(o.getSize());

            Asset asset = assetService.getOrCreateAsset(customer, o.getAssetName());
            asset.subFromTotal(o.getSize());
            assetService.createOrUpdateAsset(asset);

            Asset tryAsset = assetService.getOrCreateAsset(customer, AssetType.TRY.getSymbol());
            tryAsset.addToTotal(total);
            tryAsset.addToUsable(total);
            assetService.createOrUpdateAsset(tryAsset);
        }

        log.info("Matched order: customer={}, asset={}, side={}, size={}, price={}",
                customer.getId(), o.getAssetName(), o.getOrderSide(), o.getSize(), o.getPrice());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> list(Customer customer,
                            Instant from,
                            Instant to,
                            OrderStatus status,
                            String assetName) {

        Objects.requireNonNull(customer, "customer must not be null");

        // Defaults for time range
        Instant now = Instant.now();
        Instant safeTo   = (to   == null) ? now : to;
        Instant safeFrom = (from == null) ? safeTo.minus(DEFAULT_RANGE_DAYS, ChronoUnit.DAYS) : from;

        if (safeFrom.isAfter(safeTo)) {
            
            throw new IllegalArgumentException("'from' must be before or equal to 'to'");
        }

        long days = Duration.between(safeFrom, safeTo).toDays();
        if (days > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException(
                "Date range is too large. Max allowed: " + MAX_RANGE_DAYS + " days");
        }

        String safeAsset = (assetName == null || assetName.isBlank()) ? null : assetName.trim();

        return orderRepo.search(customer, safeFrom, safeTo, status, safeAsset);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Order> list(Customer customer,
                            Instant from,
                            Instant to,
                            OrderStatus status,
                            String assetName,
                            Pageable pageable) {
        Objects.requireNonNull(customer, "customer must not be null");
        Objects.requireNonNull(pageable, "pageable must not be null");

        Range r = normalizeRange(from, to);
        String safeAsset = normalizeAsset(assetName);
        return orderRepo.search(customer, r.from(), r.to(), status, safeAsset, pageable);
    }

    private record Range(Instant from, Instant to) {}
    
    /**
     * Applies defaults (last 30 days), ensures from <= to, and caps the window.
     */
    private Range normalizeRange(Instant from, Instant to) {
        Instant now = Instant.now();
        Instant safeTo   = (to   == null) ? now : to;
        Instant safeFrom = (from == null) ? safeTo.minus(DEFAULT_RANGE_DAYS, ChronoUnit.DAYS) : from;

        if (safeFrom.isAfter(safeTo)) {
            throw new IllegalArgumentException("'from' must be before or equal to 'to'");
        }
        long days = Duration.between(safeFrom, safeTo).toDays();
        if (days > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("Date range too large. Max: " + MAX_RANGE_DAYS + " days");
        }
        return new Range(safeFrom, safeTo);
    }

    private static String normalizeAsset(String assetName) {
        return (assetName == null || assetName.isBlank()) ? null : assetName.trim();
    }

    private static void ensureEnough(BigDecimal available, BigDecimal required, String msg) {
        if (available == null || required == null || available.compareTo(required) < 0) {
            throw new IllegalStateException(msg);
        }
    }

    private static void validateNewOrder(String assetName, OrderSide side, BigDecimal size, BigDecimal price) {
        
        if (assetName == null || assetName.isBlank()) {
            throw new IllegalArgumentException("assetName is required");
        }
        if (side == null) {
            throw new IllegalArgumentException("side is required");
        }
        if (size == null || size.signum() <= 0) {
            throw new IllegalArgumentException("size must be > 0");
        }
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("price must be > 0");
        }
    }
}
