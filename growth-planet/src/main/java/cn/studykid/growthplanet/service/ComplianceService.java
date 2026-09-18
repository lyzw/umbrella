package cn.studykid.growthplanet.service;

import cn.studykid.growthplanet.dto.request.ConsentReq;
import cn.studykid.growthplanet.dto.request.DataExportReq;
import cn.studykid.growthplanet.dto.request.RevokeConsentReq;
import cn.studykid.growthplanet.dto.request.DataDeleteReq;
import cn.studykid.growthplanet.dto.request.PrivacyTransitionReq;
import cn.studykid.growthplanet.dto.response.ConsentResp;
import cn.studykid.growthplanet.dto.response.DataExportResp;

/**
 * COMPLIANCE 模块业务接口：同意书查询 / 提交 / 撤回 / 数据导出。
 */
public interface ComplianceService {

    /** 查询同意书（PARENT）。 */
    ConsentResp getConsent(Long childId, String consentType);

    /** 提交同意书（PARENT）。 */
    ConsentResp submitConsent(ConsentReq req);

    /** 撤回同意书（PARENT），返回监护人状态（REVOKED）。 */
    String revokeConsent(RevokeConsentReq req);

    /** 数据导出（PARENT）。 */
    DataExportResp dataExport(DataExportReq req, String idempotencyKey);

    DataExportResp getRequest(Long id);

    DataExportResp dataDelete(DataDeleteReq req, String idempotencyKey);

    DataExportResp transitionRequest(Long id, PrivacyTransitionReq req);

    byte[] download(Long id);
}
