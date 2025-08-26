package com.fintech.brokerage.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Sort;

import com.fintech.brokerage.controller.dto.*;
import com.fintech.brokerage.entity.*;
import com.fintech.brokerage.enums.OrderStatus;
import com.fintech.brokerage.security.util.SecurityUtil;
import com.fintech.brokerage.service.CustomerService;
import com.fintech.brokerage.service.OrderService;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;
    private final CustomerService customerService;

    public OrderController(OrderService orderService, CustomerService customerService) {
        this.orderService = orderService;
        this.customerService = customerService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest req) {
        log.info("Creating order for customerId={} asset={} side={} size={} price={}",
                 req.getCustomerId(), req.getAssetName(), req.getSide(), req.getSize(), req.getPrice());

        Customer customer = customerService.findById(req.getCustomerId())
                .orElseThrow(() -> {
                    log.warn("Customer not found: {}", req.getCustomerId());
                    return new IllegalArgumentException("Customer not found");
                });

        checkAccess(customer.getId());

        Order order = orderService.create(customer, req.getAssetName(), req.getSide(), req.getSize(), req.getPrice());
        log.info("Order created successfully: orderId={}", order.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(new OrderResponse(order));
    }

    @GetMapping
    public List<OrderResponse> list(@RequestParam UUID customerId,
                                    @RequestParam @DateTimeFormat(iso= DateTimeFormat.ISO.DATE_TIME) Instant from,
                                    @RequestParam @DateTimeFormat(iso= DateTimeFormat.ISO.DATE_TIME) Instant to,
                                    @RequestParam(required = false) OrderStatus status,
                                    @RequestParam(required = false) String assetName) {

        log.info("Listing orders for customerId={} from={} to={} status={} assetName={}",
                 customerId, from, to, status, assetName);

        Customer customer = customerService.findById(customerId)
                .orElseThrow(() -> {
                    log.warn("Customer not found: {}", customerId);
                    return new IllegalArgumentException("Customer not found");
                });

        checkAccess(customer.getId());

        List<OrderResponse> orders = orderService.list(customer, from, to, status, assetName)
                .stream().map(OrderResponse::new).collect(Collectors.toList());

        log.info("Found {} orders for customerId={}", orders.size(), customerId);
        return orders;
    }
    
    @GetMapping("/paged")
    public Page<OrderResponse> listPaged(@RequestParam UUID customerId,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Optional<Instant> from,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Optional<Instant> to,
                                         @RequestParam(required = false) OrderStatus status,
                                         @RequestParam(required = false) String assetName,
                                         @PageableDefault(size = 20, sort = "createDate", direction = Sort.Direction.DESC)
                                         Pageable pageable) {

        Customer customer = customerService.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));

        checkAccess(customer.getId());

        Page<Order> page = orderService.list(
                customer,
                from.orElse(null),
                to.orElse(null),
                status,
                assetName,
                pageable
        );

        return page.map(OrderResponse::new);
    }

    @DeleteMapping("/{orderId}")
    public ResponseEntity<Void> cancel(@PathVariable UUID orderId) {
        log.info("Cancelling order: orderId={}", orderId);
        orderService.cancel(orderId);
        log.info("Order cancelled successfully: orderId={}", orderId);
        return ResponseEntity.noContent().build();
    }

    private void checkAccess(UUID customerId) {
        if (SecurityUtil.isAdmin()) {
            log.debug("Admin access granted for customerId={}", customerId);
            return;
        }
        UUID tokenCustomerId = SecurityUtil.currentCustomerId()
                .orElseThrow(() -> {
                    log.warn("No customer in token for access check");
                    return new AccessDeniedException("No customer in token");
                });

        if (!tokenCustomerId.equals(customerId)) {
            log.warn("Access denied: tokenCustomerId={} tried to access customerId={}", tokenCustomerId, customerId);
            throw new AccessDeniedException("Forbidden");
        }

        log.debug("Access granted for customerId={}", customerId);
    }
}
