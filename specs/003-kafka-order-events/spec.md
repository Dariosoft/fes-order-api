# Spec 003 — Publicación de order.placed en Kafka desde el outbox

## Contexto y objetivo

La Fase 1 ya registra el pedido y su evento `order.placed` en el outbox y lo publica en RabbitMQ
para el comando de stock. La Fase 2 necesita el mismo evento como log de dominio en Kafka para que
`analytics-api` y `notifications-api` construyan proyecciones, feed y métricas. Este corte agrega la
publicación en Kafka reutilizando el outbox, sin perder el evento si Kafka no está disponible y sin
alterar el flujo RabbitMQ ni el contrato HTTP.

## Usuarios / actores

- Cliente que da de alta un pedido.
- `analytics-api`, que consume `orders.events` para timeline y métricas.
- `notifications-api`, que consume `orders.events` para el feed en vivo.
- Equipo de operaciones, que monitorea el outbox y los reintentos.

## Historias de usuario

- H1: Como analítica quiero recibir `order.placed` en Kafka para proyectar ventas.
- H2: Como notificaciones quiero recibir `order.placed` en Kafka para el feed en vivo.
- H3: Como negocio quiero que un evento no se pierda si Kafka está caído.
- H4: Como operador quiero que la publicación a Kafka no rompa la publicación a RabbitMQ ni el alta.

## Requisitos funcionales (criterios de aceptación en EARS)

- RF-1: CUANDO se registre un pedido, EL SISTEMA almacenará su evento `order.placed` para RabbitMQ y para Kafka de forma atómica con el pedido.
- RF-2: CUANDO el evento esté almacenado y Kafka disponible, EL SISTEMA publicará `order.placed` en el topic `orders.events` con key `orderId`.
- RF-3: EL SISTEMA incluirá en el mensaje Kafka `eventId`, `eventType`, `occurredAt`, `orderId`, `tenantId`, `items`, `total` y `currency`.
- RF-4: SI Kafka no está disponible, ENTONCES EL SISTEMA responderá 201 al alta y conservará el evento pendiente para Kafka.
- RF-5: CUANDO Kafka vuelva a estar disponible, EL SISTEMA publicará los eventos pendientes sin descartarlos.
- RF-6: EL SISTEMA publicará a Kafka con garantía al menos una vez, admitiendo duplicados.
- RF-7: EL SISTEMA conservará la publicación existente a RabbitMQ y su estado independientemente de la publicación a Kafka.
- RF-8: EL SISTEMA mantendrá el orden de publicación por `orderId` dentro del outbox.
- RF-9: SI la publicación a Kafka falla, ENTONCES EL SISTEMA incrementará los intentos y reprogramará el evento con backoff acotado.
- RF-10: EL SISTEMA expondrá métricas de publicaciones exitosas, fallidas y pendientes de Kafka.
- RF-11: EL SISTEMA obtendrá los brokers desde `KAFKA_BOOTSTRAP_SERVERS`.
- RF-12: EL SISTEMA no declarará el topic ni la topología de Kafka; pertenecen a `infra`.
- RF-13: EL SISTEMA conservará sin cambios `POST /orders`, `GET /orders`, los health checks, las métricas y las variables de entorno existentes.
- RF-14: EL SISTEMA no implementará consumidores de Kafka en este corte.

## Requisitos no funcionales

- Atomicidad entre pedido y evento.
- Publicación at-least-once con duplicados tolerados por los consumidores.
- Sin pérdida por indisponibilidad de Kafka.
- Sin acoplar el alta al broker: el 201 no espera a Kafka.
- Trazabilidad con `orderId` y `eventId` en logs.

## Casos límite

- Kafka caído al dar de alta: 201 y evento pendiente.
- Kafka cae después de RabbitMQ y antes de Kafka: solo queda pendiente Kafka.
- Reinicio del servicio con eventos pendientes: se reanudan.
- Publicación duplicada: admisible.
- Evento ya publicado a RabbitMQ pero no a Kafka: no se vuelve a publicar a RabbitMQ.
- Fallo entre el ACK de Kafka y el marcado en base: puede duplicarse; es aceptable.

## Fuera de alcance

- Consumidores de analítica o notificaciones.
- Declarar topics, particiones o retención.
- Exactamente una vez.
- Pagos y `payment.*`.
- Cambiar la semántica de estados del pedido.
- Cambiar el contrato HTTP.

## Criterios de finalización

- Tests unitarios del relay de Kafka y de la publicación.
- `./mvnw verify` en verde.
- Demo: alta con Kafka arriba publica en `orders.events`; con Kafka abajo responde 201 y publica al volver; RabbitMQ sigue funcionando.

## Dudas abiertas

Ninguna.
