package cn.studykid.growthplanet.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 合规清单勾检请求（M5，资源=合规清单，config 列 = CP/SA）。
 * <p>按 item_key 切换勾检状态；缺省 checked=true 表示勾检完成。</p>
 */
@Data
public class AdminComplianceCheckReq {

    /** 目标清单项静态键（sys_compliance_checklist.item_key）。 */
    @NotNull
    private String itemKey;

    /** 勾检状态：true=已勾检，false=取消勾检。缺省视为 true。 */
    private Boolean checked;
}
