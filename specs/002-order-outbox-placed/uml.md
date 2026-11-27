# UML 002 — Alta de pedido con outbox y publicación de order.placed

Este documento describe el desarrollo planificado para `order-api` según `spec.md`, `plan.md` y `tasks.md`. Los identificadores de Mermaid conservan los nombres previstos de clases, endpoints, colas y variables. La topología RabbitMQ (exchange, colas, bindings, TTL y DLQ) pertenece a `infra`; aquí solo se muestran los nombres usados por este servicio.

## Flujo principal (secuencia)

```mermaid
sequenceDiagram
    autonumber
    actor Cliente
    participant Controller as OrderController<br/>POST /orders
    participant OrderService
    participant Total as OrderTotalCalculator
    participant OrderRepo as OrderRepository
    participant OutboxRepo as OrderOutboxRepository
    participant DB as PostgreSQL<br/>orders / order_items / order_outbox
    participant Relay as OutboxRelay
    participant Publisher as OrderPlacedPublisher
    participant Broker as RabbitMQ<br/>exchange orders<br/>routing key order.placed
    participant Listener as StockResultListener<br/>queue order.stock-results
    participant StockService as StockResultService

    Cliente->>Controller: POST /orders<br/>{tenantId, items:[{productId, quantity, unitPrice, currency}]}
    Controller->>OrderService: create(CreateOrderDetails)
    OrderService->>Total: calculate(items)
    Total-->>OrderService: total (HALF_UP, 2 decimales)
    OrderService->>OrderService: validar moneda única y producto no repetido
    rect rgb(235, 245, 255)
        Note over OrderService,DB: Una sola transacción @Transactional
        OrderService->>OrderRepo: save(Order status=PENDING, total, currency)
        OrderService->>OutboxRepo: save(OrderOutbox status=PENDING, eventType=order.placed)
    end
    OrderService-->>Controller: OrderResponse
    Controller-->>Cliente: 201 {orderId, status:"PENDING", total, currency}

    Note over Relay: @Scheduled fixedDelay<br/>app.outbox.poll-interval-ms
    Relay->>OutboxRepo: findPENDING(nextAttemptAt <= now)
    Relay->>Publisher: publish(OrderPlacedMessage)
    Publisher->>Broker: exchange "orders", routing key "order.placed"<br/>publisher confirms + mandatory
    alt ACK sin return
        Broker-->>Publisher: ACK
        Relay->>OutboxRepo: markPublished(eventId, publishedAt)
    else NACK, return, timeout o excepción
        Broker-->>Publisher: NACK / return / timeout
        Relay->>OutboxRepo: attempts++, nextAttemptAt (backoff)<br/>estado sigue PENDING
    end

    Note over Broker: catalog-api consume stock.reserve<br/>y publica el resultado en exchange stock
    Broker-->>Listener: stock.reserved / stock.rejected
    Listener->>StockService: apply(StockResultDetails)
    StockService->>OrderRepo: UPDATE orders SET status=destino<br/>WHERE id=orderId AND status='PENDING'
    alt 1 fila actualizada
        StockService-->>Listener: transición aplicada
    else 0 filas y el pedido existe
        StockService-->>Listener: resultado ignorado (ya no está PENDING)
    else 0 filas y el pedido no existe
        StockService-->>Listener: pedido desconocido<br/>log WARN + métrica, sin actualizar
    end
    Listener-->>Broker: ACK
```

## Estructura de clases

```mermaid
classDiagram
    class OrderController {
        +orders() Map
        +create(CreateOrderRequest) OrderResponse
    }
    class OrderService {
        +create(CreateOrderDetails) Order
    }
    class OrderTotalCalculator {
        +calculate(List~OrderItemDetails~) BigDecimal
    }
    class StockResultService {
        +apply(StockResultDetails) void
    }
    class OutboxRelay {
        +relayPending() void
    }
    class OrderPlacedPublisher {
        +publish(OrderPlacedMessage) void
    }
    class StockResultListener {
        +onMessage(StockResultMessage) void
    }
    class ApiExceptionHandler {
        +handleInvalid(InvalidOrderException) ErrorResponse
    }

    class OrderRepository {
        +save(Order) Order
        +transitionStatus(UUID, OrderStatus, OrderStatus) int
        +existsById(UUID) boolean
    }
    class OrderOutboxRepository {
        +findPending(int) List~OrderOutbox~
        +markPublished(UUID, Instant) void
    }

    class Order {
        UUID id
        UUID tenantId
        OrderStatus status
        BigDecimal total
        Currency currency
        Instant createdAt
        List~OrderItem~ items
    }
    class OrderItem {
        UUID orderId
        UUID productId
        int quantity
        BigDecimal unitPrice
        Currency currency
    }
    class OrderOutbox {
        UUID eventId
        String eventType
        UUID aggregateId
        String payload
        OutboxStatus status
        Instant occurredAt
        Instant createdAt
        Instant publishedAt
        int attempts
        Instant nextAttemptAt
    }

    class OrderStatus {
        <<enumeration>>
        PENDING
        STOCK_RESERVED
        STOCK_REJECTED
    }
    class Currency {
        <<enumeration>>
        ARS
        USD
    }
    class OutboxStatus {
        <<enumeration>>
        PENDING
        PUBLISHED
    }

    class CreateOrderRequest {
        UUID tenantId
        List~OrderItemRequest~ items
    }
    class OrderItemRequest {
        UUID productId
        int quantity
        BigDecimal unitPrice
        Currency currency
    }
    class CreateOrderDetails {
        UUID tenantId
        List~OrderItemDetails~ items
    }
    class OrderItemDetails {
        UUID productId
        int quantity
        BigDecimal unitPrice
        Currency currency
    }
    class StockResultDetails {
        String eventType
        UUID orderId
    }
    class OrderResponse {
        UUID orderId
        OrderStatus status
        BigDecimal total
        Currency currency
    }
    class ErrorResponse {
        String code
        String message
        List~String~ fields
    }
    class OrderPlacedMessage {
        UUID eventId
        String eventType
        Instant occurredAt
        UUID orderId
        UUID tenantId
        List~OrderPlacedItemMessage~ items
        BigDecimal total
        Currency currency
    }
    class OrderPlacedItemMessage {
        UUID productId
        int quantity
        BigDecimal unitPrice
        Currency currency
    }
    class StockResultMessage {
        UUID eventId
        String eventType
        UUID orderId
        Instant reservedAt
        String reason
        Instant rejectedAt
    }

    OrderController --> OrderService
    OrderController ..> CreateOrderRequest
    OrderController ..> OrderResponse
    OrderService --> OrderRepository
    OrderService --> OrderOutboxRepository
    OrderService --> OrderTotalCalculator
    OrderService ..> CreateOrderDetails
    OrderService ..> OrderPlacedMessage
    OrderRepository --> Order
    Order "1" *-- "many" OrderItem
    Order --> OrderStatus
    Order --> Currency
    OrderOutbox --> OutboxStatus
    OrderOutboxRepository --> OrderOutbox
    OutboxRelay --> OrderOutboxRepository
    OutboxRelay --> OrderPlacedPublisher
    OrderPlacedPublisher ..> OrderPlacedMessage
    StockResultListener --> StockResultService
    StockResultListener ..> StockResultMessage
    StockResultService --> OrderRepository
    StockResultService ..> StockResultDetails
    ApiExceptionHandler ..> ErrorResponse
```

## Contrato y configuración de referencia

- Endpoint existente conservado: `GET /orders` bajo el prefijo `/orders` que aplica `config/ApiPathConfig.java`.
- Endpoint nuevo: `POST /orders`.
  - Entrada: `{ "tenantId": "uuid", "items": [ { "productId": "uuid", "quantity": 1, "unitPrice": 10.00, "currency": "ARS" } ] }`.
  - Salida 201: `{ "orderId": "uuid", "status": "PENDING", "total": 10.00, "currency": "ARS" }`.
  - Salida 400: `ErrorResponse` con código, mensaje y campos inválidos.
- Evento publicado: `order.placed` con `eventId`, `eventType`, `occurredAt`, `orderId`, `tenantId`, `items` (`productId`, `quantity`, `unitPrice`, `currency`), `total`, `currency`.
- Mensajes consumidos desde `order.stock-results`: `stock.reserved` (`eventId`, `orderId`, `reservedAt`) y `stock.rejected` (`eventId`, `orderId`, `reason`, `rejectedAt`).
- Persistencia: `orders`, `order_items` y `order_outbox` en la base PostgreSQL `orders`; migraciones nuevas `V2__add_order_currency_and_items.sql` y `V3__create_order_outbox.sql` sin editar `V1__create_orders.sql`.
- Configuración: se reutilizan `DATABASE_*`, `RABBITMQ_*`, `SERVER_PORT` y Actuator; se añaden defaults para `app.outbox.poll-interval-ms`, `app.outbox.batch-size`, `app.outbox.confirm-timeout-ms` y `app.rabbitmq.stock-results-queue` (`order.stock-results`).
- Estados: `PENDING -> STOCK_RESERVED` y `PENDING -> STOCK_REJECTED`; este último es terminal en la Fase 1.
