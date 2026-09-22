package cn.studykid.growthplanet.config;

import cn.studykid.growthplanet.interceptor.AdminJwtInterceptor;
import cn.studykid.growthplanet.interceptor.JwtInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册两套鉴权拦截器，账号/会话/ token 体系完全隔离。
 * <ul>
 *   <li>C 端（小程序）：{@link JwtInterceptor} 覆盖 {@code /**}，放行登录/actuator/error；</li>
 *   <li>后台运营端：{@link AdminJwtInterceptor} 仅覆盖 {@code /api/console/**}，
 *       使用独立的 sys_admin_user 账号 + 独立 admin 密钥/issuer，与 C 端 token 互不可解。</li>
 * </ul>
 * 后台前缀从 {@code /api/admin/**} 改为 {@code /api/console/**}，以零破坏方式隔离既有
 * C 端 ADMIN 接口（MenuController/ComplianceController）与其 6 个集成测试。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;
    private final AdminJwtInterceptor adminJwtInterceptor;

    public WebMvcConfig(JwtInterceptor jwtInterceptor, AdminJwtInterceptor adminJwtInterceptor) {
        this.jwtInterceptor = jwtInterceptor;
        this.adminJwtInterceptor = adminJwtInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/api/auth/wx-login",
                        "/api/console/**",
                        "/actuator/**",
                        "/error");

        registry.addInterceptor(adminJwtInterceptor)
                .addPathPatterns("/api/console/**")
                .excludePathPatterns("/api/console/auth/login");
    }
}
