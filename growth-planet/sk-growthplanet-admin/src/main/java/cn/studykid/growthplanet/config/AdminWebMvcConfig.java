package cn.studykid.growthplanet.config;

import cn.studykid.growthplanet.interceptor.AdminJwtInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 后台运营端 Web MVC 配置：注册 Admin 鉴权拦截器。
 * <p>运营端接口前缀统一为 {@code /api/admin/**}（原 {@code /api/console/**}），
 * 使用独立的 sys_admin_user 账号 + 独立 admin 密钥/issuer，与 C 端 token 互不可解。
 * C 端拦截器按 {@code /api/mini/**} 限定（见 sk-growthplanet-miniapp），互不越界。</p>
 */
@Configuration
public class AdminWebMvcConfig implements WebMvcConfigurer {

    private final AdminJwtInterceptor adminJwtInterceptor;

    public AdminWebMvcConfig(AdminJwtInterceptor adminJwtInterceptor) {
        this.adminJwtInterceptor = adminJwtInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminJwtInterceptor)
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns("/api/admin/auth/login");
    }
}
