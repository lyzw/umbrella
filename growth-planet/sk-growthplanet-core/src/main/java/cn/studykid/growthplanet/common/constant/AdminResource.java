package cn.studykid.growthplanet.common.constant;

/**
 * 后台资源标识常量（权限矩阵 resource 维度）。与详细设计文档 §3.4「细粒度权限点矩阵」的资源列一致，
 * 用作 sys_role_permission.resource 的取值与后端 {@code AdminUserContext.requirePerm(...)} 的入参。
 */
public final class AdminResource {

    private AdminResource() {
    }

    public static final String WORKBENCH = "工作台首页";
    public static final String DASHBOARD = "运营看板";
    public static final String REPORT_EXPORT = "报表导出";

    public static final String ACCOUNT = "后台账号";
    public static final String ROLE = "角色权限";
    public static final String FAMILY_QUERY = "家庭/成员查询";
    public static final String RISK_CONTROL = "账号风控";

    public static final String DISH = "菜品库";
    public static final String DISH_CATEGORY = "菜品分类";
    public static final String MENU = "菜单编排";
    public static final String TASK_LIB = "任务库/奖励库";
    public static final String MEDAL = "勋章配置";
    public static final String WISH_MENU = "心愿菜单配置";
    public static final String UGC_QUEUE = "UGC审核队列";
    public static final String WORD_LIB = "词库管理";

    public static final String WANT_EAT = "每日想吃";
    public static final String CONFIRM = "确认单";
    public static final String APPROVAL = "审批记录";
    public static final String WALLET = "钱包与流水";
    public static final String CHORE_HEALTH = "家务健康";
    public static final String MEDAL_AWARD = "勋章发放/家庭设置";

    public static final String CONSENT = "同意留痕";
    public static final String PRIVACY_TICKET = "隐私工单";
    public static final String VERIFY = "核验记录";
    public static final String COMPLIANCE = "合规清单";

    public static final String AUDIT_LOG = "操作日志";
    public static final String C_AUDIT = "C端关键操作";

    public static final String NOTICE_TPL = "通知模板";
    public static final String NOTICE_STAT = "触达统计";

    public static final String ALERT_RULE = "告警规则";
    public static final String ALERT_LIST = "告警列表";
    public static final String CHANNEL = "通道配置";

    public static final String GLOBAL_PARAM = "全局参数";
    public static final String ENUM_DICT = "枚举字典";
    public static final String FEATURE_SWITCH = "功能开关";

    public static final String HELP = "手册/FAQ";

    /**
     * 全部资源，按控制台模块顺序排列，用作权限矩阵的行顺序（M0–M10）。
     * 注意：须在以上常量之后声明，避免静态初始化顺序问题。
     */
    public static final java.util.List<String> ALL = java.util.List.of(
            WORKBENCH,
            DASHBOARD, REPORT_EXPORT,
            ACCOUNT, ROLE, FAMILY_QUERY, RISK_CONTROL,
            DISH, DISH_CATEGORY, MENU, TASK_LIB, MEDAL, WISH_MENU, UGC_QUEUE, WORD_LIB,
            WANT_EAT, CONFIRM, APPROVAL, WALLET, CHORE_HEALTH, MEDAL_AWARD,
            CONSENT, PRIVACY_TICKET, VERIFY, COMPLIANCE,
            AUDIT_LOG, C_AUDIT,
            NOTICE_TPL, NOTICE_STAT,
            ALERT_RULE, ALERT_LIST, CHANNEL,
            GLOBAL_PARAM, ENUM_DICT, FEATURE_SWITCH,
            HELP);
}
