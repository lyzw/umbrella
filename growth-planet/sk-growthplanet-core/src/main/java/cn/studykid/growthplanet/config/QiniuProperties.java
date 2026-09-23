package cn.studykid.growthplanet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * 七牛云 Kodo 对象存储配置属性（前缀 qiniu）。
 *
 * <p>密钥仅从环境变量注入（QINIU_ACCESS_KEY / QINIU_SECRET_KEY / QINIU_BUCKET），
 * 不写死、不入库、不打印日志。三个必需项「全空」表示未启用对象存储，
 * 上传凭证接口降级返回 E-503 且不阻断启动（便于 dev/CI 无凭证环境）；
 * 「部分配置」视为误配，由 {@code QiniuConfig} 在启动时拒绝（防止线上上传静默失效）。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "qiniu")
public class QiniuProperties {

    /** 七牛 AccessKey（密钥，仅环境变量注入）。 */
    private String accessKey;

    /** 七牛 SecretKey（密钥，仅环境变量注入）。 */
    private String secretKey;

    /** 单桶名（图片与视频共用一个 bucket，以 key 前缀区分）。 */
    private String bucket;

    /** 资源读取域名（图片/视频同域，以路径区分），代码归一为 https:// 前缀。 */
    private String imageDomain = "image.studykid.cn";

    /** 上传入口；bucket 机房不同可覆盖（z1/z2/na0/as0 等）。 */
    private String uploadHost = "https://upload.qiniup.com";

    /** 上传凭证有效期（秒），默认 1 小时。 */
    private long tokenTtlSeconds = 3600L;

    /** 图片 key 前缀。 */
    private String imgPrefix = "img/";

    /** 视频 key 前缀。 */
    private String videoPrefix = "video/";

    /** 图片大小上限（字节），默认 5 MB。 */
    private long imageMaxBytes = 5L * 1024 * 1024;

    /** 视频大小上限（字节），默认 200 MB。 */
    private long videoMaxBytes = 200L * 1024 * 1024;

    /** 三个必需项是否全部为空 —— 表示未启用对象存储（降级模式）。 */
    public boolean isBlank() {
        return isBlankValue(accessKey) && isBlankValue(secretKey) && isBlankValue(bucket);
    }

    /** 三个必需项是否已完整配置 —— 表示对象存储可用。 */
    public boolean isComplete() {
        return !isBlankValue(accessKey) && !isBlankValue(secretKey) && !isBlankValue(bucket);
    }

    /** 归一化读取域名：强制 https:// 前缀、去除尾斜杠。 */
    public String normalizedImageDomain() {
        String domain = imageDomain == null ? "" : imageDomain.trim();
        int scheme = domain.indexOf("://");
        if (scheme >= 0) {
            domain = domain.substring(scheme + 3);
        }
        while (domain.endsWith("/")) {
            domain = domain.substring(0, domain.length() - 1);
        }
        return "https://" + domain;
    }

    private static boolean isBlankValue(String value) {
        return value == null || value.isBlank();
    }
}
