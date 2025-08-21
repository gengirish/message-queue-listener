# Message Queue Listener Application

A comprehensive Spring Boot application demonstrating message queue listeners for both JMS (ActiveMQ) and AMQP (RabbitMQ) message brokers. This single-file solution showcases event-driven architecture principles and Spring's messaging support.

## Features

- **JMS Support**: ActiveMQ message listeners with `@JmsListener` annotation
- **AMQP Support**: RabbitMQ message listeners with `@RabbitListener` annotation
- **Single File Architecture**: All components consolidated in one comprehensive Java file
- **Error Handling**: Retry mechanisms and dead letter queue support
- **Message Processing**: Support for both text and JSON message formats
- **Configuration**: Property-driven configuration for both message brokers
- **Monitoring**: Health checks and metrics via Spring Boot Actuator
- **Docker Support**: Complete Docker Compose setup for local development

## Technology Stack

- **Java 17**
- **Spring Boot 3.2.0**
- **Spring JMS** for ActiveMQ integration
- **Spring AMQP** for RabbitMQ integration
- **Maven** for dependency management
- **Docker & Docker Compose** for containerization

## Project Structure

```
├── com.example.messagequeue.MessageQueueListenerApplication.java  # Single comprehensive application file
├── application.yml                       # Configuration properties
├── pom.xml                              # Maven dependencies
├── docker-compose.yml                   # Docker services setup
└── README.md                           # This file
```

## Prerequisites

- **Java 17** or higher
- **Maven 3.6+**
- **Docker & Docker Compose** (for running message brokers)

## Quick Start

### 1. Start Message Brokers

Start ActiveMQ and RabbitMQ using Docker Compose:

```bash
docker-compose up -d activemq rabbitmq
```

Wait for services to be healthy:

```bash
docker-compose ps
```

### 2. Build and Run Application

```bash
# Build the application
mvn clean compile

# Run the application
mvn spring-boot:run
```

### 3. Verify Application

Check application health:

```bash
curl http://localhost:8080/api/actuator/health
```

## Message Broker Access

### ActiveMQ

- **JMS Port**: `tcp://localhost:61616`
- **Web Console**: http://localhost:8161/admin/
- **Credentials**: admin/admin

### RabbitMQ

- **AMQP Port**: `amqp://localhost:5672`
- **Management UI**: http://localhost:15672/
- **Credentials**: guest/guest

## Message Listeners

The application includes several message listeners:

### JMS Listeners (ActiveMQ)

1. **Text Message Listener**

   - Queue: `test.jms.queue`
   - Handles: String messages
   - Annotation: `@JmsListener(destination = "${jms.queue.name}")`

2. **JSON Message Listener**
   - Queue: `test.jms.queue.json`
   - Handles: JSON structured messages
   - Annotation: `@JmsListener(destination = "${jms.queue.name}.json")`

### RabbitMQ Listeners (AMQP)

1. **Structured Message Listener**

   - Queue: `test.rabbit.queue`
   - Handles: MessagePayload objects
   - Annotation: `@RabbitListener(queues = "${rabbitmq.queue.name}")`

2. **Text Message Listener**

   - Queue: `test.rabbit.queue.text`
   - Handles: String messages
   - Annotation: `@RabbitListener(queues = "${rabbitmq.queue.name}.text")`

3. **Dead Letter Queue Listener**
   - Queue: `test.rabbit.queue.dlq`
   - Handles: Failed messages
   - Annotation: `@RabbitListener(queues = "${rabbitmq.queue.name}.dlq")`

## Testing Message Processing

### Send JMS Messages (ActiveMQ)

Using ActiveMQ Web Console (http://localhost:8161/admin/):

1. Navigate to "Queues"
2. Click "Send To" for `test.jms.queue`
3. Enter message content:
   ```
   Hello from ActiveMQ!
   ```

### Send AMQP Messages (RabbitMQ)

Using RabbitMQ Management UI (http://localhost:15672/):

1. Navigate to "Queues"
2. Click on `test.rabbit.queue`
3. Expand "Publish message"
4. Enter JSON payload:
   ```json
   {
     "id": "msg-001",
     "type": "order",
     "data": {
       "orderId": "12345",
       "amount": 99.99,
       "customer": "John Doe"
     },
     "priority": 1
   }
   ```

### Using Command Line Tools

#### ActiveMQ (using curl)

```bash
# Send text message to JMS queue
curl -X POST \
  -u admin:admin \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "JMSDestination=test.jms.queue&JMSDestinationType=queue&body=Hello%20JMS" \
  http://localhost:8161/admin/sendMessage.action
```

#### RabbitMQ (using rabbitmqadmin)

```bash
# Install rabbitmqadmin
wget http://localhost:15672/cli/rabbitmqadmin
chmod +x rabbitmqadmin

# Send message to RabbitMQ queue
./rabbitmqadmin publish exchange=test.exchange routing_key=test.routing.key payload='{"id":"msg-002","type":"notification","data":{"message":"Hello RabbitMQ"}}'
```

## Configuration

### Environment Variables

You can override default configuration using environment variables:

```bash
# ActiveMQ Configuration
export ACTIVEMQ_BROKER_URL=tcp://localhost:61616
export ACTIVEMQ_USER=admin
export ACTIVEMQ_PASSWORD=admin

# RabbitMQ Configuration
export RABBITMQ_HOST=localhost
export RABBITMQ_PORT=5672
export RABBITMQ_USERNAME=guest
export RABBITMQ_PASSWORD=guest

# Queue Names
export JMS_QUEUE_NAME=test.jms.queue
export RABBITMQ_QUEUE_NAME=test.rabbit.queue
export RABBITMQ_EXCHANGE_NAME=test.exchange
export RABBITMQ_ROUTING_KEY=test.routing.key
```

### Application Properties

Key configuration properties in `application.yml`:

```yaml
# JMS Configuration
activemq:
  broker-url: tcp://localhost:61616
  user: admin
  password: admin

# RabbitMQ Configuration
rabbitmq:
  host: localhost
  port: 5672
  username: guest
  password: guest
  queue:
    name: test.rabbit.queue
  exchange:
    name: test.exchange
  routing:
    key: test.routing.key

# Queue Names
jms:
  queue:
    name: test.jms.queue
```

## Message Processing Logic

The application processes different message types:

### Text Messages

- Simple string processing
- Error simulation for messages containing "error"
- Logging and monitoring

### Structured Messages (JSON)

- Validation using Bean Validation
- Type-based processing:
  - **order**: Order processing logic
  - **notification**: Notification handling
  - **event**: Event processing
  - **generic**: Default processing

### Message Payload Structure

```json
{
  "id": "unique-message-id",
  "type": "order|notification|event",
  "data": {
    "key": "value"
  },
  "timestamp": "2024-01-01T12:00:00",
  "source": "system-name",
  "priority": 1
}
```

## Error Handling

### Retry Mechanism

- **Max Attempts**: 3
- **Backoff Delay**: 1000ms
- **Multiplier**: 2x

### Dead Letter Queue (RabbitMQ)

- Failed messages are routed to `test.rabbit.queue.dlq`
- TTL: 5 minutes
- Automatic dead letter handling

### Exception Handling

- JMS exceptions are logged and re-thrown for retry
- JSON parsing errors are handled gracefully
- Business logic errors trigger retry mechanism

## Monitoring and Health Checks

### Actuator Endpoints

- **Health**: http://localhost:8080/api/actuator/health
- **Info**: http://localhost:8080/api/actuator/info
- **Metrics**: http://localhost:8080/api/actuator/metrics

### Health Check Components

- JMS connection health
- RabbitMQ connection health
- Application status

### Logging

Logs are written to:

- **Console**: Structured format
- **File**: `logs/message-queue-listener.log`

Log levels:

- Application: INFO
- Spring JMS: DEBUG
- Spring AMQP: DEBUG

## Docker Deployment

### Build Application Image

```bash
# Build JAR file
mvn clean package

# Build Docker image
docker build -t message-queue-listener:1.0.0 .
```

### Run Complete Stack

```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f message-queue-app

# Stop services
docker-compose down
```

## Development

### Running Tests

```bash
# Unit tests
mvn test

# Integration tests
mvn verify

# All tests
mvn clean verify
```

### Code Structure

The single file `com.example.messagequeue.MessageQueueListenerApplication.java` contains:

1. **Configuration Section**: @Bean methods for JMS and RabbitMQ
2. **Message Listeners**: @JmsListener and @RabbitListener methods
3. **Processing Logic**: Business logic methods
4. **Data Models**: Static inner classes for message payloads

### Adding New Listeners

To add a new message listener:

1. Add a new method with appropriate annotation
2. Configure queue/exchange if needed
3. Implement processing logic
4. Add error handling and logging

Example:

```java
@JmsListener(destination = "new.queue.name")
@Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
public void handleNewMessage(String message) {
    logger.info("Received new message: {}", message);
    // Processing logic here
}
```

## Troubleshooting

### Common Issues

1. **Connection Refused**

   - Ensure message brokers are running
   - Check port availability
   - Verify network connectivity

2. **Authentication Failed**

   - Check credentials in configuration
   - Verify broker user permissions

3. **Queue Not Found**

   - Ensure queues are declared
   - Check queue names in configuration

4. **Messages Not Processing**
   - Check listener method signatures
   - Verify queue bindings
   - Review error logs

### Debug Mode

Enable debug logging:

```yaml
logging:
  level:
    com.example.messagequeue: DEBUG
    org.springframework.jms: DEBUG
    org.springframework.amqp: DEBUG
```

## Performance Tuning

### Concurrency Settings

```yaml
spring:
  jms:
    listener:
      concurrency: 1-3
  rabbitmq:
    listener:
      simple:
        concurrency: 1
        max-concurrency: 3
```

### Connection Pooling

- JMS: Connection pooling enabled
- RabbitMQ: Channel caching configured

## Security Considerations

- Use secure connection protocols (SSL/TLS) in production
- Implement proper authentication and authorization
- Validate and sanitize message content
- Monitor for suspicious message patterns

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make changes following the single-file architecture
4. Add tests for new functionality
5. Submit a pull request

## License

This project is licensed under the MIT License.
