package com.fintech.brokerage.repo;

import java.util.*;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import com.fintech.brokerage.entity.Asset;
import com.fintech.brokerage.entity.Customer;

public interface AssetRepository extends JpaRepository<Asset, UUID> {
    public Optional<Asset> findByCustomerIdAndAssetName(Customer customerId, String assetName);
    public List<Asset> findAllByCustomerId(Customer customerId);
}