package com.campusmarket.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.event.TransactionalEventListener;

import com.campusmarket.auction.AuctionSettledEvent;
import com.campusmarket.auction.AuctionSettlementMapper;
import com.campusmarket.chat.MarketSocket;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class AuctionSettlementKafka {
    private static final Logger log = LoggerFactory.getLogger(AuctionSettlementKafka.class);

    private final KafkaTemplate<String, AuctionSettledEvent> kafka;
    private final String topic;
    private final AuctionSettlementMapper auctions;
    private final MarketSocket socket;

    public AuctionSettlementKafka(KafkaTemplate<String, AuctionSettledEvent> kafka, AuctionSettlementMapper auctions,
                                  MarketSocket socket,
                                  @Value("${app.kafka.auction-settled-topic}") String topic) {
        this.kafka = kafka;
        this.topic = topic;
        this.auctions = auctions;
        this.socket = socket;
    }

    @Bean
    NewTopic auctionSettledTopic() {
        return TopicBuilder.name(topic).partitions(1).replicas(1).build();
    }

    @TransactionalEventListener
    public void publish(AuctionSettledEvent event) {
        try {
            kafka.send(topic, Long.toString(event.auctionId()), event).whenComplete((result, failure) -> {
                if (failure != null) {
                    log.warn("Could not publish settlement event for auction {}", event.auctionId(), failure);
                }
            });
        } catch (RuntimeException failure) {
            log.warn("Could not publish settlement event for auction {}", event.auctionId(), failure);
        }
    }

    @KafkaListener(topics = "${app.kafka.auction-settled-topic}")
    public void receive(AuctionSettledEvent event) {
        log.info("Received auction settlement: auctionId={}, itemId={}, reservationId={}, outcome={}",
                event.auctionId(), event.itemId(), event.reservationId(), event.outcome());
        var recipients = auctions.recipients(event.auctionId());
        if (recipients != null) {
            socket.auctionSettled(recipients.sellerId(), recipients.buyerId(), event.auctionId(),
                    event.reservationId(), event.outcome().name());
        }
    }
}
