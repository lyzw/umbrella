package cn.studykid.growthplanet.service.impl;

import cn.studykid.growthplanet.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.type.TypeReference;

import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 微信 access_token 获取与缓存。
 * token 有效期 7200s，提前 5 分钟过期以避免临界失效；双检查锁保证并发下只打一次微信接口。
 * 设计约束：不记录任何凭证或 token 明文到日志。
 */
@Slf4j
class WechatTokenProvider {

    private final RestTemplate restTemplate;
    private final String appid;
    private final String secret;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile String token;
    private volatile long expiresAt = 0L;

    WechatTokenProvider(RestTemplate restTemplate, String appid, String secret) {
        this.restTemplate = restTemplate;
        this.appid = appid;
        this.secret = secret;
    }

    String getAccessToken() {
        long now = System.currentTimeMillis();
        if (token != null && now < expiresAt) {
            return token;
        }
        lock.lock();
        try {
            now = System.currentTimeMillis();
            if (token != null && now < expiresAt) {
                return token;
            }
            var uri = UriComponentsBuilder
                    .fromUriString("https://api.weixin.qq.com/cgi-bin/token")
                    .queryParam("grant_type", "client_credential")
                    .queryParam("appid", appid)
                    .queryParam("secret", secret)
                    .build().toUri();
            String body = restTemplate.getForObject(uri, String.class);
            Map<String, Object> map = JsonUtils.fromJson(body, new TypeReference<Map<String, Object>>() {
            });
            Object errcode = map.get("errcode");
            if (errcode != null && !"0".equals(String.valueOf(errcode))) {
                log.warn("wx_token_error errcode={}", errcode);
                throw new IllegalStateException("wx token error");
            }
            Object accessToken = map.get("access_token");
            if (accessToken == null) {
                throw new IllegalStateException("wx token missing");
            }
            Object expiresIn = map.get("expires_in");
            long ttl = expiresIn == null ? 7200L : Long.parseLong(String.valueOf(expiresIn));
            token = String.valueOf(accessToken);
            // 提前 5 分钟刷新；至少保留 60s 缓冲。
            expiresAt = System.currentTimeMillis() + Math.max(60_000L, (ttl - 300L) * 1000L);
            return token;
        } finally {
            lock.unlock();
        }
    }
}
