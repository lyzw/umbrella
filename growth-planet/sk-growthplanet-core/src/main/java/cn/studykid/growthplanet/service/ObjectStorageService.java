package cn.studykid.growthplanet.service;

import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.common.result.ResultCode;
import cn.studykid.growthplanet.config.QiniuProperties;
import cn.studykid.growthplanet.dto.response.UploadTokenResp;
import com.qiniu.util.Auth;
import com.qiniu.util.StringMap;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 对象存储（七牛云 Kodo）能力服务：签发客户端直传凭证、拼装公网 URL。
 *
 * <p>上传架构为「客户端直传」：后端只生成 key 与短时效凭证（限定单一 key、insertOnly、
 * MIME 与大小限制），不接收也不落盘文件字节，节省应用层带宽与内存。</p>
 *
 * <p>Key 规则：图片 {@code img/{bizType}/{yyyy}/{MM}/{uuid32}.{ext}}，
 * 视频 {@code video/{bizType}/{yyyy}/{MM}/{uuid32}.{ext}}；公网 URL = 读取域名 + "/" + key。</p>
 */
@Service
public class ObjectStorageService {

    /** bizType 白名单（本期仅 dish 接入业务，其余为同一套能力的预留位）。 */
    private static final Set<String> BIZ_TYPES = Set.of("dish", "medal", "ugc", "banner");

    /** 图片来源后缀白名单。 */
    private static final Set<String> IMAGE_EXTS = Set.of("jpg", "jpeg", "png", "webp", "gif");

    /** 视频来源后缀白名单。 */
    private static final Set<String> VIDEO_EXTS = Set.of("mp4", "mov", "webm");

    private static final String MIME_IMAGE = "image/*";
    private static final String MIME_VIDEO = "video/*";

    private static final DateTimeFormatter DIR_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM");

    private final QiniuProperties properties;

    public ObjectStorageService(QiniuProperties properties) {
        this.properties = properties;
    }

    /** 对象存储是否已完整配置（AK/SK/Bucket 齐全）。 */
    public boolean isConfigured() {
        return properties.isComplete();
    }

    /** 由 key 拼装公网可访问 URL。 */
    public String publicUrl(String key) {
        return properties.normalizedImageDomain() + "/" + key;
    }

    /**
     * 为一次直传签发凭证。
     *
     * @param bizType  业务类型（白名单）
     * @param fileName 原始文件名（决定后缀与图片/视频归类）
     * @return 凭证与 key、公网 URL、限制参数
     * @throws BizException E-503 未配置对象存储；E-400 bizType 或后缀不合法
     */
    public UploadTokenResp genUploadToken(String bizType, String fileName) {
        if (!isConfigured()) {
            throw new BizException(ResultCode.E503_UNAVAILABLE, "对象存储未配置，暂时无法上传");
        }
        String biz = normalizeBizType(bizType);
        String ext = extensionOf(fileName);
        boolean video = VIDEO_EXTS.contains(ext);
        if (!video && !IMAGE_EXTS.contains(ext)) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "不支持的文件后缀：" + ext);
        }

        String prefix = ensureTrailingSlash(video ? properties.getVideoPrefix() : properties.getImgPrefix());
        String key = prefix + biz + "/" + LocalDate.now().format(DIR_FORMAT) + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + ext;
        long maxBytes = video ? properties.getVideoMaxBytes() : properties.getImageMaxBytes();
        String mimeLimit = video ? MIME_VIDEO : MIME_IMAGE;

        // 最小权限凭证：限定单一 key + 禁止覆盖（insertOnly）+ MIME 与大小上限。
        StringMap policy = new StringMap()
                .put("insertOnly", 1)
                .put("mimeLimit", mimeLimit)
                .put("fsizeLimit", maxBytes);
        String uploadToken = Auth.create(properties.getAccessKey(), properties.getSecretKey())
                .uploadToken(properties.getBucket(), key, properties.getTokenTtlSeconds(), policy);

        return new UploadTokenResp(uploadToken, key, publicUrl(key), properties.getUploadHost(),
                properties.getTokenTtlSeconds(), maxBytes, mimeLimit);
    }

    private static String normalizeBizType(String bizType) {
        String biz = bizType == null ? "" : bizType.trim().toLowerCase(Locale.ROOT);
        if (!BIZ_TYPES.contains(biz)) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "不支持的 bizType：" + bizType);
        }
        return biz;
    }

    private static String extensionOf(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "fileName 不能为空");
        }
        String name = fileName.trim();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "文件名缺少扩展名：" + fileName);
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String ensureTrailingSlash(String prefix) {
        String value = prefix == null ? "" : prefix.trim();
        if (value.isEmpty()) {
            return "";
        }
        return value.endsWith("/") ? value : value + "/";
    }
}
