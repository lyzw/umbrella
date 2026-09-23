package cn.studykid.growthplanet;

import cn.studykid.growthplanet.dto.response.AdminLoginResp;
import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 对象存储（七牛云 Kodo）集成测试：
 * 客户端直传凭证签发（图片/视频 key 前缀与策略）、参数校验 400、无权限角色 403。
 *
 * <p>测试凭证为 application-test.yml 中的合成值；凭证签发是纯 HMAC 计算，不发真实网络请求。</p>
 */
class AdminStorageIT extends BaseIT {

    private String adminLogin() throws Exception {
        var result = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(JSON)
                        .content("{\"username\":\"admin.zhou\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return dataOf(result, AdminLoginResp.class).getToken();
    }

    /** 用 SA 创建一个指定角色的新后台账号并登录，返回其 token。 */
    private String roleLogin(String saToken, String roleCode) throws Exception {
        String username = "it-" + roleCode.toLowerCase() + "-" + System.nanoTime();
        mockMvc.perform(post("/api/admin/accounts")
                        .header("Authorization", "Bearer " + saToken)
                        .contentType(JSON)
                        .content("{\"username\":\"" + username + "\",\"name\":\"IT" + roleCode
                                + "\",\"password\":\"Passw0rd!\",\"roleCode\":\"" + roleCode + "\"}"))
                .andExpect(status().isOk());
        var login = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return dataOf(login, AdminLoginResp.class).getToken();
    }

    private JsonNode requestToken(String token, String body) throws Exception {
        var result = mockMvc.perform(post("/api/admin/storage/upload-token")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    @Test
    @DisplayName("OP 取图片凭证：key 走 img/dish/ 前缀，公网 URL 与策略参数正确")
    void opCanGetImageUploadToken() throws Exception {
        String opToken = roleLogin(adminLogin(), "OP");

        JsonNode data = requestToken(opToken, "{\"bizType\":\"dish\",\"fileName\":\"cover.png\"}");

        assertThat(data.path("uploadToken").asText()).isNotBlank();
        assertThat(data.path("key").asText()).startsWith("img/dish/").endsWith(".png");
        assertThat(data.path("publicUrl").asText())
                .startsWith("https://image.studykid.cn/img/dish/")
                .endsWith(".png");
        assertThat(data.path("publicUrl").asText())
                .isEqualTo("https://image.studykid.cn/" + data.path("key").asText());
        assertThat(data.path("uploadHost").asText()).isEqualTo("https://upload.qiniup.com");
        assertThat(data.path("expiresInSeconds").asLong()).isEqualTo(3600L);
        assertThat(data.path("maxSizeBytes").asLong()).isEqualTo(5242880L);
        assertThat(data.path("mimeLimit").asText()).isEqualTo("image/*");
    }

    @Test
    @DisplayName("OP 取视频凭证：key 走 video/dish/ 前缀，策略为 video/* 与 200MB 上限")
    void opCanGetVideoUploadToken() throws Exception {
        String opToken = roleLogin(adminLogin(), "OP");

        JsonNode data = requestToken(opToken, "{\"bizType\":\"dish\",\"fileName\":\"clip.mp4\"}");

        assertThat(data.path("key").asText()).startsWith("video/dish/").endsWith(".mp4");
        assertThat(data.path("maxSizeBytes").asLong()).isEqualTo(209715200L);
        assertThat(data.path("mimeLimit").asText()).isEqualTo("video/*");
    }

    @Test
    @DisplayName("参数校验：非法 bizType / 非法后缀 / 缺扩展名 / 缺 fileName 一律 400")
    void invalidArgumentsRejected() throws Exception {
        String opToken = roleLogin(adminLogin(), "OP");

        for (String body : new String[]{
                "{\"bizType\":\"hack\",\"fileName\":\"cover.png\"}",
                "{\"bizType\":\"dish\",\"fileName\":\"evil.exe\"}",
                "{\"bizType\":\"dish\",\"fileName\":\"noext\"}",
                "{\"bizType\":\"dish\",\"fileName\":\"\"}",
                "{\"bizType\":\"\",\"fileName\":\"cover.png\"}"}) {
            mockMvc.perform(post("/api/admin/storage/upload-token")
                            .header("Authorization", "Bearer " + opToken)
                            .contentType(JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    @DisplayName("越权拦截：CR（内容审核员）无「对象存储:新建」权限 → 403")
    void crForbidden() throws Exception {
        String crToken = roleLogin(adminLogin(), "CR");

        mockMvc.perform(post("/api/admin/storage/upload-token")
                        .header("Authorization", "Bearer " + crToken)
                        .contentType(JSON)
                        .content("{\"bizType\":\"dish\",\"fileName\":\"cover.png\"}"))
                .andExpect(status().isForbidden());
    }
}
