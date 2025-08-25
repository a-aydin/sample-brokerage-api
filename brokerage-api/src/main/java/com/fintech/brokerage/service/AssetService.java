package com.fintech.brokerage.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.fintech.brokerage.entity.Asset;
import com.fintech.brokerage.entity.Customer;

public interface AssetService {

	public List<Asset> listAssets(UUID customerId);
	public Asset getOrCreateAsset(Customer customer, String assetName);
	public Asset createOrUpdateAsset(Asset asset);
	public Optional<Asset> findByCustomerIdAndAssetName(Customer customerId, String assetName);
}
