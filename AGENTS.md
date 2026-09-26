# AGENTS.md - order-api

## Proyecto
API de pedidos de Friendly E-Shop. Es un servicio independiente en Java 25 y Spring Boot que posee pedidos, estados y precios acordados; expone su contrato HTTP bajo `/orders`.
Persiste únicamente en la base `orders`, usa Flyway, prepara integración asíncrona con RabbitMQ y emite telemetría mediante Actuator/OpenTelemetry.

## Comandos
- Ejecutar: `./mvnw spring-boot:run`
- Tests: `./mvnw test`
- Compilar y verificar: `./mvnw verify`
- Lint: `./mvnw checkstyle:check`; también se ejecuta automáticamente en la fase `validate`.

## Estilo y convenciones
- Usa Java 25, Spring Boot 4.1 y el paquete `com.friendlyeshop.order`.
- Nombres, código y documentación técnica en inglés; mensajes visibles al usuario en español.
- Respeta `checkstyle.xml`: 4 espacios, sin tabs, líneas de hasta 120 caracteres e imports explícitos.
- Mantén controladores HTTP delgados y modela explícitamente las transiciones de estado.
- Crea nuevas migraciones Flyway; no edites migraciones ya aplicadas. Hibernate solo valida el esquema.

## Reglas
- Lee la skill `/java-springboot` y la spec activa, si existe, antes de tocar código.
- Usa `/clean-architecture` al diseñar o modificar capas, límites, dependencias, casos de uso o adaptadores.
- Este servicio es la fuente de verdad del pedido y debe conservar el precio aceptado al comprar.
- Nunca escribas tablas de catálogo o pagos; obtén datos por contratos y publica/consume eventos cuando la spec lo defina.
- La reserva de stock es trabajo futuro: no inventes su workflow, reintentos o semántica sin una spec.
- Conserva `/orders`, las variables de entorno, los health checks y las métricas usadas por Kubernetes.
- RabbitMQ es la mensajería prevista; no añadas Kafka ni dependencias sin acordarlo.
- No omitas ni desactives reglas de Checkstyle para evitar corregir una violación.
- Los manifiestos y secretos pertenecen a `infra`; coordina allí cambios de puerto, ruta o configuración.

## Al terminar cualquier tarea
- Tras cambios no triviales de código de producción, aplica `/clean-code-guard` antes de finalizar.
- Ejecuta `./mvnw verify`; incluye Checkstyle y los tests.
- Cubre transiciones, precios acordados y errores relevantes con tests; añade migraciones para cambios de esquema.
- Comprueba que no se hayan roto `/orders` ni los endpoints de Actuator.
