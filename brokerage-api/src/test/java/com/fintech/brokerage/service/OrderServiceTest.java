package com.fintech.brokerage.service;

import com.fintech.brokerage.entity.Asset;
import com.fintech.brokerage.entity.Customer;
import com.fintech.brokerage.entity.Order;
import com.fintech.brokerage.enums.OrderSide;
import com.fintech.brokerage.enums.OrderStatus;
import com.fintech.brokerage.repo.AssetRepository;
import com.fintech.brokerage.repo.CustomerRepository;
import com.fintech.brokerage.repo.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderServiceTest {

    private OrderRepository orderRepo;
    private AssetRepository assetRepo;
    private CustomerRepository customerRepo;
    private OrderService orderService;

    private Customer customer;
    private final UUID customerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        orderRepo = mock(OrderRepository.class);
        assetRepo = mock(AssetRepository.class);
        customerRepo = mock(CustomerRepository.class);
        orderService = new OrderService(orderRepo, assetRepo, customerRepo);

        customer = new Customer("Jane", "some encoded pass");
        customer.setId(customerId);
    }

    @Test
    void testCreateBuyOrder_Success() {
        Asset tryAsset = new Asset(customer, "TRY", BigDecimal.valueOf(1000), BigDecimal.valueOf(1000));
        when(assetRepo.findByCustomerIdAndAssetName(customer, "TRY")).thenReturn(Optional.of(tryAsset));
        when(orderRepo.save(any(Order.class))).thenAnswer(i -> i.getArguments()[0]);

        BigDecimal size = BigDecimal.valueOf(10);
        BigDecimal price = BigDecimal.valueOf(50);

        Order order = orderService.create(customer, "AAPL", OrderSide.BUY, size, price);

        assertNotNull(order);
        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertEquals(BigDecimal.valueOf(500), tryAsset.getUsableSize()); // 1000 - 10*50

        verify(assetRepo, times(1)).save(tryAsset);
        verify(orderRepo, times(1)).save(order);
    }

    @Test
    void testCreateSellOrder_Success() {
        Asset asset = new Asset(customer, "AAPL", BigDecimal.valueOf(100), BigDecimal.valueOf(80));
        when(assetRepo.findByCustomerIdAndAssetName(customer, "AAPL")).thenReturn(Optional.of(asset));
        when(orderRepo.save(any(Order.class))).thenAnswer(i -> i.getArguments()[0]);

        BigDecimal size = BigDecimal.valueOf(50);
        BigDecimal price = BigDecimal.valueOf(10);

        Order order = orderService.create(customer, "AAPL", OrderSide.SELL, size, price);

        assertNotNull(order);
        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertEquals(BigDecimal.valueOf(30), asset.getUsableSize()); // 80 - 50

        verify(assetRepo, times(1)).save(asset);
        verify(orderRepo, times(1)).save(order);
    }

    @Test
    void testCancelOrder_BuyOrder() {
        Order order = new Order(customer, "AAPL", OrderSide.BUY, BigDecimal.valueOf(10), BigDecimal.valueOf(50), OrderStatus.PENDING);
        when(orderRepo.findById(order.getId())).thenReturn(Optional.of(order));
        Asset tryAsset = new Asset(customer, "TRY", BigDecimal.valueOf(1000), BigDecimal.valueOf(500));
        when(assetRepo.findByCustomerIdAndAssetName(customer, "TRY")).thenReturn(Optional.of(tryAsset));
        when(orderRepo.save(any(Order.class))).thenAnswer(i -> i.getArguments()[0]);

        orderService.cancel(order.getId());

        assertEquals(OrderStatus.CANCELED, order.getStatus());
        assertEquals(BigDecimal.valueOf(1000), tryAsset.getUsableSize());
    }

    @Test
    void testListOrders() {
        Order order = new Order(customer, "AAPL", OrderSide.BUY, BigDecimal.valueOf(10), BigDecimal.valueOf(50), OrderStatus.PENDING);
        
        Instant from = Instant.now();
        Instant to = Instant.now();
        
        when(orderRepo.search(customer, from, to, null, null)).thenReturn(List.of(order));

        List<Order> orders = orderService.list(customer, from, to, null, null);

        assertEquals(1, orders.size());
        assertEquals(order, orders.get(0));
    }

    @Test
    void testListAssets() {
        Asset asset = new Asset(customer, "AAPL", BigDecimal.valueOf(100), BigDecimal.valueOf(80));
        when(customerRepo.findById(customerId)).thenReturn(Optional.of(customer));
        when(assetRepo.findAllByCustomerId(customer)).thenReturn(List.of(asset));

        List<Asset> assets = orderService.listAssets(customerId);

        assertEquals(1, assets.size());
        assertEquals(asset, assets.get(0));
    }
    @Test
    void testMatchOrder_Buy_Success() {
        // Mock order
        Order order = new Order(customer, "AAPL", OrderSide.BUY, BigDecimal.valueOf(10), BigDecimal.valueOf(50), OrderStatus.PENDING);
        when(orderRepo.findById(order.getId())).thenReturn(Optional.of(order));

        // Mock TRY asset
        Asset tryAsset = new Asset(customer, OrderService.TRY, BigDecimal.valueOf(1000), BigDecimal.valueOf(1000));
        when(assetRepo.findByCustomerIdAndAssetName(customer, OrderService.TRY)).thenReturn(Optional.of(tryAsset));

        // Mock asset to buy
        Asset asset = new Asset(customer, "AAPL", BigDecimal.valueOf(50), BigDecimal.valueOf(20));
        when(assetRepo.findByCustomerIdAndAssetName(customer, "AAPL")).thenReturn(Optional.of(asset));

        when(orderRepo.save(any(Order.class))).thenAnswer(i -> i.getArguments()[0]);
        when(assetRepo.save(any(Asset.class))).thenAnswer(i -> i.getArguments()[0]);

        // Call service
        orderService.match(order.getId());

        // Assertions
        assertEquals(OrderStatus.MATCHED, order.getStatus());
        assertEquals(BigDecimal.valueOf(500), tryAsset.getSize()); // 1000 - 10*50
        assertEquals(BigDecimal.valueOf(60), asset.getSize());     // 50 + 10
        assertEquals(BigDecimal.valueOf(30), asset.getUsableSize());// 20 + 10

        verify(assetRepo, times(2)).save(any(Asset.class));
        verify(orderRepo, times(1)).save(order);
    }

    @Test
    void testMatchOrder_Sell_Success() {
        Order order = new Order(customer, "AAPL", OrderSide.SELL, BigDecimal.valueOf(10), BigDecimal.valueOf(50), OrderStatus.PENDING);
        when(orderRepo.findById(order.getId())).thenReturn(Optional.of(order));

        Asset asset = new Asset(customer, "AAPL", BigDecimal.valueOf(50), BigDecimal.valueOf(50));
        Asset tryAsset = new Asset(customer, OrderService.TRY, BigDecimal.valueOf(500), BigDecimal.valueOf(500));

        when(assetRepo.findByCustomerIdAndAssetName(eq(customer), eq("AAPL"))).thenReturn(Optional.of(asset));
        when(assetRepo.findByCustomerIdAndAssetName(eq(customer), eq(OrderService.TRY))).thenReturn(Optional.of(tryAsset));

        when(orderRepo.save(any(Order.class))).thenAnswer(i -> i.getArguments()[0]);
        when(assetRepo.save(any(Asset.class))).thenAnswer(i -> i.getArguments()[0]);

        orderService.match(order.getId());

        assertEquals(OrderStatus.MATCHED, order.getStatus());
        assertEquals(BigDecimal.valueOf(40), asset.getSize());
        assertEquals(BigDecimal.valueOf(50), asset.getUsableSize());
        assertEquals(BigDecimal.valueOf(1000), tryAsset.getSize());
        assertEquals(BigDecimal.valueOf(1000), tryAsset.getUsableSize());

        verify(assetRepo).save(asset);
        verify(assetRepo).save(tryAsset);
        verify(orderRepo).save(order);
    }

    @Test
    void testMatchOrder_OrderNotPending_Throws() {
        Order order = new Order(customer, "AAPL", OrderSide.BUY, BigDecimal.TEN, BigDecimal.valueOf(50), OrderStatus.MATCHED);
        when(orderRepo.findById(order.getId())).thenReturn(Optional.of(order));

        assertThrows(IllegalStateException.class, () -> orderService.match(order.getId()));
    }

    @Test
    void testMatchOrder_OrderNotFound_Throws() {
        UUID unknownId = UUID.randomUUID();
        when(orderRepo.findById(unknownId)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> orderService.match(unknownId));
    }

}
