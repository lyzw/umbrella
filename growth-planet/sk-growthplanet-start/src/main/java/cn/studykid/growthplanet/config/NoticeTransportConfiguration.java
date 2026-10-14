package cn.studykid.growthplanet.config;

import cn.studykid.growthplanet.service.NoticeTransport;
import cn.studykid.growthplanet.service.impl.WechatNoticeTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 订阅消息传输装配：当配置了真实微信 appid/secret 时装配 {@link WechatNoticeTransport}（F-011 真实下发），
 * 否则保留 gated 桩（isConfigured=false，NoticeDeliveryService 自动跳过投递，安全降级）。
 */
@Configuration
public class NoticeTransportConfiguration {

    @Bean
    public NoticeTransport noticeTransport(
            @Value("${wx.appid:}") String appid,
            @Value("${wx.secret:}") String secret,
            NoticeProperties settings) {
        if (appid == null || appid.isBlank() || secret == null || secret.isBlank()) {
            return gatedTransport();
        }
        return new WechatNoticeTransport(appid, secret, settings);
    }

    private NoticeTransport gatedTransport() {
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
