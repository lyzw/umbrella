package cn.studykid.growthplanet.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class PrivacyTransitionReq {
    @NotNull @PositiveOrZero
    private Integer expectedVersion;
    @NotNull @Pattern(regexp = "PROCESSING|READY|COMPLETED|FAILED|REJECTED")
    private String status;
    @Positive
    private Long dueAt;
    @Pattern(regexp = "[A-Za-z0-9_-]{1,128}")
    private String evidenceRef;
    @Pattern(regexp = "IDENTITY_UNCONFIRMED|OUT_OF_SCOPE|MANUAL_REVIEW_FAILED")
    private String errorCode;
}
