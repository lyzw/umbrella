package com.growthplanet.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * JWT 配置：启用 {@link JwtProperties} 绑定。
 * JwtUtil 以 @Component 形式存在，此处仅负责属性绑定使能。
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {
}
