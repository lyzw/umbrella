package cn.studykid.growthplanet.dto.response;

/**
 * 客户端直传凭证响应：前端拿到 {@code uploadToken} 与 {@code key} 后直传七牛，
 * 上传成功后以 {@code publicUrl} 回填业务字段。
 */
public record UploadTokenResp(
        /** 七牛上传凭证（uptoken），短时效且限定单一 key。 */
        String uploadToken,
        /** 后端生成的唯一对象 key（含 img/ 或 video/ 前缀）。 */
        String key,
        /** 上传后可直接访问的公网 URL。 */
        String publicUrl,
        /** 上传入口（POST 目标地址）。 */
        String uploadHost,
        /** 凭证有效期（秒）。 */
        long expiresInSeconds,
        /** 该类型文件大小上限（字节），已写入凭证策略。 */
        long maxSizeBytes,
        /** 允许的 MIME 类型（已写入凭证策略）。 */
        String mimeLimit) {
}
