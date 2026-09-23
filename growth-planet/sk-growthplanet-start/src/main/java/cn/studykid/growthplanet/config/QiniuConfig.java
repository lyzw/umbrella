package cn.studykid.growthplanet.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 七牛云对象存储配置：启用 {@link QiniuProperties} 绑定，并执行「快速失败 + 优雅降级」启动校验。
 *
 * <ul>
 *   <li><b>全空</b>（AK/SK/Bucket 均未提供）：仅记 WARN，不阻断启动；上传凭证接口返回 E-503。</li>
 *   <li><b>部分配置</b>（三者提供了但不齐）：抛异常终止启动，避免线上凭证签发静默失效。</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(QiniuProperties.class)
public class QiniuConfig {

    private static final Logger log = LoggerFactory.getLogger(QiniuConfig.class);

    private final QiniuProperties properties;

    public QiniuConfig(QiniuProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void validate() {
        if (properties.isBlank()) {
            log.warn("[qiniu] 未配置对象存储（QINIU_ACCESS_KEY / QINIU_SECRET_KEY / QINIU_BUCKET 均为空），"
                    + "上传凭证接口将以 E-503 降级；如需启用请注入上述环境变量。");
            return;
        }
        if (!properties.isComplete()) {
            throw new IllegalStateException("七牛对象存储配置不完整：accessKey / secretKey / bucket "
                    + "必须同时提供（当前仅配置了部分项，已拒绝启动以避免上传静默失效）。");
        }
        log.info("[qiniu] 对象存储已启用：bucket={}，读取域名={}，上传入口={}，凭证有效期={}s",
                properties.getBucket(), properties.normalizedImageDomain(),
                properties.getUploadHost(), properties.getTokenTtlSeconds());
    }
}
