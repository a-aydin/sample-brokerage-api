package com.fintech.brokerage.service;

import com.fintech.brokerage.entity.Asset;
import com.fintech.brokerage.entity.Customer;
import com.fintech.brokerage.entity.Order;
import com.fintech.brokerage.enums.AssetType;
import com.fintech.brokerage.enums.OrderSide;
import com.fintech.brokerage.enums.OrderStatus;
import com.fintech.brokerage.enums.Role;
import com.fintech.brokerage.repo.OrderRepository;
import com.fintech.brokerage.service.AssetService;
import com.fintech.brokerage.service.impl.OrderServiceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepo;
    @Mock private AssetService assetService;

    @InjectMocks private OrderServiceImpl service;

    private Customer customer;

    @BeforeEach
    void setUp() {
        UUID fixedId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        customer = new Customer("alice", "{bcrypt}hash", Role.USER);
        customer.setId(fixedId);

        // @PrePersist won’t run in unit tests (no JPA), set explicitly if needed
        customer.setCreateDate(Instant.parse("2024-01-01T00:00:00Z"));
    }

    // --------------------------- CREATE ---------------------------

    @Test
    @DisplayName("create(BUY): reserves usable TRY and stores PENDING order")
    void create_buy_success() {
        BigDecimal price = convertStringToBigDecimal("10.00");
        BigDecimal size  = convertStringToBigDecimal("10.0000"); // total = 100.0000
        Asset tryAsset = new Asset(customer, AssetType.TRY.getSymbol(), convertStringToBigDecimal("1000.0000"), convertStringToBigDecimal("1000.0000"));
        when(assetService.getOrCreateAsset(customer, AssetType.TRY.getSymbol())).thenReturn(tryAsset);

        Order saved = new Order(customer, AssetType.AAPL.getSymbol(), OrderSide.BUY, size, price, OrderStatus.PENDING);
        when(orderRepo.save(any(Order.class))).thenReturn(saved);

        Order result = service.create(customer, AssetType.AAPL.getSymbol(), OrderSide.BUY, size, price);

        assertSame(saved, result, "should return repository-saved order");
        assertEquals(convertStringToBigDecimal("900.0000"), tryAsset.getUsableSize(), "usable TRY should decrease by total cost");
        verify(assetService).createOrUpdateAsset(tryAsset);
        verify(orderRepo).save(argThat(o ->
                o.getCustomerId() == customer &&
                o.getAssetName().equals(AssetType.AAPL.getSymbol()) &&
                o.getOrderSide() == OrderSide.BUY &&
                o.getStatus() == OrderStatus.PENDING
        ));
    }

    @Test
    @DisplayName("create(SELL): reserves usable asset and stores PENDING order")
    void create_sell_success() {
        BigDecimal price = convertStringToBigDecimal("50.00");
        BigDecimal size  = convertStringToBigDecimal("3.0000");
        Asset asset = new Asset(customer, AssetType.AAPL.getSymbol(), convertStringToBigDecimal("10.0000"), convertStringToBigDecimal("5.0000"));
        when(assetService.getOrCreateAsset(customer, AssetType.AAPL.getSymbol())).thenReturn(asset);

        Order saved = new Order(customer, AssetType.AAPL.getSymbol(), OrderSide.SELL, size, price, OrderStatus.PENDING);
        when(orderRepo.save(any(Order.class))).thenReturn(saved);

        Order result = service.create(customer, AssetType.AAPL.getSymbol(), OrderSide.SELL, size, price);

        assertSame(saved, result);
        assertEquals(convertStringToBigDecimal("2.0000"), asset.getUsableSize(), "usable asset should decrease by size");
        verify(assetService).createOrUpdateAsset(asset);
    }

    @Test
    @DisplayName("create(BUY): throws when TRY usable is insufficient")
    void create_buy_insufficient() {
        BigDecimal price = convertStringToBigDecimal("10.00");
        BigDecimal size  = convertStringToBigDecimal("10.0000"); // need 100
        Asset tryAsset = new Asset(customer, AssetType.TRY.getSymbol(), convertStringToBigDecimal("50.0000"), convertStringToBigDecimal("50.0000"));
        when(assetService.getOrCreateAsset(customer, AssetType.TRY.getSymbol())).thenReturn(tryAsset);

        assertThrows(IllegalStateException.class,
                () -> service.create(customer, AssetType.AAPL.getSymbol(), OrderSide.BUY, size, price));
        verify(orderRepo, never()).save(any());
        verify(assetService, never()).createOrUpdateAsset(any());
    }

    @Test
    @DisplayName("create: input validation (assetName/side/size/price)")
    void create_validation() {
        assertAll(
            () -> assertThrows(IllegalArgumentException.class,
                    () -> service.create(customer, " ", OrderSide.BUY, convertStringToBigDecimal("1"), convertStringToBigDecimal("1"))),
            () -> assertThrows(IllegalArgumentException.class,
                    () -> service.create(customer, AssetType.AAPL.getSymbol(), null, convertStringToBigDecimal("1"), convertStringToBigDecimal("1"))),
            () -> assertThrows(IllegalArgumentException.class,
                    () -> service.create(customer, AssetType.AAPL.getSymbol(), OrderSide.BUY, convertStringToBigDecimal("0"), convertStringToBigDecimal("1"))),
            () -> assertThrows(IllegalArgumentException.class,
                    () -> service.create(customer, AssetType.AAPL.getSymbol(), OrderSide.BUY, convertStringToBigDecimal("1"), convertStringToBigDecimal("-5")))
        );
    }

    // --------------------------- CANCEL ---------------------------

    @Test
    @DisplayName("cancel(BUY): atomic flip wins → refund exactly once")
    void cancel_buy_wins() {
        UUID id = UUID.randomUUID();
        Order pendingBuy = new Order(customer, AssetType.AAPL.getSymbol(), OrderSide.BUY, convertStringToBigDecimal("2.0000"), convertStringToBigDecimal("10.0000"), OrderStatus.PENDING);
        when(orderRepo.findById(id)).thenReturn(Optional.of(pendingBuy)); // first read
        when(orderRepo.cancelIfPending(id)).thenReturn(1);                // we win flip

        Asset tryAsset = new Asset(customer, AssetType.TRY.getSymbol(), convertStringToBigDecimal("0.0000"), convertStringToBigDecimal("0.0000"));
        when(assetService.getOrCreateAsset(customer, AssetType.TRY.getSymbol())).thenReturn(tryAsset);

        service.cancel(id);

        // refund 2 * 10 = 20 usable TRY
        assertEquals(convertStringToBigDecimal("20.0000"), tryAsset.getUsableSize());
        verify(assetService).createOrUpdateAsset(tryAsset);
        verify(orderRepo, times(1)).cancelIfPending(id);
        // No second branch (already canceled) executed
    }

    @Test
    @DisplayName("cancel(SELL): atomic flip wins → refund asset usable once")
    void cancel_sell_wins() {
        UUID id = UUID.randomUUID();
        Order pendingSell = new Order(customer, AssetType.AAPL.getSymbol(), OrderSide.SELL, convertStringToBigDecimal("5.0000"), convertStringToBigDecimal("3.0000"), OrderStatus.PENDING);
        when(orderRepo.findById(id)).thenReturn(Optional.of(pendingSell)); // first read
        when(orderRepo.cancelIfPending(id)).thenReturn(1);

        Asset asset = new Asset(customer, AssetType.AAPL.getSymbol(), convertStringToBigDecimal("10.0000"), convertStringToBigDecimal("0.0000"));
        when(assetService.getOrCreateAsset(customer, AssetType.AAPL.getSymbol())).thenReturn(asset);

        service.cancel(id);

        assertEquals(convertStringToBigDecimal("5.0000"), asset.getUsableSize());
        verify(assetService).createOrUpdateAsset(asset);
    }

    @Test
    @DisplayName("cancel: idempotent no-op when already CANCELED")
    void cancel_idempotent_alreadyCanceled() {
        UUID id = UUID.randomUUID();
        Order anyOrder = new Order(customer, AssetType.AAPL.getSymbol(), OrderSide.BUY, convertStringToBigDecimal("1.0000"), convertStringToBigDecimal("1.0000"), OrderStatus.PENDING);
        when(orderRepo.findById(id)).thenReturn(Optional.of(anyOrder)); // first read
        when(orderRepo.cancelIfPending(id)).thenReturn(0);
        // second read shows already CANCELED
        Order canceled = new Order(customer, AssetType.AAPL.getSymbol(), OrderSide.BUY, convertStringToBigDecimal("1.0"), convertStringToBigDecimal("1.0"), OrderStatus.CANCELED);
        when(orderRepo.findById(id)).thenReturn(Optional.of(anyOrder), Optional.of(canceled));

        assertDoesNotThrow(() -> service.cancel(id));
        // No refunds, no exceptions
        verify(assetService, never()).createOrUpdateAsset(any());
    }

    @Test
    @DisplayName("cancel: throws when not cancelable anymore (MATCHED)")
    void cancel_notCancelable() {
        UUID id = UUID.randomUUID();
        Order anyOrder = new Order(customer, AssetType.AAPL.getSymbol(), OrderSide.BUY, convertStringToBigDecimal("1.0"), convertStringToBigDecimal("1.0"), OrderStatus.PENDING);
        when(orderRepo.findById(id)).thenReturn(Optional.of(anyOrder)); // first read
        when(orderRepo.cancelIfPending(id)).thenReturn(0);
        // second read shows MATCHED
        Order matched = new Order(customer, AssetType.AAPL.getSymbol(), OrderSide.BUY, convertStringToBigDecimal("1.0"), convertStringToBigDecimal("1.0"), OrderStatus.MATCHED);
        when(orderRepo.findById(id)).thenReturn(Optional.of(anyOrder), Optional.of(matched));

        assertThrows(IllegalStateException.class, () -> service.cancel(id));
        verify(assetService, never()).createOrUpdateAsset(any());
    }

    // --------------------------- MATCH ---------------------------

    @Test
    @DisplayName("match(BUY): atomic flip wins → settle exactly once")
    void match_buy_wins() {
        UUID id = UUID.randomUUID();

        // Arrange
        Order pendingBuy = new Order(
                customer,
                AssetType.AAPL.getSymbol(), 
                OrderSide.BUY,
                new BigDecimal("3.0000"),
                new BigDecimal("10.0000"),
                OrderStatus.PENDING
        );
        when(orderRepo.findById(id)).thenReturn(Optional.of(pendingBuy));
        when(orderRepo.matchIfPending(id)).thenReturn(1);

        Asset tryAsset = new Asset(
                customer,
                AssetType.TRY.getSymbol(),
                new BigDecimal("100.0000"),
                new BigDecimal("100.0000")
        );
        Asset aaplAsset = new Asset(
                customer,
                AssetType.AAPL.getSymbol(),
                convertStringToBigDecimal("0.0000"),
                convertStringToBigDecimal("0.0000")
        );

        when(assetService.getOrCreateAsset(customer, AssetType.TRY.getSymbol())).thenReturn(tryAsset);
        when(assetService.getOrCreateAsset(customer, AssetType.AAPL.getSymbol())).thenReturn(aaplAsset);

        // Act
        service.match(id);

        assertEquals(new BigDecimal("70.0000"), tryAsset.getSize(), "TRY total should decrease by 30");
        assertEquals(new BigDecimal("3.0000"),  aaplAsset.getSize(), "AAPL total should increase by 3");
        assertEquals(new BigDecimal("3.0000"),  aaplAsset.getUsableSize(), "AAPL usable should increase by 3");

        verify(orderRepo, times(1)).matchIfPending(id);
        verify(assetService, times(2)).createOrUpdateAsset(any());
        verify(orderRepo, never()).save(any(Order.class));
    }


    @Test
    @DisplayName("match(SELL): atomic flip wins → settle exactly once")
    void match_sell_wins() {
        UUID id = UUID.randomUUID();
        Order pendingSell = new Order(customer, AssetType.AAPL.getSymbol(), OrderSide.SELL, convertStringToBigDecimal("2.0000"), convertStringToBigDecimal("25.0000"), OrderStatus.PENDING);
        when(orderRepo.findById(id)).thenReturn(Optional.of(pendingSell)); // first read
        when(orderRepo.matchIfPending(id)).thenReturn(1);

        Asset asset    = new Asset(customer, AssetType.AAPL.getSymbol(), convertStringToBigDecimal("10.0000"), convertStringToBigDecimal("5.0000"));
        Asset tryAsset = new Asset(customer, AssetType.TRY.getSymbol(), convertStringToBigDecimal("0.0000"), convertStringToBigDecimal("0.0000"));
        when(assetService.getOrCreateAsset(customer, AssetType.AAPL.getSymbol())).thenReturn(asset);
        when(assetService.getOrCreateAsset(customer, AssetType.TRY.getSymbol())).thenReturn(tryAsset);

        service.match(id);

        // SELL match: Asset total -= 2; TRY total += 50; TRY usable += 50
        assertEquals(convertStringToBigDecimal("8.0000"), asset.getSize());
        assertEquals(convertStringToBigDecimal("50.0000"), tryAsset.getSize());
        assertEquals(convertStringToBigDecimal("50.0000"), tryAsset.getUsableSize());
        verify(assetService, times(3)).createOrUpdateAsset(any());
    }

    @Test
    @DisplayName("match: idempotent no-op when already MATCHED")
    void match_idempotent_alreadyMatched() {
        UUID id = UUID.randomUUID();
        Order any = new Order(customer, AssetType.AAPL.getSymbol(), OrderSide.BUY, convertStringToBigDecimal("1"), convertStringToBigDecimal("1"), OrderStatus.PENDING);
        when(orderRepo.findById(id)).thenReturn(Optional.of(any));
        when(orderRepo.matchIfPending(id)).thenReturn(0);
        Order matched = new Order(customer, AssetType.AAPL.getSymbol(), OrderSide.BUY, convertStringToBigDecimal("1"), convertStringToBigDecimal("1"), OrderStatus.MATCHED);
        when(orderRepo.findById(id)).thenReturn(Optional.of(any), Optional.of(matched));

        assertDoesNotThrow(() -> service.match(id));
        verify(assetService, never()).createOrUpdateAsset(any());
    }

    @Test
    @DisplayName("match: throws when not PENDING and not already matched (e.g., CANCELED)")
    void match_notPending_throws() {
        UUID id = UUID.randomUUID();
        Order any = new Order(customer, AssetType.AAPL.getSymbol(), OrderSide.BUY, convertStringToBigDecimal("1"), convertStringToBigDecimal("1"), OrderStatus.PENDING);
        when(orderRepo.findById(id)).thenReturn(Optional.of(any));
        when(orderRepo.matchIfPending(id)).thenReturn(0);
        Order canceled = new Order(customer, AssetType.AAPL.getSymbol(), OrderSide.BUY, convertStringToBigDecimal("1"), convertStringToBigDecimal("1"), OrderStatus.CANCELED);
        when(orderRepo.findById(id)).thenReturn(Optional.of(any), Optional.of(canceled));

        assertThrows(IllegalStateException.class, () -> service.match(id));
    }

    // --------------------------- LIST ---------------------------

    @Test
    @DisplayName("list: defaults date range, trims AssetType.AAPL.getSymbol(), delegates to repo")
    void list_defaults_and_delegates() {
        List<Order> mockResult = List.of(
                new Order(customer, AssetType.AAPL.getSymbol(), OrderSide.BUY, convertStringToBigDecimal("1"), convertStringToBigDecimal("1"), OrderStatus.PENDING)
        );
        when(orderRepo.search(any(), any(), any(), any(), any())).thenReturn(mockResult);

        List<Order> res = service.list(customer, null, null, null, "  " + AssetType.AAPL.getSymbol() + "  "); // to test trim→non-null
        assertEquals(1, res.size());

        ArgumentCaptor<Instant> fromCap = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> toCap   = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<String> assetCap = ArgumentCaptor.forClass(String.class);

        verify(orderRepo).search(eq(customer), fromCap.capture(), toCap.capture(), isNull(), assetCap.capture());

        Instant from = fromCap.getValue();
        Instant to   = toCap.getValue();
        assertTrue(!from.isAfter(to), "from should be <= to");
        assertEquals(AssetType.AAPL.getSymbol(), assetCap.getValue().trim(), "asset should be trimmed");
    }

    @Test
    @DisplayName("list: throws on invalid range (from > to)")
    void list_invalid_range() {
        Instant to   = Instant.now();
        Instant from = to.plusSeconds(60);
        assertThrows(IllegalArgumentException.class,
                () -> service.list(customer, from, to, null, null));
    }

    @Test
    @DisplayName("list(pageable): normalizes asset and delegates to repo.search(pageable)")
    void list_pageable_delegates() {
        Page<Order> page = new PageImpl<>(List.of());
        when(orderRepo.search(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(page);

        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createDate"));
        Page<Order> out = service.list(customer, null, null, null, "  " + AssetType.AAPL.getSymbol() + "  ", pageable);

        assertSame(page, out);
        verify(orderRepo).search(eq(customer), any(), any(), isNull(), eq(AssetType.AAPL.getSymbol()), eq(pageable));
    }

    private static BigDecimal convertStringToBigDecimal(String s) {
        return new BigDecimal(s);
    }
}
