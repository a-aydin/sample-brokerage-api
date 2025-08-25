package com.fintech.brokerage.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fintech.brokerage.entity.Asset;
import com.fintech.brokerage.entity.Customer;
import com.fintech.brokerage.entity.Order;
import com.fintech.brokerage.enums.AssetType;
import com.fintech.brokerage.enums.OrderSide;
import com.fintech.brokerage.enums.OrderStatus;
import com.fintech.brokerage.repo.OrderRepository;
import com.fintech.brokerage.service.impl.OrderServiceImpl;

class OrderServiceTest {

    private OrderRepository orderRepo;
    private AssetService assetService;
    private OrderService orderService;

    private Customer customer;
    private final UUID customerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        orderRepo = mock(OrderRepository.class);
        assetService = mock(AssetService.class);
        orderService = new OrderServiceImpl(orderRepo, assetService);

        customer = new Customer("Jane", "some encoded pass");
        customer.setId(customerId);
    }

    @Test
    void testCreateBuyOrder_Success() {
        Asset tryAsset = new Asset(customer, AssetType.TRY.getSymbol(), BigDecimal.valueOf(1000), BigDecimal.valueOf(1000));
        when(assetService.getOrCreateAsset(customer, AssetType.TRY.getSymbol())).thenReturn(tryAsset);
        when(orderRepo.save(any(Order.class))).thenAnswer(i -> i.getArguments()[0]);

        BigDecimal size = BigDecimal.valueOf(10);
        BigDecimal price = BigDecimal.valueOf(50);

        Order order = orderService.create(customer, AssetType.AAPL.getSymbol(), OrderSide.BUY, size, price);

        assertNotNull(order);
        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertEquals(BigDecimal.valueOf(500), tryAsset.getUsableSize()); // 1000 - 500

        verify(assetService).createOrUpdateAsset(tryAsset);
        verify(orderRepo).save(order);
    }

    @Test
    void testCreateSellOrder_Success() {
        Asset asset = new Asset(customer, AssetType.AAPL.getSymbol(), BigDecimal.valueOf(100), BigDecimal.valueOf(80));
        when(assetService.getOrCreateAsset(customer, AssetType.AAPL.getSymbol())).thenReturn(asset);
        when(orderRepo.save(any(Order.class))).thenAnswer(i -> i.getArguments()[0]);

        BigDecimal size = BigDecimal.valueOf(50);
        BigDecimal price = BigDecimal.valueOf(10);

        Order order = orderService.create(customer, AssetType.AAPL.getSymbol(), OrderSide.SELL, size, price);

        assertNotNull(order);
        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertEquals(BigDecimal.valueOf(30), asset.getUsableSize());

        verify(assetService).createOrUpdateAsset(asset);
        verify(orderRepo).save(order);
    }

    @Test
    void testCancelOrder_BuyOrder() {
        Order order = new Order(customer, AssetType.AAPL.getSymbol(), OrderSide.BUY, BigDecimal.valueOf(10), BigDecimal.valueOf(50), OrderStatus.PENDING);
        when(orderRepo.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepo.save(any(Order.class))).thenAnswer(i -> i.getArguments()[0]);

        Asset tryAsset = new Asset(customer, AssetType.TRY.getSymbol(), BigDecimal.valueOf(1000), BigDecimal.valueOf(500));
        when(assetService.getOrCreateAsset(customer, AssetType.TRY.getSymbol())).thenReturn(tryAsset);

        orderService.cancel(order.getId());

        assertEquals(OrderStatus.CANCELED, order.getStatus());
        assertEquals(BigDecimal.valueOf(1000), tryAsset.getUsableSize());

        verify(assetService).createOrUpdateAsset(tryAsset);
        verify(orderRepo).save(order);
    }

    @Test
    void testListOrders() {
        Order order = new Order(customer, AssetType.AAPL.getSymbol(), OrderSide.BUY, BigDecimal.valueOf(10), BigDecimal.valueOf(50), OrderStatus.PENDING);
        Instant from = Instant.now();
        Instant to = Instant.now();

        when(orderRepo.search(customer, from, to, null, null)).thenReturn(List.of(order));

        List<Order> orders = orderService.list(customer, from, to, null, null);

        assertEquals(1, orders.size());
        assertEquals(order, orders.get(0));
    }

    @Test
    void testMatchOrder_Buy_Success() {
        Order order = new Order(customer, AssetType.AAPL.getSymbol(), OrderSide.BUY, BigDecimal.valueOf(10), BigDecimal.valueOf(50), OrderStatus.PENDING);
        when(orderRepo.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepo.save(any(Order.class))).thenAnswer(i -> i.getArguments()[0]);

        Asset tryAsset = new Asset(customer, AssetType.TRY.getSymbol(), BigDecimal.valueOf(1000), BigDecimal.valueOf(1000));
        when(assetService.getOrCreateAsset(customer, AssetType.TRY.getSymbol())).thenReturn(tryAsset);

        Asset asset = new Asset(customer, AssetType.AAPL.getSymbol(), BigDecimal.valueOf(50), BigDecimal.valueOf(20));
        when(assetService.getOrCreateAsset(customer, AssetType.AAPL.getSymbol())).thenReturn(asset);

        when(assetService.createOrUpdateAsset(any(Asset.class))).thenAnswer(i -> i.getArguments()[0]);

        orderService.match(order.getId());

        assertEquals(OrderStatus.MATCHED, order.getStatus());
        assertEquals(BigDecimal.valueOf(500), tryAsset.getSize());
        assertEquals(BigDecimal.valueOf(60), asset.getSize());
        assertEquals(BigDecimal.valueOf(30), asset.getUsableSize());

        verify(assetService, times(2)).createOrUpdateAsset(any(Asset.class));
        verify(orderRepo).save(order);
    }

    @Test
    void testMatchOrder_Sell_Success() {
        Order order = new Order(customer, AssetType.AAPL.getSymbol(), OrderSide.SELL, BigDecimal.valueOf(10), BigDecimal.valueOf(50), OrderStatus.PENDING);
        when(orderRepo.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepo.save(any(Order.class))).thenAnswer(i -> i.getArguments()[0]);

        Asset asset = new Asset(customer, AssetType.AAPL.getSymbol(), BigDecimal.valueOf(50), BigDecimal.valueOf(50));
        Asset tryAsset = new Asset(customer, AssetType.TRY.getSymbol(), BigDecimal.valueOf(500), BigDecimal.valueOf(500));

        when(assetService.getOrCreateAsset(customer, AssetType.AAPL.getSymbol())).thenReturn(asset);
        when(assetService.getOrCreateAsset(customer, AssetType.TRY.getSymbol())).thenReturn(tryAsset);
        when(assetService.createOrUpdateAsset(any(Asset.class))).thenAnswer(i -> i.getArguments()[0]);

        orderService.match(order.getId());

        assertEquals(OrderStatus.MATCHED, order.getStatus());
        assertEquals(BigDecimal.valueOf(40), asset.getSize());
        assertEquals(BigDecimal.valueOf(50), asset.getUsableSize());
        assertEquals(BigDecimal.valueOf(1000), tryAsset.getSize());
        assertEquals(BigDecimal.valueOf(1000), tryAsset.getUsableSize());

        verify(assetService).createOrUpdateAsset(asset);
        verify(assetService).createOrUpdateAsset(tryAsset);
        verify(orderRepo).save(order);
    }

    @Test
    void testMatchOrder_NotPending_Throws() {
        Order order = new Order(customer, AssetType.AAPL.getSymbol(), OrderSide.BUY, BigDecimal.TEN, BigDecimal.valueOf(50), OrderStatus.MATCHED);
        when(orderRepo.findById(order.getId())).thenReturn(Optional.of(order));

        assertThrows(IllegalStateException.class, () -> orderService.match(order.getId()));
    }

    @Test
    void testMatchOrder_NotFound_Throws() {
        UUID unknownId = UUID.randomUUID();
        when(orderRepo.findById(unknownId)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> orderService.match(unknownId));
    }
}
