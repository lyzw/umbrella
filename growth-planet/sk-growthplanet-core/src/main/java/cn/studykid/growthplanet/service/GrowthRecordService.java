package cn.studykid.growthplanet.service;

import cn.studykid.growthplanet.dto.request.GrowthRecordReq;
import cn.studykid.growthplanet.dto.response.GrowthRecordItem;
import cn.studykid.growthplanet.dto.response.GrowthRecordsResp;
import cn.studykid.growthplanet.dto.response.GrowthSaveResp;
import cn.studykid.growthplanet.dto.response.GrowthStandardResp;

import java.util.List;

/**
 * 儿童成长记录：家长录入身高体重（服务端算 BMI 并按 WHO 示意标准给分位评估），
 * 以及 WHO 生长标准分位表查询。
 */
public interface GrowthRecordService {

    /** 新增记录（PARENT，绑定 + 同意 + 档案完整）。 */
    GrowthSaveResp save(GrowthRecordReq req);

    /** 记录列表（PARENT 本家庭儿童 / CHILD 本人；按 record_date DESC，limit 默认 30）。 */
    GrowthRecordsResp list(Long childId, int limit);

    /** 单条记录（含评估），供详情/曲线最新点。 */
    GrowthRecordItem latest(Long childId);

    /** WHO 标准分位表。 */
    GrowthStandardResp standard(String ageBand, String gender, String metric);

    /** 评估一条记录的条目（内部复用，供列表与最新点）。 */
    GrowthRecordItem evaluate(GrowthRecordItem item, String gender);
}
