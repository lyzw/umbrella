package cn.studykid.growthplanet.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "notice")
public class NoticeProperties {
    private boolean deliveryEnabled;
    private int batchSize = 50;
    private String platformApprovalReference = "";
    private Map<String, String> templates = new HashMap<>();
}
