package cn.studykid.growthplanet.config;

import cn.studykid.growthplanet.interceptor.JwtInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 小程序端 Web MVC 配置：注册 C 端鉴权拦截器。
 * <p>C 端接口前缀统一为 {@code /api/mini/**}，拦截器按该前缀精确限定——
 * 拆分后后台运营端（{@code /api/admin/**}，见 sk-growthplanet-admin）与本模块共存于
 * 同一 Spring 上下文，若此处挂 {@code /**} 会误拦运营端接口，故不再使用排除路径策略。</p>
 */
@Configuration
public class MiniappWebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;

    public MiniappWebMvcConfig(JwtInterceptor jwtInterceptor) {
        this.jwtInterceptor = jwtInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/mini/**")
                .excludePathPatterns("/api/mini/auth/wx-login");
    }
}
