package com.fintech.brokerage.repo;

import com.fintech.brokerage.entity.Customer;
import com.fintech.brokerage.entity.Order;
import com.fintech.brokerage.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    @Query("select o from Order o where o.customerId = :customer and o.createDate between :from and :to"
        + " and (:status is null or o.status = :status) and (:assetName is null or o.assetName = :assetName)")
    List<Order> search(@Param("customer") Customer customer,
                       @Param("from") Instant from,
                       @Param("to") Instant to,
                       @Param("status") OrderStatus status,
                       @Param("assetName") String assetName);

    Page<Order> findByCustomerId(Customer customer, Pageable pageable);

    /**
     * Atomically flip PENDING -> CANCELED. Returns 1 if we won the race, else 0.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update Order o
           set o.status = com.fintech.brokerage.enums.OrderStatus.CANCELED
         where o.id = :orderId
           and o.status = com.fintech.brokerage.enums.OrderStatus.PENDING
    """)
    int cancelIfPending(@Param("orderId") UUID orderId);
    
    /**
     * Atomically flip PENDING -> MATCHED. Returns 1 if we won the race, else 0.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update Order o
           set o.status = com.fintech.brokerage.enums.OrderStatus.MATCHED
         where o.id = :orderId
           and o.status = com.fintech.brokerage.enums.OrderStatus.PENDING
    """)
    int matchIfPending(@Param("orderId") UUID orderId);
    
    @Query("""
            select o from Order o
             where o.customerId = :customer
               and o.createDate between :from and :to
               and (:status is null or o.status = :status)
               and (:assetName is null or o.assetName = :assetName)
        """)
    Page<Order> search(@Param("customer") Customer customer,
                       @Param("from") Instant from,
                       @Param("to") Instant to,
                       @Param("status") OrderStatus status,
                       @Param("assetName") String assetName,
                       Pageable pageable);
}
