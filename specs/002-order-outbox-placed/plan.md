# Plan 002 — Alta de pedido con outbox y publicación de order.placed

## 1. Resumen de la solución

Implementar en `order-api` un alta mínima de pedido (`POST /orders`) que, en una única transacción de base de datos, persiste el pedido con sus ítems y un evento `order.placed` en una tabla outbox. Un relay programado lee los eventos pendientes y los publica en RabbitMQ (exchange `orders`, routing key `order.placed`) con publisher confirms; si el broker está caído, el alta igual responde 201 y el evento permanece pendiente hasta que la publicación sea confirmada. Un listener de la cola `order.stock-results` aplica `stock.reserved` y `stock.rejected` mediante una transición condicional desde `PENDING`, de modo que reentregas, resultados contradictorios y mensajes concurrentes no corrompen el pedido. `GET /orders`, Actuator, health checks y las variables de entorno actuales se conservan.

Se respeta `AGENTS.md`: Java 25, Spring Boot 4.1, paquete `com.friendlyeshop.order`, estructura Layered, JPA con Hibernate en modo `validate`, Flyway para esquema, RabbitMQ como única mensajería y sin escribir datos de catálogo ni de pagos. La implementación seguirá `/spring-boot-project-creator` en su opción Layered, con `/spring-boot-layered-template` como complemento para `messaging/`, `event/` y `constants/`, y terminará con `/clean-code-guard` y `./mvnw verify`.

## 2. Arquitectura y paquetes

### 2.1 Estructura por capas

- `controller/OrderController.java`: conserva `GET /orders` y añade `POST /orders`. Delega el alta en `service/` y construye la respuesta HTTP; no accede a repositorios.
- `service/OrderService.java`: caso de uso de alta. Valida reglas de negocio, calcula el total, crea el pedido y el evento outbox dentro de una transacción.
- `service/StockResultService.java`: aplica resultados de stock con transición condicional e idempotente; distingue pedido desconocido, pedido ya transicionado y transición aplicada.
- `service/OrderTotalCalculator.java`: cálculo y redondeo del total con `BigDecimal`.
- `repository/OrderRepository.java`: acceso JPA al pedido y transición atómica de estado.
- `repository/OrderOutboxRepository.java`: lectura de eventos pendientes y marcado como publicado.
- `model/Order.java`, `model/OrderItem.java`, `model/OrderOutbox.java`: entidades JPA.
- `model/enums/OrderStatus.java`, `model/enums/Currency.java`, `model/enums/OutboxStatus.java`: estados y monedas tipadas.
- `model/dto/request/CreateOrderRequest.java`, `model/dto/request/OrderItemRequest.java`: contrato HTTP de entrada con Bean Validation.
- `model/dto/in/CreateOrderDetails.java`, `model/dto/in/OrderItemDetails.java`, `model/dto/in/StockResultDetails.java`: comandos/resultados internos ya validados.
- `model/dto/out/OrderResponse.java`, `model/dto/out/ErrorResponse.java`: contrato HTTP de salida.
- `messaging/rabbit/OrderPlacedMessage.java`, `messaging/rabbit/OrderPlacedItemMessage.java`: payload del evento `order.placed`.
- `messaging/rabbit/StockResultMessage.java`: payload de entrada de `stock.reserved`/`stock.rejected`.
- `messaging/rabbit/OrderPlacedPublisher.java`: publicación con confirms hacia `orders`/`order.placed`.
- `messaging/rabbit/StockResultListener.java`: `@RabbitListener` de la cola `order.stock-results`, con ACK/NACK y política de reintentos.
- `messaging/rabbit/StockResultDeadLetterPublisher.java`: publica el mensaje agotado en `order.stock-results.dlq` y permite el ACK.
- `messaging/rabbit/RetryAttempts.java`: calcula el intento actual a partir de la cabecera `x-death`.
- `messaging/outbox/OutboxRelay.java`: relay programado que publica pendientes y actualiza el outbox.
- `config/OutboxConfig.java`: `@EnableScheduling` y propiedades del relay.
- `config/RabbitConfig.java`: confirmaciones, returns y `mandatory` del `RabbitTemplate`; no declara exchanges, colas ni bindings, que pertenecen a `infra`.
- `constants/MessagingNames.java`: nombres estables del contrato RabbitMQ y tipos de evento.
- `exception/InvalidOrderException.java`, `exception/ApiExceptionHandler.java`: rechazo de altas inválidas y respuesta de error.

`GET /orders` no cambia de ruta ni de forma de respuesta; `ApiPathConfig` sigue publicando todos los controladores bajo `/orders`.

### 2.2 Flujo de alta y outbox (RF-1, RF-2, RF-5, RF-6, RF-7, RF-9, RF-10)

1. `POST /orders` enlaza `CreateOrderRequest` y valida formato con Bean Validation.
2. `OrderService` valida reglas de negocio (moneda única, producto no repetido) y calcula el total.
3. En una única transacción se guarda `Order` con estado `PENDING`, sus `OrderItem` y un `OrderOutbox` con estado `PENDING`, `eventId` generado, `occurredAt`, `orderId`, `eventType=order.placed` y el payload completo.
4. La transacción confirma y el controlador responde 201 con `orderId`, `status`, `total` y `currency`.
5. El relay, en un hilo separado, publica el evento y solo marca `PUBLISHED` cuando recibe confirmación del broker.

### 2.3 Flujo de resultados de stock (RF-11, RF-12, RF-13, RF-14, RF-15, RF-16)

1. `StockResultListener` consume de `order.stock-results`.
2. `StockResultService` ejecuta una actualización condicional `PENDING -> STOCK_RESERVED` o `PENDING -> STOCK_REJECTED`.
3. Si actualiza una fila, la transición es la primera y se aplica.
4. Si no actualiza ninguna fila, consulta si el pedido existe: si no existe es desconocido (ACK, log y métrica); si existe ya no está en `PENDING`, por lo que el resultado se ignora.
5. `STOCK_REJECTED` es terminal; ningún resultado posterior lo reabre.

## 3. Modelo de datos y migraciones Flyway

No se edita `V1__create_orders.sql`. Se añaden migraciones nuevas:

### 3.1 `V2__add_order_currency_and_items.sql`

- Añadir `orders.currency VARCHAR(3) NOT NULL` con `CHECK (currency IN ('ARS','USD'))`. Para filas preexistentes se usa un valor por defecto seguro y se retira el default tras el backfill.
- Añadir `CHECK` de `orders.status IN ('PENDING','STOCK_RESERVED','STOCK_REJECTED')`.
- Crear `order_items`:
  - `order_id UUID NOT NULL` con FK a `orders(id)` `ON DELETE CASCADE`.
  - `product_id UUID NOT NULL`.
  - `quantity INTEGER NOT NULL CHECK (quantity > 0)`.
  - `unit_price NUMERIC(19,2) NOT NULL CHECK (unit_price >= 0)`.
  - `currency VARCHAR(3) NOT NULL CHECK (currency IN ('ARS','USD'))`.
  - Clave primaria compuesta `(order_id, product_id)` para impedir productos repetidos en el mismo pedido.
- Índice `order_items_order_id_idx` sobre `order_id`.

### 3.2 `V3__create_order_outbox.sql`

- Crear `order_outbox`:
  - `event_id UUID PRIMARY KEY`.
  - `event_type VARCHAR(50) NOT NULL`.
  - `aggregate_id UUID NOT NULL` (identificador del pedido).
  - `payload JSONB NOT NULL` (evento `order.placed` completo).
  - `status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','PUBLISHED'))`.
  - `occurred_at TIMESTAMPTZ NOT NULL`.
  - `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`.
  - `published_at TIMESTAMPTZ NULL`.
  - `attempts INTEGER NOT NULL DEFAULT 0`.
  - `next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`.
- Índice `order_outbox_pending_idx` sobre `(status, next_attempt_at)`.
- Índice `order_outbox_aggregate_id_idx` sobre `aggregate_id`.

Hibernate solo valida; el esquema lo crean las migraciones. El payload JSONB se serializa con el mismo contrato que el mensaje publicado.

## 4. API `POST /orders` (RF-1, RF-2, RF-17)

- Ruta: `POST /orders` (prefijo ya aplicado por `ApiPathConfig`).
- Body:
  ```json
  { "tenantId": "uuid", "items": [ { "productId": "uuid", "quantity": 1, "unitPrice": 10.00, "currency": "ARS" } ] }
  ```
- Respuesta 201:
  ```json
  { "orderId": "uuid", "status": "PENDING", "total": 10.00, "currency": "ARS" }
  ```
- Rechazo de validación: 400 con `ErrorResponse` (código, mensaje y lista de campos inválidos) sin persistir pedido ni evento.
- El controlador no conoce JPA ni RabbitMQ; solo `OrderService` y `OrderResponse.from(...)`.

## 5. Cálculo de total y moneda (RF-3)

- `OrderTotalCalculator` recibe la lista de ítems validados.
- Suma `BigDecimal` de `quantity * unitPrice` para cada ítem.
- Aplica `setScale(2, RoundingMode.HALF_UP)` una sola vez sobre la suma final.
- La moneda del pedido es la moneda única de todos los ítems; si hay más de una, la solicitud se rechaza.
- `total` se persiste en `orders.total NUMERIC(19,2)` y se devuelve en la respuesta y en `order.placed`.

## 6. Validaciones (RF-17, RF-18)

Bean Validation en los DTO de request:

- `tenantId`: `@NotNull` UUID válido.
- `items`: `@NotEmpty`.
- `productId`: `@NotNull` UUID válido.
- `quantity`: `@NotNull`, `@Min(1)`.
- `unitPrice`: `@NotNull`, `@DecimalMin("0.00")`.
- `currency`: `@NotNull`, restringida a `ARS` o `USD`.

Reglas de negocio en `OrderService`:

- Todos los ítems comparten la misma moneda.
- No hay `productId` repetido en el mismo pedido; refuerzo con la PK compuesta de `order_items`.
- No se consultan `catalog-api` ni `account-api`; el precio acordado y la existencia del tenant/producto se confían al cliente en la Fase 1.

## 7. Outbox transaccional (RF-5, RF-6, RF-9)

- `OrderService.create(...)` se anota con `@Transactional`.
- Dentro de la misma transacción:
  - Construir `Order` con `id` UUID generado, `tenantId`, `status=PENDING`, `total`, `currency`, `createdAt`.
  - Construir `OrderItem` por cada ítem.
  - Construir `OrderOutbox` con `eventId` UUID, `eventType=order.placed`, `aggregateId=order.id`, `payload` serializado, `status=PENDING`, `occurredAt=ahora`, `attempts=0`.
- Si falla cualquier escritura, la transacción revierte y no queda pedido ni evento.
- El fallo del broker no forma parte de la transacción: el evento ya quedó `PENDING` y el relay lo reintenta.

## 8. Relay y publicación RabbitMQ (RF-7, RF-8, RF-9, RF-10)

- `OutboxRelay` se ejecuta con `@Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:5000}")`.
- Lee un lote de eventos `PENDING` con `next_attempt_at <= now`, ordenados por `occurred_at`, con tamaño `app.outbox.batch-size` (por defecto 50).
- Por cada evento:
  - Serializa/publica el payload en exchange `orders` con routing key `order.placed`.
  - Usa `RabbitTemplate` con `publisher-confirm-type=correlated`, `publisher-returns=true` y `template.mandatory=true`.
  - Espera la confirmación del broker con un timeout configurable.
  - Si recibe ACK y no hay return, marca `PUBLISHED` con `published_at`.
  - Si hay NACK, return, timeout o excepción, incrementa `attempts`, calcula un `next_attempt_at` con backoff exponencial acotado y deja el evento `PENDING`; nunca lo borra ni lo marca como fallido definitivo.
- La publicación es al menos una vez: un fallo entre el ACK del broker y el marcado en base puede provocar una nueva publicación; los consumidores deben tolerar duplicados.
- No se implementa plazo máximo ni descarte de pendientes. La antigüedad de los pendientes se expone como métrica.
- No se declaran exchanges, colas, bindings, TTL ni DLQ; solo se usan los nombres acordados con `infra`.

Payload `order.placed`:

```json
{
  "eventId": "uuid",
  "eventType": "order.placed",
  "occurredAt": "2026-11-25T12:00:00Z",
  "orderId": "uuid",
  "tenantId": "uuid",
  "items": [ { "productId": "uuid", "quantity": 1, "unitPrice": 10.00, "currency": "ARS" } ],
  "total": 10.00,
  "currency": "ARS"
}
```

## 9. Consumo de `order.stock-results` (RF-11, RF-12, RF-13, RF-14, RF-15, RF-16, RF-22)

- `StockResultListener` con `@RabbitListener(queues = "${app.rabbitmq.stock-results-queue:order.stock-results}")`.
- Deserializa `StockResultMessage` con `eventId`, `orderId` y campos opcionales `reservedAt`, `reason`, `rejectedAt`. El tipo de evento (`stock.reserved` o `stock.rejected`) se obtiene de la routing key recibida, porque el contrato del cuerpo no incluye `eventType`.
- `StockResultService.apply(...)`:
  - `stock.reserved`: intenta `PENDING -> STOCK_RESERVED`.
  - `stock.rejected`: intenta `PENDING -> STOCK_REJECTED`; el `reason` solo se registra en log y no altera el estado.
- La transición se implementa como `UPDATE orders SET status = :to WHERE id = :id AND status = 'PENDING'` con `@Modifying` y retorno de filas afectadas.
- Si se actualiza una fila: transición aplicada y ACK.
- Si no se actualiza:
  - Si `orderRepository.existsById(id)` es falso: pedido desconocido; ACK, log WARN y métrica `orders_stock_results_unknown_total`; no se actualiza nada.
  - Si existe: ya no está en `PENDING`; se ignora el resultado, se registra en log DEBUG/INFO y se hace ACK.
- Esto garantiza idempotencia y primera transición ganadora incluso ante concurrencia, porque la condición `status='PENDING'` se evalúa de forma atómica en base de datos.
- `STOCK_REJECTED` es terminal; cualquier resultado posterior cae en la rama "ya no está en PENDING" y se ignora.
- Errores de deserialización o fallos inesperados no se confirman; se aplica la política de reintentos y DLQ descrita en §9.1.

### 9.1 Contrato de reintentos y DLQ (dependencia con `fes-infra`)

- `fes-infra` declara `order.stock-results.retry` (TTL 30 s) y `order.stock-results.dlq` (retención 14 días).
- Ante un fallo procesable (deserialización, base de datos o publicación), `StockResultListener` cuenta los ciclos con la cabecera `x-death`: `intentos = suma de count de las entradas de x-death de order.stock-results.retry + 1`.
- Si `intentos < 5`, hace NACK con `requeue = false`; la topología lo envía a la cola de reintento y vuelve a `order.stock-results` tras 30 s.
- Si `intentos >= 5`, `StockResultDeadLetterPublisher` publica el mensaje original en `order.stock-results.dlq` por el exchange por defecto y luego se hace ACK, para no reintentar indefinidamente.
- `order-api` no declara colas, exchanges, bindings, TTL ni DLQ; solo implementa el conteo y el envío final a la DLQ.

## 10. Configuración y compatibilidad operativa (RF-20, RF-21)

En `application.yaml` se añade solo lo necesario, manteniendo las variables actuales:

- `spring.rabbitmq.publisher-confirm-type: correlated`.
- `spring.rabbitmq.publisher-returns: true`.
- `spring.rabbitmq.template.mandatory: true`.
- `spring.rabbitmq.listener.simple.acknowledge-mode: manual`.
- Propiedades opcionales con default: `app.outbox.poll-interval-ms`, `app.outbox.batch-size`, `app.outbox.confirm-timeout-ms`, `app.rabbitmq.stock-results-queue`.
- No se añaden secretos nuevos; se reutilizan `RABBITMQ_*`, `DATABASE_*` y `SERVER_PORT`.
- No se modifican `GET /orders`, los grupos de health, Prometheus ni `ApiPathConfig`.
- `order-api` sigue sin escribir tablas de catálogo o pagos; solo `orders`, `order_items` y `order_outbox`.

## 11. Observabilidad

- Métricas Micrometer:
  - `orders_created_total`.
  - `orders_stock_results_total{result="STOCK_RESERVED|STOCK_REJECTED"}`.
  - `orders_stock_results_ignored_total`.
  - `orders_stock_results_unknown_total`.
  - `orders_outbox_published_total`.
  - `orders_outbox_publish_failures_total`.
  - `orders_outbox_pending_count` (gauge calculado sobre la tabla outbox).
- Logs estructurados con `orderId`, `eventId`, `eventType` y resultado de la transición.
- No se añade el outbox al readiness para no alterar el comportamiento actual de Kubernetes; el monitoreo de pendientes es por métrica y logs.

## 12. Manejo de errores

- `InvalidOrderException` para violaciones de reglas de negocio (moneda mixta, producto repetido).
- `MethodArgumentNotValidException` y errores de binding se traducen a 400 con `ErrorResponse`.
- Errores de persistencia en el alta revierten la transacción y devuelven 5xx; no dejan pedido ni evento.
- Fallos de publicación no afectan la respuesta 201 ya emitida; se registran y el evento queda `PENDING`.
- Resultados de stock de pedidos desconocidos no son errores: ACK, log y métrica.
- No se inventan compensaciones ni reintentos de stock; pertenecen a `catalog-api`.

## 13. Pruebas

- Unitarias (`OrderTotalCalculatorTest`): suma, moneda única, redondeo HALF_UP a 2 decimales, incluidos casos como `3 × 10.005 = 30.02`.
- Unitarias (`OrderServiceTest`): validaciones de negocio, creación de pedido `PENDING`, persistencia de ítems y outbox `PENDING` en la misma transacción (repositorios mockeados).
- Unitarias (`StockResultServiceTest`): `stock.reserved` y `stock.rejected` aplicados desde `PENDING`; reentrega, resultado contradictorio, pedido desconocido y `STOCK_REJECTED` terminal.
- Unitarias (`StockResultListenerTest`): con `x-death` de 0 a 3 hace NACK sin reencolar; con 4 o más publica en `order.stock-results.dlq` y hace ACK; la deserialización inválida sigue el mismo camino.
- Unitarias (`OutboxRelayTest`): publica y marca `PUBLISHED` con ACK; ante NACK, return, timeout o excepción mantiene `PENDING`, incrementa intentos y aplica backoff; nunca borra.
- Unitarias (`OrderPlacedPublisherTest`): exchange `orders`, routing key `order.placed`, payload con todos los campos del contrato.
- Web (`OrderControllerWebTest`): `POST /orders` devuelve 201 con el contrato esperado; solicitudes inválidas devuelven 400 sin persistir; `GET /orders` conserva su respuesta y su prefijo.
- Verificación de esquema: `./mvnw verify` ejecuta Checkstyle, tests y validación de Flyway contra la base configurada; no se añaden dependencias de test nuevas sin acordarlo.
- No se agregan tests de topología RabbitMQ; esa responsabilidad es de `infra`.

## 14. Trazabilidad RF → plan

| RF | Componentes / secciones |
|---|---|
| RF-1 | §2.1, §2.2, §4 |
| RF-2 | §2.2, §4 |
| RF-3 | §5 |
| RF-4 | §2.2, §3.1, §7 |
| RF-5 | §2.3, §9 |
| RF-6 | §2.2, §3.2, §7 |
| RF-7 | §8 |
| RF-8 | §8 (payload) |
| RF-9 | §2.2, §7, §8 |
| RF-10 | §8 |
| RF-11 | §2.3, §9 |
| RF-12 | §2.3, §9 |
| RF-13 | §2.3, §9 |
| RF-14 | §9 |
| RF-15 | §9 |
| RF-16 | §9, §11 |
| RF-17 | §4, §6 |
| RF-18 | §6 |
| RF-19 | §4 (sin idempotencia ni `Idempotency-Key`) |
| RF-20 | §2.1, §10 |
| RF-21 | §3, §10 |
| RF-22 | §9.1 |

## 15. Orden de implementación

1. Migraciones `V2` y `V3` y entidades/enums JPA.
2. DTOs de request/response y validaciones.
3. `OrderTotalCalculator` y `OrderService` con outbox transaccional.
4. `POST /orders` en `OrderController` y manejo de errores.
5. `OrderPlacedPublisher`, `OutboxRelay` y configuración de confirms.
6. `StockResultListener` y `StockResultService` con transición condicional.
7. Métricas, logs y configuración de `application.yaml`.
8. Tests unitarios y web; `./mvnw verify`; `/clean-code-guard`.

## 16. Riesgos y supuestos

- Se asume que `infra` declara el exchange `orders`, la cola `order.stock-results`, sus bindings y la política de reintentos/DLQ; `order-api` solo publica y consume.
- Se asume una única base PostgreSQL `orders` con Flyway habilitado.
- La entrega al menos una vez puede duplicar `order.placed`; los consumidores deben ser idempotentes.
- La transición condicional en base de datos es la garantía de primera transición ganadora; no se usa bloqueo optimista adicional.
- El relay puede publicar duplicados si el proceso cae entre la confirmación del broker y el marcado `PUBLISHED`; es aceptable en la Fase 1.
- No se añaden dependencias nuevas (por ejemplo Testcontainers) sin acuerdo previo; las pruebas usan el stack existente.
- Los eventos pendientes no se descartan nunca; su crecimiento se controla con monitoreo y la topología de `infra`.
