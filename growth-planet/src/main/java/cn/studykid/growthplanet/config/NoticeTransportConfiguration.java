package cn.studykid.growthplanet.config;

import cn.studykid.growthplanet.service.NoticeTransport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NoticeTransportConfiguration {
    @Bean
    @ConditionalOnMissingBean(NoticeTransport.class)
    public NoticeTransport gatedNoticeTransport() {
        return new NoticeTransport() {
            @Override
            public boolean isConfigured() {
                return false;
            }

            @Override
            public Outcome send(String openid, String templateId, String eventType, String eventKey) {
                throw new IllegalStateException("Wechat subscription transport is not configured");
            }
        };
    }
}
