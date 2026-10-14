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
    /**
     * 各事件类型对应的订阅消息 data 模板（JSON 字符串），其中 {@code {eventKey}} 占位符在发送时替换为通知的 eventKey。
     * 模板的字段名/结构须与微信后台该 templateId 的关键词一一对应，否则微信会拒绝（REJECTED）。
     * 例：{@code notice.data-templates.menu-reminder={"thing1":{"value":"{eventKey}"}}}
     */
    private Map<String, String> dataTemplates = new HashMap<>();
}
