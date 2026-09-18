package cn.studykid.growthplanet.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.Set;

@Data
@Component
@ConfigurationProperties(prefix = "privacy")
public class PrivacyProperties {
    private Set<Long> operatorIds = Set.of();
}
