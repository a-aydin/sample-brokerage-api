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

}
