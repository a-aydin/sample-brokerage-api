package com.fintech.brokerage.service.impl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fintech.brokerage.entity.Asset;
import com.fintech.brokerage.entity.Customer;
import com.fintech.brokerage.repo.AssetRepository;
import com.fintech.brokerage.repo.CustomerRepository;
import com.fintech.brokerage.service.AssetService;

import jakarta.persistence.EntityNotFoundException;

@Service
public class AssetServiceImpl implements AssetService {

	private final AssetRepository assetRepo;
	private final CustomerRepository customerRepo;

	public AssetServiceImpl(AssetRepository assetRepo, CustomerRepository customerRepo) {
		this.assetRepo = assetRepo;
		this.customerRepo = customerRepo;
	}

	@Override
	public List<Asset> listAssets(UUID customerId) {
		Customer customer = customerRepo.findById(customerId)
				.orElseThrow(() -> new EntityNotFoundException("Customer not found with id: " + customerId));

		return assetRepo.findAllByCustomerId(customer);
	}

	@Override
	public Asset getOrCreateAsset(Customer customer, String assetName) {
		return assetRepo.findByCustomerIdAndAssetName(customer, assetName)
				.orElseGet(() -> assetRepo.save(new Asset(customer, assetName, BigDecimal.ZERO, BigDecimal.ZERO)));
	}

	@Override
	public Asset createOrUpdateAsset(Asset asset) {
		return assetRepo.save(asset);		
	}

	@Override
	public Optional<Asset> findByCustomerIdAndAssetName(Customer customer, String assetName) {
		return assetRepo.findByCustomerIdAndAssetName(customer, assetName);
	}

}
