package com.fintech.brokerage.service;

import java.util.UUID;
import com.fintech.brokerage.enums.Role;

public interface JwtService {

	public String issueToken(UUID customerId, String username, Role role);
}
