package cn.studykid.growthplanet.config;

import cn.studykid.growthplanet.service.NoticeDeliveryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "notice.delivery-enabled", havingValue = "true")
public class NoticeSchedulingConfiguration {
    private final NoticeDeliveryService delivery;

    public NoticeSchedulingConfiguration(NoticeDeliveryService delivery) {
        this.delivery = delivery;
    }

    @Scheduled(fixedDelayString = "${notice.scan-delay-ms:10000}", initialDelayString = "${notice.scan-delay-ms:10000}")
    public void scan() {
        delivery.deliverBatch();
    }
}
