package cn.studykid.growthplanet.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 访问日志配置：启用 {@link AccessLogProperties} 绑定。
 * 过滤器本身（{@link AccessLogFilter}）以 @Component 注册，此处只负责属性绑定使能。
 */
@Configuration
@EnableConfigurationProperties(AccessLogProperties.class)
public class AccessLogConfig {
}
