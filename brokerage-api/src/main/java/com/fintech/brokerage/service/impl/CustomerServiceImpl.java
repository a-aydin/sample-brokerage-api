package com.fintech.brokerage.service.impl;

import org.springframework.stereotype.Service;
import com.fintech.brokerage.entity.Customer;
import com.fintech.brokerage.repo.CustomerRepository;
import com.fintech.brokerage.service.CustomerService;

import java.util.Optional;
import java.util.UUID;

@Service
public class CustomerServiceImpl implements CustomerService{

    private final CustomerRepository customerRepo;

    public CustomerServiceImpl(CustomerRepository customerRepo) {
        this.customerRepo = customerRepo;
    }

    @Override
    public Optional<Customer> findById(UUID id) {
        return customerRepo.findById(id);
    }
    @Override
    public Iterable<Customer> listAll() {
        return customerRepo.findAll();
    }
}
