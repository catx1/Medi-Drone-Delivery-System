package org.example.cw3ilp.api.repository;

import org.example.cw3ilp.entity.DeliveryOrder;
import org.example.cw3ilp.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeliveryOrderRepository extends JpaRepository<DeliveryOrder, Long> {
    Optional<DeliveryOrder> findByOrderNumber(String orderNumber);
    List<DeliveryOrder> findByStatusOrderByCreatedAtAsc(OrderStatus status);
    Optional<DeliveryOrder> findByPickupCode(String pickupCode);
}
