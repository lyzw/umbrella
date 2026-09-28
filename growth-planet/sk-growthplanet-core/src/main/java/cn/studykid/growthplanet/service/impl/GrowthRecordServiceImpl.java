package cn.studykid.growthplanet.service.impl;

import cn.studykid.growthplanet.common.context.UserContext;
import cn.studykid.growthplanet.common.enums.RoleEnum;
import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.common.result.ResultCode;
import cn.studykid.growthplanet.dto.request.GrowthRecordReq;
import cn.studykid.growthplanet.dto.response.GrowthRecordItem;
import cn.studykid.growthplanet.dto.response.GrowthRecordsResp;
import cn.studykid.growthplanet.dto.response.GrowthSaveResp;
import cn.studykid.growthplanet.dto.response.GrowthStandardResp;
import cn.studykid.growthplanet.entity.ChildGrowthRecord;
import cn.studykid.growthplanet.entity.FamilyMember;
import cn.studykid.growthplanet.mapper.ChildGrowthRecordMapper;
import cn.studykid.growthplanet.service.ChildAuthorizationService;
import cn.studykid.growthplanet.service.GrowthRecordService;
import cn.studykid.growthplanet.service.GrowthStandardData;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 儿童成长记录业务：家长录入身高体重（服务端算 BMI、按 WHO 示意标准给分位评估），
 * 儿童本人只读；WHO 标准分位表供标准页查询。
 */
@Service
public class GrowthRecordServiceImpl implements GrowthRecordService {

    private static final int DEFAULT_LIMIT = 30;
    private static final int MAX_LIMIT = 100;

    private final ChildGrowthRecordMapper records;
    private final ChildAuthorizationService authorization;

    public GrowthRecordServiceImpl(ChildGrowthRecordMapper records,
                                   ChildAuthorizationService authorization) {
        this.records = records;
        this.authorization = authorization;
    }

    @Override
    @Transactional
    public GrowthSaveResp save(GrowthRecordReq req) {
        var ctx = UserContext.get();
        if (ctx == null || !RoleEnum.PARENT.name().equals(ctx.getRole())) {
            throw new BizException(ResultCode.E009_FORBIDDEN, "仅家长可录入成长记录");
        }
        FamilyMember member = authorization.lockBoundChild(req.getChildId());
        authorization.requireConsent(member);

        ChildGrowthRecord record = new ChildGrowthRecord();
        record.setChildId(req.getChildId());
        record.setFamilyId(member.getFamilyId());
        record.setRecordDate(req.getRecordDate() == null ? LocalDate.now() : req.getRecordDate());
        record.setGender(req.getGender());
        record.setHeightCm(req.getHeightCm().setScale(2, RoundingMode.HALF_UP));
        record.setWeightKg(req.getWeightKg().setScale(2, RoundingMode.HALF_UP));
        record.setBmi(computeBmi(req.getHeightCm(), req.getWeightKg()));
        record.setAgeYears(req.getAgeYears());
        record.setNote(req.getNote());
        record.setCreateBy(ctx.getUserId());
        records.insert(record);
        return GrowthSaveResp.builder().id(record.getId()).build();
    }

    @Override
    @Transactional(readOnly = true)
    public GrowthRecordsResp list(Long childId, int limit) {
        FamilyMember member = readBound(childId);
        authorization.requireConsentReadOnly(member);
        int size = Math.min(limit > 0 ? limit : DEFAULT_LIMIT, MAX_LIMIT);
        List<ChildGrowthRecord> rows = records.selectList(new QueryWrapper<ChildGrowthRecord>()
                .eq("child_id", childId).eq("delete_at", 0)
                .orderByDesc("record_date").last("LIMIT " + size));
        List<GrowthRecordItem> items = new ArrayList<>();
        for (ChildGrowthRecord row : rows) {
            items.add(evaluate(toItem(row), row.getGender()));
        }
        return GrowthRecordsResp.builder().childId(childId).items(items).build();
    }

    @Override
    @Transactional(readOnly = true)
    public GrowthRecordItem latest(Long childId) {
        FamilyMember member = readBound(childId);
        authorization.requireConsentReadOnly(member);
        ChildGrowthRecord row = records.selectOne(new QueryWrapper<ChildGrowthRecord>()
                .eq("child_id", childId).eq("delete_at", 0)
                .orderByDesc("record_date", "id").last("LIMIT 1"));
        return row == null ? null : evaluate(toItem(row), row.getGender());
    }

    @Override
    @Transactional(readOnly = true)
    public GrowthStandardResp standard(String ageBand, String gender, String metric) {
        if (!GrowthStandardData.validBand(ageBand)) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "年龄段仅支持 WHO 2006 / WHO 2007");
        }
        if (!GrowthStandardData.validGender(gender)) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "性别仅支持 BOY/GIRL");
        }
        if (!GrowthStandardData.validMetric(metric)) {
            throw new BizException(ResultCode.E400_INVALID_ARGUMENT, "指标仅支持 HEIGHT/WEIGHT/BMI");
        }
        List<GrowthStandardResp.GrowthStandardRow> rows = new ArrayList<>();
        for (Integer ageYears : GrowthStandardData.ages(gender, ageBand)) {
            GrowthStandardResp.GrowthStandardRow row = GrowthStandardData.row(gender, ageYears, metric, ageBand);
            if (row != null) {
                rows.add(row);
            }
        }
        return GrowthStandardResp.builder()
                .ageBand(ageBand)
                .gender(gender)
                .metric(metric)
                .unit(GrowthStandardData.unitOf(metric))
                .rows(rows)
                .build();
    }

    @Override
    public GrowthRecordItem evaluate(GrowthRecordItem item, String gender) {
        if (item.getAgeYears() == null || gender == null || gender.isBlank()) {
            item.setHeightBand(null);
            item.setWeightBand(null);
            item.setBmiCategory("暂无阈值");
            return item;
        }
        item.setHeightBand(GrowthStandardData.bandOf(gender, item.getAgeYears(),
                GrowthStandardData.HEIGHT, item.getHeightCm().doubleValue()));
        item.setWeightBand(GrowthStandardData.bandOf(gender, item.getAgeYears(),
                GrowthStandardData.WEIGHT, item.getWeightKg().doubleValue()));
        item.setBmiCategory(GrowthStandardData.bmiCategory(gender, item.getAgeYears(),
                item.getBmi().doubleValue()));
        return item;
    }

    private GrowthRecordItem toItem(ChildGrowthRecord row) {
        return GrowthRecordItem.builder()
                .id(row.getId())
                .recordDate(row.getRecordDate())
                .gender(row.getGender())
                .heightCm(row.getHeightCm())
                .weightKg(row.getWeightKg())
                .bmi(row.getBmi())
                .ageYears(row.getAgeYears())
                .note(row.getNote())
                .build();
    }

    private BigDecimal computeBmi(BigDecimal heightCm, BigDecimal weightKg) {
        BigDecimal meters = heightCm.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        return weightKg.divide(meters.multiply(meters), 1, RoundingMode.HALF_UP);
    }

    private FamilyMember readBound(Long childId) {
        FamilyMember member = authorization.boundChild(childId);
        // 读路径：PARENT 本家庭任意孩子 / CHILD 本人均可，需 BOUND 关系。
        return member;
    }
}
