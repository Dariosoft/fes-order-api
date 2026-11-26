# Tasks 002 — Alta de pedido con outbox y publicación de order.placed

Tareas pequeñas (20–30 min), en orden de dependencia. Cada tarea indica los RF que cubre y una condición verificable de finalización.

## Fase A — Esquema y modelo de persistencia

- [x] **T1. Crear migración `V2__add_order_currency_and_items.sql`.**
  Cubre: RF-4, RF-17.
  Done when: al aplicar Flyway sobre una base PostgreSQL limpia se añaden `orders.currency` con `CHECK (currency IN ('ARS','USD'))`, el `CHECK` de estados y la tabla `order_items` con PK `(order_id, product_id)`, sus checks e índices, sin editar `V1__create_orders.sql`.

- [x] **T2. Crear migración `V3__create_order_outbox.sql`.**
  Cubre: RF-6, RF-8, RF-9.
  Done when: Flyway crea `order_outbox` con `event_id`, `event_type`, `aggregate_id`, `payload JSONB`, `status` (`PENDING`/`PUBLISHED`), `occurred_at`, `created_at`, `published_at`, `attempts`, `next_attempt_at` y los índices `order_outbox_pending_idx` y `order_outbox_aggregate_id_idx`.

- [x] **T3. Crear enums `OrderStatus`, `Currency` y `OutboxStatus`.**
  Cubre: RF-4, RF-11, RF-12, RF-13, RF-14.
  Done when: existen exactamente `PENDING`, `STOCK_RESERVED`, `STOCK_REJECTED`; `ARS`, `USD`; y `PENDING`, `PUBLISHED`, y `./mvnw compile` pasa.

- [x] **T4. Crear entidades `Order` y `OrderItem`.**
  Cubre: RF-1, RF-5, RF-6, RF-11, RF-12.
  Done when: `Order` mapea `orders` con `id`, `tenantId`, `status`, `total`, `currency`, `createdAt` y colección de ítems; `OrderItem` mapea `order_items` con `orderId`, `productId`, `quantity`, `unitPrice`, `currency`; Hibernate valida el esquema al arrancar.

- [x] **T5. Crear entidad `OrderOutbox`.**
  Cubre: RF-6, RF-8, RF-9.
  Done when: `OrderOutbox` mapea `order_outbox` con `eventId`, `eventType`, `aggregateId`, `payload`, `status`, `occurredAt`, `createdAt`, `publishedAt`, `attempts`, `nextAttemptAt`, y Hibernate valida el esquema.

- [x] **T6. Crear `OrderRepository` y `OrderOutboxRepository`.**
  Cubre: RF-5, RF-6, RF-9, RF-11, RF-12, RF-14, RF-15, RF-16.
  Done when: `OrderRepository` expone la transición atómica `PENDING -> estado destino` devolviendo filas afectadas y la comprobación de existencia; `OrderOutboxRepository` expone la lectura de pendientes por `next_attempt_at` y el marcado `PUBLISHED`; compila y los tests de repositorio con mocks reflejan las firmas.

## Fase B — Contratos HTTP y mensajería

- [x] **T7. Crear DTOs de entrada `CreateOrderRequest` y `OrderItemRequest` con Bean Validation.**
  Cubre: RF-17.
  Done when: el body `{tenantId, items:[{productId, quantity, unitPrice, currency}]}` se valida con `tenantId` UUID obligatorio, `items` no vacío, `productId` UUID obligatorio, `quantity >= 1`, `unitPrice >= 0`, `currency` en `ARS`/`USD`.

- [x] **T8. Crear DTOs internos y de salida `CreateOrderDetails`, `OrderItemDetails`, `StockResultDetails`, `OrderResponse` y `ErrorResponse`.**
  Cubre: RF-2, RF-8, RF-11, RF-12, RF-16.
  Done when: existen con factorías `from(...)` donde corresponda y `OrderResponse` produce `{orderId, status, total, currency}`.

- [x] **T9. Crear `MessagingNames` y los payloads `OrderPlacedMessage`, `OrderPlacedItemMessage` y `StockResultMessage`.**
  Cubre: RF-7, RF-8, RF-11, RF-12.
  Done when: las constantes son `orders`, `order.placed`, `order.stock-results`, `order.placed`, `stock.reserved`, `stock.rejected`; `OrderPlacedMessage` contiene `eventId`, `eventType`, `occurredAt`, `orderId`, `tenantId`, `items`, `total`, `currency`; `StockResultMessage` contiene `eventId`, `eventType`, `orderId` y los campos opcionales de cada resultado.

## Fase C — Alta de pedido

- [x] **T10. Implementar `OrderTotalCalculator` con pruebas unitarias.**
  Cubre: RF-3.
  Done when: suma `cantidad × precio unitario` en `BigDecimal`, aplica `HALF_UP` a 2 decimales y los tests cubren redondeo (`3 × 10.005 = 30.02`) y total cero.

- [x] **T11. Implementar las validaciones de negocio del alta.**
  Cubre: RF-17, RF-18.
  Done when: una moneda mixta o un `productId` repetido lanzan `InvalidOrderException`; el servicio no invoca clientes de catálogo ni de cuenta y confía en el precio recibido.

- [x] **T12. Implementar `OrderService.create` con pedido, ítems y outbox en una sola transacción.**
  Cubre: RF-1, RF-4, RF-5, RF-6, RF-19.
  Done when: el método `@Transactional` guarda `Order` en `PENDING`, sus `OrderItem` y un `OrderOutbox` `PENDING` con `eventType=order.placed`; si una escritura falla, no queda pedido ni evento; no aplica idempotencia ni `Idempotency-Key`.

- [x] **T13. Implementar el manejo de errores 400.**
  Cubre: RF-17.
  Done when: `InvalidOrderException`, `MethodArgumentNotValidException` y errores de binding producen 400 con `ErrorResponse` y no se persiste nada.

- [x] **T14. Añadir `POST /orders` a `OrderController`.**
  Cubre: RF-1, RF-2.
  Done when: el endpoint delega en `OrderService`, responde 201 con `{orderId, status:"PENDING", total, currency}` y conserva `GET /orders`.

- [x] **T15. Añadir pruebas web del alta y de compatibilidad.**
  Cubre: RF-2, RF-17, RF-20.
  Done when: `MockMvc` verifica 201 con el contrato, 400 para cada regla de validación y que `GET /orders` responde igual bajo `/orders`; `./mvnw test` pasa.

## Fase D — Outbox y publicación

- [x] **T16. Implementar `OrderPlacedPublisher`.**
  Cubre: RF-7, RF-8, RF-10.
  Done when: publica en exchange `orders` con routing key `order.placed`, serializa el payload completo y una prueba unitaria verifica exchange, routing key y campos.

- [x] **T17. Configurar publisher confirms y returns en `RabbitConfig` y `application.yaml`.**
  Cubre: RF-7, RF-9, RF-20.
  Done when: `publisher-confirm-type=correlated`, `publisher-returns=true` y `template.mandatory=true` están activos; no se declaran exchanges, colas ni bindings y las variables de entorno actuales no cambian de significado.

- [x] **T18. Implementar `OutboxRelay` para publicar pendientes y marcar `PUBLISHED` con ACK.**
  Cubre: RF-7, RF-9, RF-10.
  Done when: un evento `PENDING` se publica, se espera confirmación y solo con ACK se marca `PUBLISHED` con `published_at`; una prueba con doble del publisher lo verifica.

- [x] **T19. Implementar el comportamiento del relay ante NACK, return, timeout o excepción.**
  Cubre: RF-9, RF-10.
  Done when: ante cualquier fallo el evento permanece `PENDING`, se incrementa `attempts`, se actualiza `next_attempt_at` con backoff acotado y el evento nunca se borra ni se marca como fallido definitivo.

- [x] **T20. Exponer métricas y logs del outbox.**
  Cubre: RF-9, RF-16 (observabilidad de pendientes y desconocidos).
  Done when: existen `orders_outbox_published_total`, `orders_outbox_publish_failures_total` y `orders_outbox_pending_count`, y los logs incluyen `orderId` y `eventId` sin exponer secretos.

## Fase E — Resultados de stock

- [x] **T21. Implementar `StockResultListener` sobre `order.stock-results`.**
  Cubre: RF-11, RF-12, RF-16, RF-22.
  Done when: `@RabbitListener` usa la cola configurable con default `order.stock-results`, deserializa `StockResultMessage`, obtiene el tipo de evento de la routing key recibida y delega en `StockResultService`; no declara topología. Ante fallo procesable cuenta `x-death` (sumando `count`) y hace NACK sin reencolar mientras el intento sea menor a 5; al quinto publica el mensaje original en `order.stock-results.dlq` y hace ACK.

- [x] **T22. Implementar la transición `PENDING -> STOCK_RESERVED`.**
  Cubre: RF-5, RF-11.
  Done when: un `stock.reserved` de un pedido `PENDING` deja el pedido en `STOCK_RESERVED` conservando tenant, ítems, precios, total y moneda.

- [x] **T23. Implementar la transición `PENDING -> STOCK_REJECTED` y su terminalidad.**
  Cubre: RF-12, RF-13.
  Done when: un `stock.rejected` de un pedido `PENDING` deja el pedido en `STOCK_REJECTED`; el `reason` solo se registra en log y ningún resultado posterior reabre ni cambia el pedido.

- [x] **T24. Ignorar reentregas y resultados contradictorios o fuera de orden.**
  Cubre: RF-13, RF-14, RF-15.
  Done when: repetir el mismo resultado no altera el estado; un resultado contrario después de la primera transición se ignora y prevalece el primero aplicado; la prueba cubre también ejecución concurrente simulada.

- [x] **T25. Tratar resultados de stock de pedidos desconocidos.**
  Cubre: RF-16.
  Done when: si el pedido no existe se hace ACK, se registra un log WARN y la métrica `orders_stock_results_unknown_total`, y no se actualiza ninguna fila.

- [x] **T26. Añadir pruebas unitarias del consumo de stock.**
  Cubre: RF-11, RF-12, RF-13, RF-14, RF-15, RF-16, RF-22.
  Done when: `StockResultServiceTest` cubre reserva, rechazo, reentrega, contradicción, concurrencia simulada y pedido desconocido; `StockResultListenerTest` cubre intentos 1–4 con NACK sin reencolar y el quinto intento con publicación en `order.stock-results.dlq` y ACK; `./mvnw test` pasa.

## Fase F — Cierre y verificación

- [x] **T27. Añadir propiedades del relay y del listener conservando las variables existentes.**
  Cubre: RF-20.
  Done when: `app.outbox.poll-interval-ms`, `app.outbox.batch-size`, `app.outbox.confirm-timeout-ms` y `app.rabbitmq.stock-results-queue` tienen defaults; `DATABASE_*`, `RABBITMQ_*` y `SERVER_PORT` mantienen su significado; `GET /orders` y Actuator no cambian.

- [x] **T28. Ejecutar Checkstyle, tests y verificación completa.**
  Cubre: todos los RF.
  Done when: `./mvnw verify` termina en verde, incluyendo Checkstyle, tests y validación de Flyway/Hibernate contra la base configurada.

- [ ] **T29. Aplicar `/clean-code-guard` y validar aislamiento y demo manual.**
  Cubre: RF-20, RF-21, RF-22 y todos los RF.
  Done when: no quedan hallazgos de la revisión; la demo manual confirma alta con broker arriba/abajo, publicación posterior, transiciones de stock, reentrega ignorada y pedido desconocido sin actualización; no hay escrituras en tablas de catálogo ni de pagos.

## Resumen

Total: 29 tareas. Orden: esquema y modelo (T1–T6), contratos (T7–T9), alta (T10–T15), outbox y publicación (T16–T20), consumo de stock (T21–T26) y cierre (T27–T29).
