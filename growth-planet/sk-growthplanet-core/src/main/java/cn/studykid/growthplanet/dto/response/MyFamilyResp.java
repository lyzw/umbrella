package cn.studykid.growthplanet.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 「我的家庭」列表项：当前用户在某家庭下的归属视图。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyFamilyResp {
    /** 家庭 ID。 */
    private Long familyId;
    /** 家庭名称。 */
    private String familyName;
    /** 我在该家庭中的角色：PARENT / CHILD。 */
    private String role;
    /** 绑定状态：PENDING / BOUND / REJECTED。 */
    private String bindStatus;
    /** 关系标签（爸/妈/爷爷/孩子…）。 */
    private String relationLabel;
    /** 我是否为该家庭的 owner。 */
    private Boolean isOwner;
}
