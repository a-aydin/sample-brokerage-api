package com.fintech.brokerage.config;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.fintech.brokerage.entity.Asset;
import com.fintech.brokerage.entity.Customer;
import com.fintech.brokerage.repo.AssetRepository;
import com.fintech.brokerage.repo.CustomerRepository;

@Configuration
public class DataInitializer {
    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    CommandLineRunner seed(CustomerRepository customers, AssetRepository assetRepository, PasswordEncoder encoder) {
        return args -> {
            if (customers.count() == 0) {
                Customer customer1 = new Customer("alice", encoder.encode("alice123"));
                customers.save(customer1);
                Customer customer2 = new Customer("bob", encoder.encode("bob123"));
                customers.save(customer2);
                Customer customer3 = new Customer("John", encoder.encode("john123"));
                customers.save(customer3);
                log.info("Seeded customers: alice, bob, John");
            
            
	            // Default Asset verileri
	            Asset asset1 = new Asset(customer1, "TRY", new BigDecimal("500.0000"), new BigDecimal("500.0000"));
	            Asset asset2 = new Asset(customer2, "GOOGL", new BigDecimal("50.0000"), new BigDecimal("50.0000"));
	            Asset asset3 = new Asset(customer3, "TSLA", new BigDecimal("75.5000"), new BigDecimal("75.5000"));
	            log.info("Seeded assets: TRY, GOOGL, TSLA");
	            
	            assetRepository.save(asset1);
	            assetRepository.save(asset2);
	            assetRepository.save(asset3);
            }
        };
    }
}
