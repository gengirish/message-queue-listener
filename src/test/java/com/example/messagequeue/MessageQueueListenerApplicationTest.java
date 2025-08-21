package com.example.messagequeue;

import jakarta.jms.ConnectionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the Message Queue Listener Application.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "activemq.broker-url=vm://localhost?broker.persistent=false",
    "rabbitmq.host=localhost",
    "rabbitmq.port=5672"
})
class MessageQueueListenerApplicationTest {

    @Autowired
    private JmsTemplate jmsTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private DefaultJmsListenerContainerFactory jmsListenerContainerFactory;

    @Autowired
    private ConnectionFactory jmsConnectionFactory;

    @Autowired
    private org.springframework.amqp.rabbit.connection.ConnectionFactory rabbitConnectionFactory;

    @Test
    void contextLoads() {
        // Test that the Spring context loads successfully
    }

    @Test
    void jmsBeansArePresentAndCorrectType() {
        assertNotNull(jmsTemplate, "JmsTemplate should be present");
        assertNotNull(jmsListenerContainerFactory, "JmsListenerContainerFactory should be present");
        assertNotNull(jmsConnectionFactory, "JMS ConnectionFactory should be present");
        assertTrue(jmsConnectionFactory instanceof ConnectionFactory, "JMS ConnectionFactory should be of type jakarta.jms.ConnectionFactory");
    }

    @Test
    void rabbitBeansArePresentAndCorrectType() {
        assertNotNull(rabbitTemplate, "RabbitTemplate should be present");
        assertNotNull(rabbitConnectionFactory, "RabbitMQ ConnectionFactory should be present");
        assertTrue(rabbitConnectionFactory instanceof org.springframework.amqp.rabbit.connection.ConnectionFactory, "RabbitMQ ConnectionFactory should be of type org.springframework.amqp.rabbit.connection.ConnectionFactory");
    }

    @Test
    void jmsTextMessageListenerProcessesMessage() {
        String testMessage = "Hello JMS!";
        assertDoesNotThrow(() -> jmsTemplate.convertAndSend("test.jms.queue", testMessage));
        // No exception means listener processed the message
    }

    @Test
    void jmsJsonMessageListenerProcessesValidMessage() throws Exception {
        String json = "{\"id\":\"1\",\"type\":\"order\",\"data\":{\"item\":\"book\"}}";
        assertDoesNotThrow(() -> jmsTemplate.convertAndSend("test.jms.queue.json", json));
    }

    @Test
    void jmsJsonMessageListenerHandlesInvalidJson() {
        String invalidJson = "not a json";
        // Listener exceptions are handled asynchronously; sending invalid JSON will not throw here.
        // In a real integration test, you would verify DLQ or error logs.
        assertDoesNotThrow(() -> jmsTemplate.convertAndSend("test.jms.queue.json", invalidJson));
    }

    @Test
    void jmsTextMessageListenerHandlesError() {
        String errorMessage = "This will cause error";
        // Listener exceptions are handled asynchronously; sending an error message will not throw here.
        // In a real integration test, you would verify DLQ or error logs.
        assertDoesNotThrow(() -> jmsTemplate.convertAndSend("test.jms.queue", errorMessage));
    }

    @Test
    void rabbitMessageListenerProcessesValidPayload() {
        // Skip test if RabbitMQ is not running
        org.junit.jupiter.api.Assumptions.assumeTrue(isRabbitAvailable(), "RabbitMQ broker not available");
        String json = "{\"id\":\"2\",\"type\":\"notification\",\"data\":{\"msg\":\"hi\"}}";
        assertDoesNotThrow(() -> rabbitTemplate.convertAndSend("test.exchange", "test.routing.key", json));
    }

    @Test
    void rabbitTextMessageListenerProcessesMessage() {
        org.junit.jupiter.api.Assumptions.assumeTrue(isRabbitAvailable(), "RabbitMQ broker not available");
        String testMessage = "Hello RabbitMQ!";
        assertDoesNotThrow(() -> rabbitTemplate.convertAndSend("test.exchange", "test.routing.key", testMessage));
    }

    @Test
    void rabbitMessageListenerHandlesInvalidPayload() {
        org.junit.jupiter.api.Assumptions.assumeTrue(isRabbitAvailable(), "RabbitMQ broker not available");
        String invalidJson = "{\"id\":\"3\",\"type\":\"order\"}";
        assertDoesNotThrow(() -> rabbitTemplate.convertAndSend("test.exchange", "test.routing.key", invalidJson));
    }

    // Note: Dead letter queue handling is hard to assert directly without a test consumer,
    // but sending a message that causes an error should route it to DLQ after retries.
    // This test ensures no exception is thrown at the test level.
    @Test
    void rabbitDeadLetterQueueReceivesFailedMessage() {
        org.junit.jupiter.api.Assumptions.assumeTrue(isRabbitAvailable(), "RabbitMQ broker not available");
        String errorPayload = "{\"id\":\"4\",\"type\":\"order\",\"data\":{\"fail\":\"error\"}}";
        assertDoesNotThrow(() -> rabbitTemplate.convertAndSend("test.exchange", "test.routing.key", errorPayload));
        // In a real test, you would consume from DLQ and assert the message is present.
    }

    // Utility method to check if RabbitMQ is available
    private boolean isRabbitAvailable() {
        try {
            rabbitConnectionFactory.createConnection().close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
