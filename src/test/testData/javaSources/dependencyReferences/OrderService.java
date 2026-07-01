package com.example.domain.order;

class OrderService extends com.example.infrastructure.persistence.BaseRepository
        implements com.example.adapter.ExternalPort {
    private com.example.infrastructure.persistence.OrderJpaRepository repository;

    com.example.infrastructure.persistence.OrderDto map(com.example.adapter.ExternalRequest request) {
        return new com.example.infrastructure.persistence.OrderDto();
    }
}
