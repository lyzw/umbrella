package cn.studykid.growthplanet.config;

import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.common.result.ResultCode;
import cn.studykid.growthplanet.dto.request.ChildProfileReq;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "compliance")
public class ComplianceProperties {
    private boolean collectionEnabled;
    private boolean selfAttestationAccepted;
    private String approvalReference = "";
    private String catalogReference = "";
    private String agreementVersion = "v1";
    private String agreementText = "";
    private List<String> grades = List.of();
    private List<String> allergens = List.of();

    public void requireCollection() {
        if (!collectionEnabled || approvalReference.isBlank() || agreementText.isBlank()
                || agreementVersion.isBlank() || agreementVersion.length() > 16) {
            throw new BizException(ResultCode.E009_FORBIDDEN, "Q-01 尚未批准");
        }
    }

    public void validateProfile(ChildProfileReq req) {
        requireCollection();
        if (catalogReference.isBlank() || !grades.contains(req.getGrade())
                || !allergens.containsAll(req.getAllergies())) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "年级或过敏原不在发布目录");
        }
    }
}
