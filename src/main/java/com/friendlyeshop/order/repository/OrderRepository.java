package com.friendlyeshop.order.repository;

import com.friendlyeshop.order.model.Order;
import com.friendlyeshop.order.model.enums.OrderStatus;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Order o SET o.status = :target WHERE o.id = :orderId AND o.status = :source")
    int transitionStatus(
            @Param("orderId") UUID orderId,
            @Param("source") OrderStatus source,
            @Param("target") OrderStatus target);
}
