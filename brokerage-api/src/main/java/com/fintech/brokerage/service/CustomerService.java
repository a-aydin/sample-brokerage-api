package com.fintech.brokerage.service;

import java.util.Optional;
import java.util.UUID;
import com.fintech.brokerage.entity.Customer;

public interface CustomerService {

	public Optional<Customer> findById(UUID id);
	public Iterable<Customer> listAll();
}
