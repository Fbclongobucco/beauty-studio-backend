package com.longobuccodev.beautystudio.messaging.infra;

import com.longobuccodev.beautystudio.messaging.application.EventHandler;
import com.longobuccodev.beautystudio.messaging.application.EventPublisher;
import com.longobuccodev.beautystudio.messaging.core.AppointmentEvent;
import com.longobuccodev.beautystudio.messaging.core.EventContract;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.thread.Threading;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Topologia e adaptadores AMQP compartilhados. Exchanges sao declaradas por todos os servicos;
 * a fila consumidora (com filas de espera e DLQ) so quando {@code beauty.messaging.consumer.queue} esta definida.
 */
@AutoConfiguration(after = RabbitAutoConfiguration.class)
@EnableConfigurationProperties(MessagingProperties.class)
public class MessagingAutoConfiguration {

    /** Liga publisher confirms e returns na conexao do Spring: sem eles nao ha garantia de entrega. */
    @Bean
    static BeanPostProcessor publisherConfirmsPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) {
                if (bean instanceof CachingConnectionFactory factory) {
                    factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
                    factory.setPublisherReturns(true);
                }
                return bean;
            }
        };
    }

    @Bean
    TopicExchange appointmentsExchange() {
        return new TopicExchange(EventContract.APPOINTMENTS_EXCHANGE, true, false);
    }

    @Bean
    DirectExchange appointmentsDeadLetterExchange() {
        return new DirectExchange(EventContract.DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    @ConditionalOnMissingBean
    AppointmentEventCodec appointmentEventCodec(JsonMapper jsonMapper) {
        return new AppointmentEventCodec(jsonMapper);
    }

    @Bean
    @ConditionalOnMissingBean(EventPublisher.class)
    @ConditionalOnBean(RabbitTemplate.class)
    EventPublisher rabbitEventPublisher(RabbitTemplate template, AppointmentEventCodec codec,
                                        MessagingProperties properties) {
        return new RabbitEventPublisher(template, codec, properties.publishConfirmTimeout());
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "beauty.messaging.consumer", name = "queue")
    static class ConsumerConfiguration {

        @Bean
        Declarables appointmentConsumerTopology(MessagingProperties properties) {
            return ConsumerTopology.declarables(properties.consumer());
        }

        @Bean
        @ConditionalOnBean(EventHandler.class)
        SimpleMessageListenerContainer appointmentEventListenerContainer(
                ConnectionFactory connectionFactory, EventHandler<AppointmentEvent> handler,
                AppointmentEventCodec codec, RabbitTemplate template, MessagingProperties properties,
                Environment environment) {
            MessagingProperties.Consumer consumer = properties.consumer();
            SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(connectionFactory);
            container.setQueueNames(consumer.queue());
            container.setAcknowledgeMode(AcknowledgeMode.MANUAL);
            container.setPrefetchCount(consumer.prefetch());
            container.setDefaultRequeueRejected(false);
            if (Threading.VIRTUAL.isActive(environment)) {
                SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("amqp-consumer-");
                executor.setVirtualThreads(true);
                container.setTaskExecutor(executor);
            }
            container.setMessageListener(new RetryingEventListener(handler, codec, template, consumer));
            return container;
        }
    }

    /** Filas do consumidor: principal, uma de espera por intervalo de retentativa e a DLQ. */
    public static final class ConsumerTopology {

        private ConsumerTopology() {
        }

        public static Declarables declarables(MessagingProperties.Consumer consumer) {
            List<Declarable> out = new ArrayList<>();
            TopicExchange exchange = new TopicExchange(EventContract.APPOINTMENTS_EXCHANGE, true, false);
            DirectExchange dlx = new DirectExchange(EventContract.DEAD_LETTER_EXCHANGE, true, false);

            Queue main = QueueBuilder.durable(consumer.queue())
                    .deadLetterExchange(EventContract.DEAD_LETTER_EXCHANGE)
                    .deadLetterRoutingKey(consumer.deadLetterQueue())
                    .build();
            out.add(main);
            for (String key : consumer.routingKeys()) {
                out.add(BindingBuilder.bind(main).to(exchange).with(key));
            }

            for (int i = 0; i < consumer.retryDelays().size(); i++) {
                // Sem consumidor: ao expirar o TTL, a mensagem volta para a fila principal pela exchange padrao
                out.add(QueueBuilder.durable(consumer.retryQueue(i))
                        .ttl((int) consumer.retryDelays().get(i).toMillis())
                        .deadLetterExchange("")
                        .deadLetterRoutingKey(consumer.queue())
                        .build());
            }

            Queue dlq = QueueBuilder.durable(consumer.deadLetterQueue()).build();
            out.add(dlq);
            Binding dlqBinding = BindingBuilder.bind(dlq).to(dlx).with(consumer.deadLetterQueue());
            out.add(dlqBinding);
            return new Declarables(out);
        }
    }
}
