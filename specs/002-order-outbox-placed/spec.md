# Spec 002 — Alta de pedido con outbox y publicación de order.placed

## Contexto y objetivo

Friendly E-Shop necesita iniciar el ciclo de venta asíncrono de la Fase 1. Hoy `order-api` solo expone un `/orders` de disponibilidad y todavía no registra pedidos reales. Este corte habilita el alta mínima de un pedido, garantiza que el pedido aceptado y su evento `order.placed` no se separen ni se pierdan aunque el broker no esté disponible, y actualiza el pedido con el resultado de la reserva de stock sin corromperlo ante reentregas, resultados contradictorios o mensajes de pedidos desconocidos. El resultado es la base del proceso de venta asíncrono; la reserva de stock, las notificaciones al vendedor y los pagos pertenecen a otros servicios e iteraciones.

## Usuarios / actores

- Cliente o checkout que solicita el alta de un pedido.
- `catalog-api`, que produce los resultados de stock `stock.reserved` y `stock.rejected`.
- Equipo de operaciones, que depende de los health checks, las métricas y las variables de entorno existentes.

## Historias de usuario

- H1: Como cliente quiero dar de alta un pedido con los productos, cantidades y precios acordados para obtener un pedido registrado con su total y su estado.
- H2: Como negocio quiero que el evento `order.placed` de un pedido aceptado no se pierda aunque el broker esté caído para no dejar ventas sin continuidad.
- H3: Como responsable de catálogo quiero que los resultados de stock actualicen el pedido una sola vez y solo desde su estado inicial para que las reentregas y los resultados fuera de orden no corrompan su estado.
- H4: Como equipo de operaciones quiero conservar el comportamiento actual de `/orders`, los health checks, las métricas y las variables de entorno para no romper el despliegue.

## Requisitos funcionales (criterios de aceptación en EARS)

- RF-1: CUANDO un cliente solicita el alta de un pedido con un tenant y al menos un ítem, cada uno con producto, cantidad, precio unitario acordado y moneda, EL SISTEMA registrará el pedido.
- RF-2: CUANDO el pedido se registre, EL SISTEMA responderá 201 con el identificador del pedido, su estado `PENDING`, su total y su moneda.
- RF-3: EL SISTEMA calculará el total del pedido como la suma, para cada ítem, de la cantidad por el precio unitario acordado, expresada en la moneda única del pedido y redondeada a 2 decimales con redondeo HALF_UP.
- RF-4: EL SISTEMA asignará al pedido el estado inicial `PENDING` antes de recibir cualquier resultado de stock.
- RF-5: MIENTRAS el pedido esté en estado `PENDING`, EL SISTEMA aceptará la primera transición a `STOCK_RESERVED` o `STOCK_REJECTED` proveniente de un resultado de stock.
- RF-6: CUANDO se registre un pedido, EL SISTEMA almacenará el pedido y su evento `order.placed` de forma atómica, de modo que no exista uno sin el otro.
- RF-7: CUANDO el pedido y su evento estén almacenados, EL SISTEMA publicará el evento `order.placed` en el exchange `orders` con routing key `order.placed`.
- RF-8: EL SISTEMA incluirá en `order.placed` una identificación única del evento, su tipo `order.placed`, el momento del alta, el tenant, el pedido, los ítems con sus productos, cantidades, precios acordados y monedas, el total y la moneda.
- RF-9: SI el broker no está disponible durante la publicación, ENTONCES EL SISTEMA responderá 201 al alta, conservará el evento `order.placed` pendiente y lo publicará cuando el broker vuelva a estar disponible, sin descartarlo.
- RF-10: EL SISTEMA publicará `order.placed` con garantía de entrega al menos una vez, por lo que un mismo evento podrá publicarse más de una vez.
- RF-11: CUANDO se reciba el evento `stock.reserved` de un pedido registrado en estado `PENDING`, EL SISTEMA actualizará el pedido al estado `STOCK_RESERVED`, conservando el tenant, los ítems, los precios acordados, el total y la moneda.
- RF-12: CUANDO se reciba el evento `stock.rejected` de un pedido registrado en estado `PENDING`, EL SISTEMA actualizará el pedido al estado `STOCK_REJECTED`, conservando el tenant, los ítems, los precios acordados, el total y la moneda.
- RF-13: MIENTRAS el pedido esté en estado `STOCK_REJECTED`, EL SISTEMA no aplicará ninguna transición posterior ni reabrirá el pedido.
- RF-14: SI el mismo resultado de stock se recibe más de una vez para un pedido, ENTONCES EL SISTEMA lo tratará como ya procesado y no alterará el estado ni los datos del pedido.
- RF-15: SI se recibe un resultado de stock contradictorio o fuera de orden para un pedido que ya no está en estado `PENDING`, ENTONCES EL SISTEMA lo ignorará y conservará el primer resultado aplicado.
- RF-16: SI se recibe un resultado de stock para un pedido desconocido, ENTONCES EL SISTEMA confirmará la recepción del mensaje, registrará el hecho y la métrica correspondiente, y no actualizará ningún pedido.
- RF-17: SI el alta incumple alguna regla de validación (tenant UUID obligatorio, al menos un ítem, producto obligatorio, cantidad mayor que 0, precio unitario mayor o igual que 0, moneda única `ARS` o `USD`, sin producto repetido en el mismo pedido), ENTONCES EL SISTEMA rechazará la solicitud sin registrar el pedido ni el evento.
- RF-18: EL SISTEMA confiará en el precio unitario acordado enviado por el cliente y no consultará los servicios de catálogo ni de cuenta durante el alta de un pedido.
- RF-19: EL SISTEMA no aplicará idempotencia al alta de `POST /orders`; cada reintento del cliente podrá registrar un pedido distinto.
- RF-20: EL SISTEMA conservará el comportamiento actual de `GET /orders`, los health checks, las métricas y las variables de entorno existentes.
- RF-21: EL SISTEMA no escribirá datos de catálogo ni de pagos.
- RF-22: SI el procesamiento de un resultado de stock falla, ENTONCES EL SISTEMA no confirmará la recepción del mensaje mientras queden reintentos; agotados los 5 intentos, publicará el mensaje original en `order.stock-results.dlq` y confirmará la recepción para no reintentarlo indefinidamente.

## Requisitos no funcionales

- Atomicidad: ningún pedido registrado puede carecer de su evento `order.placed`, ni ningún evento de alta puede existir sin su pedido.
- Idempotencia: la aplicación repetida o concurrente del mismo resultado de stock producirá el mismo estado final y no duplicará transiciones; solo la primera transición desde `PENDING` se aplica.
- No pérdida: ningún evento `order.placed` de un pedido registrado se descartará por indisponibilidad del broker.
- Plazo de publicación: en la Fase 1 no existe un plazo máximo de publicación; los eventos pendientes permanecen almacenados, se monitorizan y nunca se descartan.
- Garantía de entrega: la publicación de `order.placed` es al menos una vez, con posibles duplicados.
- Reintentos: el consumo de resultados de stock admite 5 intentos con 30 segundos entre intentos; al quinto fallo el mensaje queda en la DLQ y no se reintenta indefinidamente.
- Compatibilidad operativa: los health checks y las métricas existentes seguirán disponibles para Kubernetes, y las variables de entorno actuales mantendrán su significado.
- Aislamiento de datos: `order-api` solo escribirá datos del pedido y de sus eventos de salida.
- Observabilidad: la recepción de resultados de stock de pedidos desconocidos y los eventos pendientes de publicación quedarán registrados en logs y métricas.

## Casos límite

- Reentrega del mismo `stock.reserved` o `stock.rejected`, con o sin el mismo identificador de entrega: se trata como ya procesado y no cambia el pedido.
- Llegada simultánea de dos resultados de stock para el mismo pedido: solo la primera transición desde `PENDING` se aplica y la otra se ignora.
- Resultado `stock.reserved` o `stock.rejected` para un pedido desconocido: se confirma la recepción, se registra en log y métrica, y no se actualiza ningún pedido.
- Resultados de stock contradictorios o fuera de orden, como un rechazo después de una reserva o una reserva después de un rechazo: se ignoran y prevalece el primero aplicado.
- Rechazo por stock ya aplicado: `STOCK_REJECTED` es terminal en la Fase 1; no permite reintento ni reapertura.
- Alta con un tenant o un producto inexistente en otros servicios: el alta confía en los datos recibidos y no verifica su existencia; solo se validan formato y reglas locales.
- Alta solicitada con el broker caído: responde 201, el evento queda pendiente en el outbox y se publica al volver el broker.
- Broker que cae después de almacenar el pedido y antes de publicar, incluido el reinicio del servicio con eventos pendientes: el relay reanuda la publicación sin descartar el evento.
- Falla al almacenar el pedido o el evento: no debe quedar ninguno de los dos.
- Reintento del cliente sobre `POST /orders`: no es idempotente en la Fase 1 y puede crear pedidos distintos.
- Solicitud sin ítems, con cantidades no positivas, precios negativos, monedas mezcladas o productos repetidos: se rechaza sin registrar el pedido ni el evento.
- Publicación duplicada de `order.placed`: es admisible por la garantía de entrega al menos una vez.
- Falla transitoria al procesar un resultado de stock: no se confirma la recepción mientras queden reintentos; agotados los 5 intentos, el mensaje se publica en `order.stock-results.dlq` y se confirma.
- Evento pendiente que no puede publicarse tras muchos intentos: permanece pendiente y monitorizado; no se descarta ni existe plazo máximo que lo elimine.

## Fuera de alcance

- Reservar o liberar stock; pertenece a `catalog-api`.
- Notificar al vendedor; corresponde a la Fase 2.
- Cobrar o gestionar pagos; pertenece a `payment-api`.
- Declarar exchanges, colas, bindings, TTL y la retención de la DLQ; la topología pertenece a `infra`.
- Detalle de producto o de tienda.
- Kafka y cualquier mensajería distinta de la acordada.
- Cambiar el comportamiento actual de `GET /orders`, los health checks, las métricas o las variables de entorno.
- Añadir endpoints de consulta de pedidos distintos del comportamiento actual de `GET /orders`.
- Compensar o reintentar reservas de stock; pertenece a `catalog-api`.
- Escribir tablas o datos de catálogo o de pagos.
- Idempotencia del alta `POST /orders` e `Idempotency-Key`; se difieren a checkout y pagos.
- Reabrir, reintentar o compensar pedidos en estado `STOCK_REJECTED`.
- Verificar contra otros servicios la existencia del tenant o del producto, o la vigencia del precio.
- Garantía de entrega exactamente una vez.
- Plazo máximo de publicación o descarte de eventos `order.placed` pendientes.

## Criterios de finalización

- Todos los RF tienen pruebas automatizadas en verde.
- Demo manual: un alta con broker disponible publica `order.placed`; un alta con broker caído responde 201 y no pierde el evento, que se publica al volver; `stock.reserved` y `stock.rejected` actualizan el pedido solo desde `PENDING`; una reentrega, un resultado contradictorio o un pedido desconocido no corrompen el estado.
- `GET /orders`, los health checks y las métricas siguen respondiendo, y las variables de entorno actuales no cambian.
- No existe ninguna escritura en datos de catálogo ni de pagos.
- Todas las decisiones de la sección `Decisiones resueltas` quedan reflejadas en los RF y en sus pruebas.

## Dudas abiertas

- Ninguna.

## Decisiones resueltas

- ~~¿Cuál es el estado inicial del pedido y cuáles son los estados y transiciones permitidos?~~ Estado inicial `PENDING`; estados `PENDING`, `STOCK_RESERVED` y `STOCK_REJECTED`. Solo se permite una transición desde `PENDING`; los resultados posteriores o contradictorios se ignoran y prevalece el primero aplicado.
- ~~¿El rechazo por stock es un estado terminal o permite reintento o reapertura del pedido?~~ Es terminal en la Fase 1; no permite reintento ni reapertura.
- ~~¿Qué reglas de validación debe aplicar el alta (tenant, ítems, cantidades, precios, monedas y productos repetidos)?~~ Tenant UUID obligatorio; al menos un ítem; producto obligatorio; cantidad mayor que 0; precio unitario mayor o igual que 0; una sola moneda por pedido (`ARS` o `USD`); sin producto repetido en el mismo pedido.
- ~~¿El alta debe verificar contra otros servicios la existencia del tenant y del producto y la vigencia del precio, o confía en los datos recibidos?~~ Confía en los datos recibidos; no consulta catálogo ni cuenta durante la Fase 1.
- ~~¿Cómo se calcula y redondea el total cuando los ítems tienen monedas distintas?~~ El pedido tiene una sola moneda; el total es la suma de cantidad por precio unitario con redondeo HALF_UP a 2 decimales.
- ~~¿El alta responde 201 aunque el broker no esté disponible?~~ Sí; el outbox persiste el evento y el relay lo publica después.
- ~~¿Dentro de qué plazo máximo debe publicarse un evento order.placed pendiente?~~ No hay plazo máximo en la Fase 1; los eventos pendientes nunca se descartan y se monitorizan.
- ~~¿La publicación de order.placed es al menos una vez, con posibles duplicados, o exactamente una vez?~~ Es al menos una vez; se admiten duplicados.
- ~~¿Qué debe hacer el servicio ante un resultado de stock de un pedido desconocido?~~ Confirmar la recepción, registrar log y métrica, y no actualizar ningún pedido.
- ~~¿Qué resultado prevalece si llegan resultados de stock contradictorios o fuera de orden para el mismo pedido?~~ Prevalece el primero aplicado desde `PENDING`; los demás se ignoran.
- ~~¿El alta debe ser idempotente ante reintentos del cliente sobre POST /orders?~~ No en la Fase 1; cada reintento puede crear un pedido distinto. Se difiere a checkout y pagos y no se añade `Idempotency-Key`.
- ~~¿Qué debe hacer el servicio si un evento order.placed no puede publicarse tras el plazo máximo?~~ No existe plazo máximo; el evento permanece pendiente, se monitoriza y nunca se descarta.
- ~~¿Qué estado exacto debe tener el pedido tras `stock.reserved`?~~ `STOCK_RESERVED`.
- ~~¿Qué estado exacto debe tener el pedido tras `stock.rejected`?~~ `STOCK_REJECTED`; el motivo del rechazo es informativo y no altera el estado del pedido.
- El consumo de resultados de stock admite 5 intentos con 30 segundos entre intentos; los primeros fallos se reencolan y al quinto se publica el mensaje original en `order.stock-results.dlq` y se confirma.
