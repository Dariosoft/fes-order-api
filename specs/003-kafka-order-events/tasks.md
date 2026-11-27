# Tasks 003 — Publicación de order.placed en Kafka desde el outbox

## Fase A — Dependencia y esquema

- [ ] **T1. Agregar `spring-kafka` y configuración.** Cubre RF-11, RF-12.
  Done when: `./mvnw compile` pasa y `KAFKA_BOOTSTRAP_SERVERS` tiene default local.
- [ ] **T2. Crear `V4__add_kafka_outbox_tracking.sql` y actualizar `OrderOutbox`.** Cubre RF-1.
  Done when: Flyway aplica la migración e Hibernate valida el esquema.
- [ ] **T3. Inicializar los campos Kafka en `OrderService.create`.** Cubre RF-1.
  Done when: un alta deja el outbox `PENDING` para Kafka en la misma transacción.

## Fase B — Publicación y relay

- [ ] **T4. Implementar `KafkaOrderPlacedPublisher`.** Cubre RF-2, RF-3.
  Done when: publica en `orders.events` con key `orderId` y el envelope completo.
- [ ] **T5. Implementar `KafkaOutboxRelay`.** Cubre RF-2, RF-5, RF-8, RF-9.
  Done when: publica pendientes en orden por pedido, marca `PUBLISHED` con ACK y aplica backoff.
- [ ] **T6. Independizar Kafka de RabbitMQ.** Cubre RF-7.
  Done when: una falla de Kafka no altera el estado RabbitMQ y viceversa.
- [ ] **T7. Exponer métricas y logs.** Cubre RF-10.
  Done when: existen publicadas, fallidas y pendientes, con `orderId`/`eventId` en logs.

## Fase C — Pruebas y cierre

- [ ] **T8. Unitarias de publisher y relay.** Cubre RF-2…RF-10.
  Done when: cubren ACK, excepción, timeout, backoff y no pérdida.
- [ ] **T9. Verificar contrato HTTP y RabbitMQ intactos.** Cubre RF-13, RF-14.
  Done when: `./mvnw verify` pasa y `POST/GET /orders` responden igual.
- [ ] **T10. Demo manual con Kafka arriba y abajo.** Cubre RF-4, RF-5, RF-6.
  Done when: el evento aparece en `orders.events` al volver Kafka.
