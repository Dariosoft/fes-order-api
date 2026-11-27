# Plan 003 — Publicación de order.placed en Kafka desde el outbox

## 1. Resumen

Extender el outbox existente con un segundo estado de publicación para Kafka. El alta sigue
guardando pedido y evento en una transacción; el relay de RabbitMQ no cambia y un nuevo
`KafkaOutboxRelay` publica los mismos eventos en el topic `orders.events` con key `orderId`. La
publicación a Kafka es at-least-once, tolera Kafka caído y no afecta el 201 ni el flujo RabbitMQ.
Cubre RF-1 … RF-14.

## 2. Dependencia y configuración

- Agregar `spring-kafka` (vía `spring-boot-starter-kafka` si el BOM de Spring Boot lo expone; si no,
  `org.springframework.kafka:spring-kafka`).
- `application.yaml`:
  - `spring.kafka.bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}`
  - `spring.kafka.producer.acks: all`
  - `spring.kafka.producer.properties.enable.idempotence: true`
  - `spring.kafka.producer.key-serializer` y `value-serializer` String.
- Propiedades `app.kafka.topic=orders.events`, `app.kafka.poll-interval-ms`,
  `app.kafka.batch-size`, `app.kafka.send-timeout-ms`.

## 3. Esquema

Nueva migración `V4__add_kafka_outbox_tracking.sql` sobre `order_outbox`:

- `kafka_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (kafka_status IN ('PENDING','PUBLISHED'))`.
- `kafka_published_at TIMESTAMPTZ NULL`.
- `kafka_attempts INTEGER NOT NULL DEFAULT 0`.
- `kafka_next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`.
- Índice `order_outbox_kafka_pending_idx (kafka_status, kafka_next_attempt_at)`.

No se edita `V3`. La entidad `OrderOutbox` suma los campos y métodos de marcado.

## 4. Payload y publicación

- Reutilizar `OrderPlacedMessage` y su codec JSON como payload del mensaje Kafka.
- `KafkaOrderPlacedPublisher` publica en `orders.events` con key `orderId.toString()` y espera el
  resultado del `send` con timeout.
- El mensaje incluye `eventId`, `eventType`, `occurredAt`, `orderId`, `tenantId`, `items`, `total`
  y `currency` (RF-3).

## 5. Relay

`KafkaOutboxRelay`:

- `@Scheduled(fixedDelayString = "${app.kafka.poll-interval-ms:5000}")`.
- Lee un lote de filas con `kafka_status='PENDING'` y `kafka_next_attempt_at <= now`, ordenadas por
  `occurred_at` y `event_id` (RF-8).
- Publica cada evento con key `orderId`; con ACK marca `PUBLISHED` y `kafka_published_at`.
- Ante excepción o timeout, incrementa `kafka_attempts`, calcula backoff exponencial acotado y deja
  `PENDING` (RF-9).
- Reutiliza `OutboxBackoff`.
- Publica duplicados si el proceso cae entre ACK y marcado; es aceptable (RF-6).

## 6. Independencia de RabbitMQ

- `OutboxRelay` conserva su lógica y su estado (`status`, `attempts`, `next_attempt_at`).
- Los nuevos campos de Kafka son independientes: un evento puede estar publicado en RabbitMQ y
  pendiente en Kafka, o al revés (RF-7).
- El alta `OrderService.create` no cambia salvo inicializar los campos Kafka en el constructor de
  `OrderOutbox`.

## 7. Observabilidad

- Métricas `orders_kafka_published_total`, `orders_kafka_publish_failures_total`,
  `orders_kafka_pending_count`.
- Logs con `orderId` y `eventId`, sin secretos.
- No se agrega Kafka al readiness para no alterar Kubernetes (RF-13).

## 8. Fuera de alcance

- Consumidores (RF-14).
- Declaración de topics (RF-12).
- Cambios de contrato HTTP (RF-13).

## 9. Trazabilidad RF → plan

| RF | Sección |
|---|---|
| RF-1 | §3, §6 |
| RF-2, RF-3 | §4 |
| RF-4, RF-5 | §5 |
| RF-6 | §5 |
| RF-7 | §6 |
| RF-8 | §5 |
| RF-9 | §5 |
| RF-10 | §7 |
| RF-11 | §2 |
| RF-12 | §8 |
| RF-13 | §6, §7 |
| RF-14 | §8 |
