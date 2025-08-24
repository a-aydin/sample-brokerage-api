package com.fintech.brokerage.repo;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import com.fintech.brokerage.entity.Customer;
import java.util.List;


public interface CustomerRepository extends JpaRepository<Customer, UUID> {
   
	Optional<Customer> findByUsername(String username);
    
}