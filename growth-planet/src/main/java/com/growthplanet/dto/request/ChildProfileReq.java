package com.growthplanet.dto.request;

import lombok.Data;

import java.util.List;

/**
 * 儿童档案提交请求。
 */
@Data
public class ChildProfileReq {
    private String nickname;
    private String grade;
    private String school;
    private List<String> allergies;
    private List<String> dislikes;
    private List<String> tastes;
}
