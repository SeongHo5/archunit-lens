package com.example.domain;

import com.example.infrastructure.OrderJpaRepository;

class OrderService {
    private final OrderJpaRepository repository = new OrderJpaRepository();
}
