package com.fintech.brokerage.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.fintech.brokerage.service.OrderService;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
	
    private final OrderService orderService;
    
    public AdminController(OrderService orderService) {
    	this.orderService = orderService; 
    }

    @PostMapping("/orders/{id}/match")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> match(@PathVariable UUID id) {
        orderService.match(id);
        return ResponseEntity.noContent().build();
    }
}
