package com.xiaolvshu.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置类
 */
@Configuration
public class RabbitMQConfig {

    // ============ 点赞相关常量 ============

    /**
     * 点赞交换机
     */
    public static final String LIKE_EXCHANGE = "like.exchange";

    /**
     * 点赞队列
     */
    public static final String LIKE_QUEUE = "like.queue";

    /**
     * 点赞路由键
     */
    public static final String LIKE_ROUTING_KEY = "like.routing.key";

    /**
     * 点赞死信交换机
     */
    public static final String LIKE_DLX_EXCHANGE = "like.dlx.exchange";

    /**
     * 点赞死信队列
     */
    public static final String LIKE_DLX_QUEUE = "like.dlx.queue";

    /**
     * 点赞死信路由键
     */
    public static final String LIKE_DLX_ROUTING_KEY = "like.dlx.routing.key";

    // ============ 交换机定义 ============

    @Bean
    public DirectExchange likeExchange() {
        return new DirectExchange(LIKE_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange likeDlxExchange() {
        return new DirectExchange(LIKE_DLX_EXCHANGE, true, false);
    }

    // ============ 队列定义 ============

    @Bean
    public Queue likeQueue() {
        return QueueBuilder.durable(LIKE_QUEUE)
                // 绑定死信交换机
                .withArgument("x-dead-letter-exchange", LIKE_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", LIKE_DLX_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue likeDlxQueue() {
        return QueueBuilder.durable(LIKE_DLX_QUEUE).build();
    }

    // ============ 绑定关系 ============

    @Bean
    public Binding likeBinding(Queue likeQueue, DirectExchange likeExchange) {
        return BindingBuilder.bind(likeQueue).to(likeExchange).with(LIKE_ROUTING_KEY);
    }

    @Bean
    public Binding likeDlxBinding(Queue likeDlxQueue, DirectExchange likeDlxExchange) {
        return BindingBuilder.bind(likeDlxQueue).to(likeDlxExchange).with(LIKE_DLX_ROUTING_KEY);
    }

    // ============ 消息转换器 ============

    @Bean
    public MessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter jackson2JsonMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jackson2JsonMessageConverter);
        return rabbitTemplate;
    }
}
