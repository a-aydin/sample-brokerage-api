package com.fintech.brokerage.controller;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fintech.brokerage.entity.Asset;
import com.fintech.brokerage.security.util.SecurityUtil;
import com.fintech.brokerage.service.AssetService;

@RestController
@RequestMapping("/api/assets")
public class AssetController {

	private static final Logger log = LoggerFactory.getLogger(AssetController.class);

	private final AssetService assetService;

	public AssetController(AssetService assetService) {
		this.assetService = assetService;
	}

	private void checkAccess(UUID customerId) {
		if (SecurityUtil.isAdmin()) {
			log.debug("Admin access granted for customerId={}", customerId);
			return;
		}

		UUID tokenCustomerId = SecurityUtil.currentCustomerId().orElseThrow(() -> {
			log.warn("Access denied: no customer found in token while accessing customerId={}", customerId);
			return new AccessDeniedException("No customer in token");
		});

		if (!tokenCustomerId.equals(customerId)) {
			log.warn("Access denied: tokenCustomerId={} tried to access customerId={}", tokenCustomerId, customerId);
			throw new AccessDeniedException("Forbidden");
		}

		log.debug("Access granted: tokenCustomerId={} matches requested customerId={}", tokenCustomerId, customerId);
	}

	@GetMapping
	public List<Asset> list(@RequestParam UUID customerId) {
		log.info("Received request to list assets for customerId={}", customerId);
		checkAccess(customerId);

		try {
			List<Asset> assets = assetService.listAssets(customerId);
			log.info("Returning {} assets for customerId={}", assets.size(), customerId);
			return assets;
		} catch (Exception ex) {
			log.error("Unexpected error while listing assets for customerId={}", customerId, ex);
			throw ex;
		}
	}
}
