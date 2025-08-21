package com.example.messagequeue;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Single-file Spring Boot application that demonstrates message queue listeners
 * for both JMS (ActiveMQ) and AMQP (RabbitMQ) message brokers.
 * 
 * This application includes:
 * - JMS listener for ActiveMQ queues
 * - RabbitMQ listener for AMQP queues
 * - Configuration for both message brokers
 * - Error handling and retry mechanisms
 * - Message processing logic
 * - Health monitoring capabilities
 */
@SpringBootApplication
@EnableJms
@EnableRabbit
@EnableRetry
@Component
@Validated
public class MessageQueueListenerApplication {

    private static final Logger logger = LoggerFactory.getLogger(MessageQueueListenerApplication.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Configuration properties
    @Value("${activemq.broker-url:tcp://localhost:61616}")
    private String activeMqBrokerUrl;

    @Value("${activemq.user:admin}")
    private String activeMqUser;

    @Value("${activemq.password:admin}")
    private String activeMqPassword;

    @Value("${rabbitmq.host:localhost}")
    private String rabbitMqHost;

    @Value("${rabbitmq.port:5672}")
    private int rabbitMqPort;

    @Value("${rabbitmq.username:guest}")
    private String rabbitMqUsername;

    @Value("${rabbitmq.password:guest}")
    private String rabbitMqPassword;

    @Value("${jms.queue.name:test.jms.queue}")
    private String jmsQueueName;

    @Value("${rabbitmq.queue.name:test.rabbit.queue}")
    private String rabbitQueueName;

    @Value("${rabbitmq.exchange.name:test.exchange}")
    private String rabbitExchangeName;

    @Value("${rabbitmq.routing.key:test.routing.key}")
    private String rabbitRoutingKey;

    public static void main(String[] args) {
        SpringApplication.run(MessageQueueListenerApplication.class, args);
        logger.info("Message Queue Listener Application started successfully!");
    }

    // ========== JMS CONFIGURATION ==========

    /**
     * Configure ActiveMQ connection factory
     */
    @Bean(name = "jmsConnectionFactory")
    public jakarta.jms.ConnectionFactory jmsConnectionFactory() {
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory();
        connectionFactory.setBrokerURL(activeMqBrokerUrl);
        connectionFactory.setUserName(activeMqUser);
        connectionFactory.setPassword(activeMqPassword);
        connectionFactory.setTrustAllPackages(true);
        logger.info("JMS ConnectionFactory configured for broker: {}", activeMqBrokerUrl);
        return connectionFactory;
    }

    /**
     * Configure JMS listener container factory
     */
    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory() {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(jmsConnectionFactory());
        factory.setMessageConverter(jmsMessageConverter());
        factory.setConcurrency("1-3"); // Min-Max concurrent consumers
        factory.setReceiveTimeout(5000L);
        factory.setSessionTransacted(true);
        return factory;
    }

    /**
     * Configure JMS message converter for JSON serialization
     */
    @Bean
    public MessageConverter jmsMessageConverter() {
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setTargetType(MessageType.TEXT);
        converter.setTypeIdPropertyName("_type");
        return converter;
    }

    /**
     * Configure JMS template for sending messages
     */
    @Bean
    public JmsTemplate jmsTemplate() {
        JmsTemplate template = new JmsTemplate();
        template.setConnectionFactory(jmsConnectionFactory());
        template.setMessageConverter(jmsMessageConverter());
        return template;
    }

    // ========== RABBITMQ CONFIGURATION ==========

    /**
     * Configure RabbitMQ connection factory
     */
    @Bean(name = "rabbitConnectionFactory")
    public org.springframework.amqp.rabbit.connection.ConnectionFactory rabbitConnectionFactory() {
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory();
        connectionFactory.setHost(rabbitMqHost);
        connectionFactory.setPort(rabbitMqPort);
        connectionFactory.setUsername(rabbitMqUsername);
        connectionFactory.setPassword(rabbitMqPassword);
        connectionFactory.setVirtualHost("/");
        connectionFactory.setChannelCacheSize(10);
        logger.info("RabbitMQ ConnectionFactory configured for host: {}:{}", rabbitMqHost, rabbitMqPort);
        return connectionFactory;
    }

    /**
     * Configure RabbitMQ template
     */
    @Bean
    public RabbitTemplate rabbitTemplate() {
        RabbitTemplate template = new RabbitTemplate(rabbitConnectionFactory());
        template.setMessageConverter(rabbitMessageConverter());
        template.setRetryTemplate(null); // Disable retry template for manual handling
        return template;
    }

    /**
     * Configure RabbitMQ message converter
     */
    @Bean
    public Jackson2JsonMessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * Declare the main exchange
     */
    @Bean
    public TopicExchange messageExchange() {
        return new TopicExchange(rabbitExchangeName, true, false);
    }

    /**
     * Declare the main queue
     */
    @Bean
    public Queue messageQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", rabbitExchangeName + ".dlx");
        args.put("x-dead-letter-routing-key", "dead.letter");
        args.put("x-message-ttl", 300000); // 5 minutes TTL
        return QueueBuilder.durable(rabbitQueueName).withArguments(args).build();
    }

    /**
     * Declare dead letter exchange
     */
    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(rabbitExchangeName + ".dlx", true, false);
    }

    /**
     * Declare dead letter queue
     */
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(rabbitQueueName + ".dlq").build();
    }

    /**
     * Bind main queue to exchange
     */
    @Bean
    public Binding messageBinding() {
        return BindingBuilder.bind(messageQueue()).to(messageExchange()).with(rabbitRoutingKey);
    }

    /**
     * Bind dead letter queue to dead letter exchange
     */
    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with("dead.letter");
    }

    // ========== JMS MESSAGE LISTENERS ==========

    /**
     * JMS Listener for processing text messages from ActiveMQ
     */
    @JmsListener(destination = "${jms.queue.name}")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public void handleJmsTextMessage(String message, Message jmsMessage) {
        try {
            logger.info("Received JMS text message: {}", message);
            
            // Extract message properties
            String messageId = jmsMessage.getJMSMessageID();
            long timestamp = jmsMessage.getJMSTimestamp();
            
            logger.info("JMS Message ID: {}, Timestamp: {}", messageId, timestamp);
            
            // Process the message
            processTextMessage(message, "JMS");
            
            logger.info("Successfully processed JMS text message: {}", messageId);
            
        } catch (JMSException e) {
            logger.error("Error extracting JMS message properties: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process JMS message", e);
        } catch (Exception e) {
            logger.error("Error processing JMS text message: {}", e.getMessage(), e);
            throw e; // Re-throw for retry mechanism
        }
    }

    /**
     * JMS Listener for processing JSON messages from ActiveMQ
     */
    @JmsListener(destination = "${jms.queue.name}.json")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public void handleJmsJsonMessage(TextMessage textMessage) {
        try {
            String jsonContent = textMessage.getText();
            logger.info("Received JMS JSON message: {}", jsonContent);
            
            // Parse JSON message
            MessagePayload payload = objectMapper.readValue(jsonContent, MessagePayload.class);
            
            // Process the structured message
            processStructuredMessage(payload, "JMS");
            
            logger.info("Successfully processed JMS JSON message with ID: {}", payload.getId());
            
        } catch (JMSException e) {
            logger.error("Error extracting JMS text content: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to extract JMS message content", e);
        } catch (JsonProcessingException e) {
            logger.error("Error parsing JMS JSON message: {}", e.getMessage(), e);
            throw new RuntimeException("Invalid JSON format in JMS message", e);
        } catch (Exception e) {
            logger.error("Error processing JMS JSON message: {}", e.getMessage(), e);
            throw e;
        }
    }

    // ========== RABBITMQ MESSAGE LISTENERS ==========

    /**
     * RabbitMQ Listener for processing messages from AMQP queue
     */
    @RabbitListener(queues = "${rabbitmq.queue.name}")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public void handleRabbitMessage(@Valid MessagePayload payload, 
                                   org.springframework.amqp.core.Message message) {
        try {
            logger.info("Received RabbitMQ message: {}", payload);
            
            // Extract message properties
            org.springframework.amqp.core.MessageProperties props = message.getMessageProperties();
            String messageId = props.getMessageId();
            String routingKey = props.getReceivedRoutingKey();
            
            logger.info("RabbitMQ Message ID: {}, Routing Key: {}", messageId, routingKey);
            
            // Process the structured message
            processStructuredMessage(payload, "RabbitMQ");
            
            logger.info("Successfully processed RabbitMQ message with ID: {}", payload.getId());
            
        } catch (Exception e) {
            logger.error("Error processing RabbitMQ message: {}", e.getMessage(), e);
            throw e; // Re-throw for retry and dead letter handling
        }
    }

    /**
     * RabbitMQ Listener for processing text messages
     */
    @RabbitListener(queues = "${rabbitmq.queue.name}.text")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public void handleRabbitTextMessage(String message, 
                                       org.springframework.amqp.core.Message amqpMessage) {
        try {
            logger.info("Received RabbitMQ text message: {}", message);
            
            // Extract message properties
            org.springframework.amqp.core.MessageProperties props = amqpMessage.getMessageProperties();
            String contentType = props.getContentType();
            
            logger.info("RabbitMQ Content Type: {}", contentType);
            
            // Process the text message
            processTextMessage(message, "RabbitMQ");
            
            logger.info("Successfully processed RabbitMQ text message");
            
        } catch (Exception e) {
            logger.error("Error processing RabbitMQ text message: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Dead Letter Queue listener for handling failed messages
     */
    @RabbitListener(queues = "${rabbitmq.queue.name}.dlq")
    public void handleDeadLetterMessage(org.springframework.amqp.core.Message message) {
        try {
            logger.warn("Received message in Dead Letter Queue");
            
            org.springframework.amqp.core.MessageProperties props = message.getMessageProperties();
            String originalQueue = (String) props.getHeaders().get("x-first-death-queue");
            String reason = (String) props.getHeaders().get("x-first-death-reason");
            
            logger.warn("Dead letter - Original Queue: {}, Reason: {}", originalQueue, reason);
            
            // Handle dead letter message (e.g., log, store, alert)
            handleDeadLetterMessage(new String(message.getBody()), originalQueue, reason);
            
        } catch (Exception e) {
            logger.error("Error processing dead letter message: {}", e.getMessage(), e);
        }
    }

    // ========== MESSAGE PROCESSING LOGIC ==========

    /**
     * Process text messages from any queue type
     */
    private void processTextMessage(String message, String source) {
        logger.info("Processing text message from {}: {}", source, message);
        
        // Simulate message processing
        try {
            Thread.sleep(100); // Simulate processing time
            
            // Business logic here
            if (message.toLowerCase().contains("error")) {
                throw new RuntimeException("Simulated processing error for message: " + message);
            }
            
            // Log successful processing
            logger.info("Text message processed successfully from {}", source);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Message processing interrupted", e);
        }
    }

    /**
     * Process structured messages (JSON payloads)
     */
    private void processStructuredMessage(MessagePayload payload, String source) {
        logger.info("Processing structured message from {}: ID={}, Type={}", 
                   source, payload.getId(), payload.getType());
        
        try {
            // Validate payload
            if (payload.getData() == null || payload.getData().isEmpty()) {
                throw new IllegalArgumentException("Message data cannot be empty");
            }
            
            // Simulate processing based on message type
            Thread.sleep(200); // Simulate processing time
            
            switch (payload.getType().toLowerCase()) {
                case "order":
                    processOrderMessage(payload);
                    break;
                case "notification":
                    processNotificationMessage(payload);
                    break;
                case "event":
                    processEventMessage(payload);
                    break;
                default:
                    logger.warn("Unknown message type: {}", payload.getType());
                    processGenericMessage(payload);
            }
            
            logger.info("Structured message processed successfully from {}", source);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Message processing interrupted", e);
        }
    }

    /**
     * Process order-specific messages
     */
    private void processOrderMessage(MessagePayload payload) {
        logger.info("Processing order message: {}", payload.getData());
        // Order-specific business logic
    }

    /**
     * Process notification messages
     */
    private void processNotificationMessage(MessagePayload payload) {
        logger.info("Processing notification message: {}", payload.getData());
        // Notification-specific business logic
    }

    /**
     * Process event messages
     */
    private void processEventMessage(MessagePayload payload) {
        logger.info("Processing event message: {}", payload.getData());
        // Event-specific business logic
    }

    /**
     * Process generic messages
     */
    private void processGenericMessage(MessagePayload payload) {
        logger.info("Processing generic message: {}", payload.getData());
        // Generic message processing logic
    }

    /**
     * Handle dead letter messages
     */
    private void handleDeadLetterMessage(String messageContent, String originalQueue, String reason) {
        logger.error("Dead letter message from queue '{}' with reason '{}': {}", 
                    originalQueue, reason, messageContent);
        
        // Implement dead letter handling logic:
        // - Store in database for manual review
        // - Send alert to administrators
        // - Attempt alternative processing
        // - Log for audit purposes
    }

    // ========== MESSAGE PAYLOAD MODELS ==========

    /**
     * Message payload model for structured messages
     */
    public static class MessagePayload {
        
        @NotBlank(message = "Message ID cannot be blank")
        @JsonProperty("id")
        private String id;
        
        @NotBlank(message = "Message type cannot be blank")
        @JsonProperty("type")
        private String type;
        
        @NotNull(message = "Message data cannot be null")
        @JsonProperty("data")
        private Map<String, Object> data;
        
        @JsonProperty("timestamp")
        private LocalDateTime timestamp;
        
        @JsonProperty("source")
        private String source;
        
        @JsonProperty("priority")
        private Integer priority = 1;

        // Default constructor
        public MessagePayload() {
            this.timestamp = LocalDateTime.now();
        }

        // Constructor with required fields
        public MessagePayload(String id, String type, Map<String, Object> data) {
            this();
            this.id = id;
            this.type = type;
            this.data = data;
        }

        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public Map<String, Object> getData() { return data; }
        public void setData(Map<String, Object> data) { this.data = data; }

        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }

        public Integer getPriority() { return priority; }
        public void setPriority(Integer priority) { this.priority = priority; }

        @Override
        public String toString() {
            return String.format("MessagePayload{id='%s', type='%s', data=%s, timestamp=%s, source='%s', priority=%d}",
                    id, type, data, timestamp, source, priority);
        }
    }

    /**
     * Health check model for monitoring
     */
    public static class HealthStatus {
        private final String component;
        private final boolean healthy;
        private final String message;
        private final LocalDateTime timestamp;

        public HealthStatus(String component, boolean healthy, String message) {
            this.component = component;
            this.healthy = healthy;
            this.message = message;
            this.timestamp = LocalDateTime.now();
        }

        // Getters
        public String getComponent() { return component; }
        public boolean isHealthy() { return healthy; }
        public String getMessage() { return message; }
        public LocalDateTime getTimestamp() { return timestamp; }

        @Override
        public String toString() {
            return String.format("HealthStatus{component='%s', healthy=%s, message='%s', timestamp=%s}",
                    component, healthy, message, timestamp);
        }
    }
}
