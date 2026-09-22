package cn.studykid.growthplanet.config;

import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.common.result.ResultCode;
import cn.studykid.growthplanet.dto.request.ChildProfileReq;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
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
    /**
     * 学校发布目录（R4-lite）。校名仍是 SCHOOL 菜单的归属键文本，但两侧（家长填档案、管理员发布菜单）
     * 都必须从本目录取值，避免手输错字导致「该校孩子看不到菜单」。
     */
    private List<String> schools = List.of();

    public void requireCollection() {
        if (!collectionEnabled || approvalReference.isBlank() || agreementText.isBlank()
                || agreementVersion.isBlank() || agreementVersion.length() > 16) {
            throw new BizException(ResultCode.E009_FORBIDDEN, "Q-01 尚未批准");
        }
    }

    /**
     * 启动期目录体检（R5-d）：开启采集却缺任一目录时直接拒绝启动。
     * 否则过敏原目录为空会让 validAllergens 恒 false ⇒ 全员菜品 UNKNOWN ⇒ 孩子端静默不可点菜（fail-closed 但无任何提示）。
     * collection-enabled=false（prod 默认）时不校验，保持"未开启采集即可启动"的既有行为。
     */
    @PostConstruct
    public void validateCatalogOnStartup() {
        if (!collectionEnabled) {
            return;
        }
        List<String> missing = new ArrayList<>();
        if (approvalReference.isBlank()) {
            missing.add("compliance.approval-reference");
        }
        if (agreementText.isBlank()) {
            missing.add("compliance.agreement-text");
        }
        if (catalogReference.isBlank()) {
            missing.add("compliance.catalog-reference");
        }
        if (grades.isEmpty()) {
            missing.add("compliance.grades");
        }
        if (allergens.isEmpty()) {
            missing.add("compliance.allergens");
        }
        if (schools.isEmpty()) {
            missing.add("compliance.schools");
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("compliance 目录未就绪，拒绝启动以免儿童侧静默不可用：" + missing);
        }
    }

    public void validateProfile(ChildProfileReq req) {
        requireCollection();
        String school = req.getSchool() == null ? null : req.getSchool().trim();
        if (catalogReference.isBlank() || !grades.contains(req.getGrade())
                || !schools.contains(school)
                || !allergens.containsAll(req.getAllergies())) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "年级、学校或过敏原不在发布目录");
        }
    }
}
