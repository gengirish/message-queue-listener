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
}
