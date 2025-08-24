package com.fintech.brokerage.service;

import org.springframework.stereotype.Service;
import com.fintech.brokerage.entity.Customer;
import com.fintech.brokerage.repo.CustomerRepository;

import java.util.Optional;
import java.util.UUID;

@Service
public class CustomerService {

    private final CustomerRepository customerRepo;

    public CustomerService(CustomerRepository customerRepo) {
        this.customerRepo = customerRepo;
    }

    public Optional<Customer> findById(UUID id) {
        return customerRepo.findById(id);
    }

    public Iterable<Customer> listAll() {
        return customerRepo.findAll();
    }
}
