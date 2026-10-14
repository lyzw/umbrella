package cn.studykid.growthplanet.service.impl;

import cn.studykid.growthplanet.config.NoticeProperties;
import cn.studykid.growthplanet.service.NoticeTransport;
import cn.studykid.growthplanet.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.type.TypeReference;

import java.util.Map;

/**
 * 微信订阅消息真实传输实现（F-011 适配器）。
 * 调用 cgi-bin/message/subscribe/send 下发订阅消息；errcode 映射到 NoticeTransport.Outcome：
 * 0 → ACCEPTED；模板/授权类错误（4xxxx 指定集合）→ REJECTED（终态）；其余/网络 → RETRYABLE。
 * 设计约束：有界超时、绝不记录 openid/凭证/payload 明文。
 */
@Slf4j
public class WechatNoticeTransport implements NoticeTransport {

    private final WechatTokenProvider tokenProvider;
    private final RestTemplate restTemplate;
    private final NoticeProperties settings;

    public WechatNoticeTransport(String appid, String secret, NoticeProperties settings) {
        this.settings = settings;
        this.tokenProvider = new WechatTokenProvider(buildRestTemplate(), appid, secret);
        this.restTemplate = buildRestTemplate();
    }

    /** 测试/显式注入用构造器。 */
    WechatNoticeTransport(WechatTokenProvider tokenProvider, RestTemplate restTemplate, NoticeProperties settings) {
        this.tokenProvider = tokenProvider;
        this.restTemplate = restTemplate;
        this.settings = settings;
    }

    private static RestTemplate buildRestTemplate() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        return new RestTemplate(factory);
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public Outcome send(String openid, String templateId, String eventType, String eventKey) {
        if (templateId == null || templateId.isBlank()) {
            return Outcome.REJECTED;
        }
        String dataTemplate = settings.getDataTemplates().get(eventType);
        if (dataTemplate == null || dataTemplate.isBlank()) {
            // 未配置该事件的 data 模板：发送必然因关键词不匹配被微信拒绝，提前判终态避免无效重试。
            log.warn("wx_notice_no_data_template eventType={}", eventType);
            return Outcome.REJECTED;
        }
        String data = dataTemplate.replace("{eventKey}", eventKey == null ? "" : eventKey);
        try {
            String token = tokenProvider.getAccessToken();
            var uri = UriComponentsBuilder
                    .fromUriString("https://api.weixin.qq.com/cgi-bin/message/subscribe/send")
                    .queryParam("access_token", token).build().toUri();
            Map<String, Object> payload = Map.of(
                    "touser", openid,
                    "template_id", templateId,
                    "data", JsonUtils.fromJson(data, new TypeReference<Map<String, Object>>() {
                    }));
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(JsonUtils.toJson(payload), headers);
            String body = restTemplate.postForObject(uri, entity, String.class);
            Map<String, Object> resp = JsonUtils.fromJson(body, new TypeReference<Map<String, Object>>() {
            });
            Object errcode = resp.get("errcode");
            if (errcode == null) {
                return Outcome.ACCEPTED;
            }
            long code = Long.parseLong(String.valueOf(errcode));
            if (code == 0L) {
                return Outcome.ACCEPTED;
            }
            // 终态拒绝：模板不存在/未授权/数据格式错/用户拒绝接收等。
            if (code == 40003 || code == 40037 || code == 43101
                    || code == 47003 || code == 41030 || code == 41031) {
                log.warn("wx_notice_rejected errcode={} eventType={}", code, eventType);
                return Outcome.REJECTED;
            }
            log.warn("wx_notice_retryable errcode={} eventType={}", code, eventType);
            return Outcome.RETRYABLE_FAILURE;
        } catch (IllegalStateException ex) {
            // access_token 获取失败
            return Outcome.RETRYABLE_FAILURE;
        } catch (RestClientException ex) {
            log.warn("wx_notice_transport_failure eventType={}", eventType);
            return Outcome.RETRYABLE_FAILURE;
        } catch (RuntimeException ex) {
            log.warn("wx_notice_failure eventType={}", eventType);
            return Outcome.RETRYABLE_FAILURE;
        }
    }
}
