package com.fancia.backend.payment.config

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder

@Configuration
class KafkaTopicConfig {
    @Bean
    fun subscriptionChangedTopic(): NewTopic =
        TopicBuilder.name("subscriptions").partitions(3).replicas(1).build()

    @Bean
    fun connectCheckoutsTopic(): NewTopic =
        TopicBuilder.name("connect-checkouts").partitions(3).replicas(1).build()
}
