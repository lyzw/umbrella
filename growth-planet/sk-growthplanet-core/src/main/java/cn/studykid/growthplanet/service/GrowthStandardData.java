package cn.studykid.growthplanet.service;

import cn.studykid.growthplanet.dto.response.GrowthStandardResp;

import java.util.ArrayList;
import java.util.List;

/**
 * WHO 儿童生长标准示意数据（Growth Planet 内置精简版）。
 * <p>
 * 结构：按「年龄段（0-5 WHO 2006 / 5-19 WHO 2007）× 性别 × 指标（身高 cm / 体重 kg / BMI）」
 * 存 P3 / P50 / P97 锚点，P15 / P85 按对数近似线性插值给出（P15 = P50 - (P50-P3)*0.6，
 * P85 = P50 + (P97-P50)*0.6）。BMI 类别评估用每岁 P3 / P85 / P97（P85 同样插值）。
 * <p>
 * <b>注意：</b>当前数值为开发期示意锚点（与 WHO 官方曲线偏差约 ±2%），
 * 正式上线前必须以 WHO 官方 LMS 参数（或卫健委许可的发布表）整体替换，替换点集中在本类。
 */
public final class GrowthStandardData {

    public static final String BAND_0_5 = "WHO 2006";
    public static final String BAND_5_19 = "WHO 2007";

    public static final String BOY = "BOY";
    public static final String GIRL = "GIRL";
    public static final String HEIGHT = "HEIGHT";
    public static final String WEIGHT = "WEIGHT";
    public static final String BMI = "BMI";

    private GrowthStandardData() {
    }

    /** 单岁锚点：身高/体重/BMI 的 P3/P50/P97（男童 0-5 岁，WHO 2006 示意）。 */
    private static final double[][] BOY_0_5 = {
            // 岁  h3   h50  h97   w3   w50  w97   b3   b50  b97
            {0, 44.1, 49.9, 55.7, 2.1, 3.3, 4.8, 11.5, 13.4, 15.2},
            {1, 70.0, 75.7, 81.7, 7.7, 9.6, 12.2, 14.8, 17.1, 19.4},
            {2, 81.0, 87.1, 93.9, 9.8, 12.2, 15.6, 14.5, 16.2, 18.2},
            {3, 88.4, 95.1, 102.7, 11.5, 14.3, 18.3, 14.0, 15.6, 17.6},
            {4, 94.3, 101.6, 110.0, 12.8, 16.3, 21.0, 13.6, 15.2, 17.2},
            {5, 99.7, 107.7, 117.0, 14.2, 18.3, 23.8, 13.2, 14.9, 17.0},
    };

    /** 女童 0-5 岁（WHO 2006 示意）。 */
    private static final double[][] GIRL_0_5 = {
            {0, 43.6, 49.1, 54.9, 2.1, 3.2, 4.7, 11.3, 13.1, 14.9},
            {1, 68.4, 74.0, 79.9, 7.0, 8.9, 11.5, 14.3, 16.6, 18.8},
            {2, 79.1, 85.5, 92.9, 9.2, 11.5, 14.8, 14.1, 15.8, 17.8},
            {3, 86.5, 94.1, 102.1, 10.8, 13.7, 17.6, 13.6, 15.3, 17.3},
            {4, 92.5, 99.9, 108.6, 12.1, 15.6, 20.3, 13.2, 14.9, 16.9},
            {5, 98.0, 106.2, 115.6, 13.5, 17.4, 22.7, 12.9, 14.6, 16.6},
    };

    /** 男童 5-19 岁（WHO 2007 示意）。 */
    private static final double[][] BOY_5_19 = {
            {5, 102.0, 110.0, 118.0, 15.3, 18.8, 24.0, 13.6, 15.3, 18.6},
            {6, 108.5, 116.0, 124.5, 17.0, 21.0, 27.0, 13.3, 15.5, 19.5},
            {7, 114.0, 121.8, 131.0, 18.8, 23.4, 30.5, 13.2, 15.7, 20.6},
            {8, 119.5, 127.3, 137.0, 20.7, 26.0, 34.5, 13.3, 16.1, 21.8},
            {9, 124.8, 132.9, 143.0, 23.0, 29.0, 39.0, 13.4, 16.5, 23.0},
            {10, 129.9, 138.4, 149.0, 25.4, 32.4, 44.0, 13.6, 17.0, 24.4},
            {11, 134.9, 143.8, 155.0, 28.2, 36.0, 49.5, 13.8, 17.5, 25.7},
            {12, 140.0, 149.5, 161.5, 31.4, 40.2, 56.0, 14.1, 18.0, 27.0},
            {13, 146.0, 156.3, 169.0, 35.0, 45.2, 63.5, 14.5, 18.6, 28.4},
            {14, 152.5, 163.3, 176.0, 39.0, 50.6, 71.0, 14.9, 19.2, 29.6},
            {15, 158.5, 169.6, 182.0, 43.0, 55.7, 78.5, 15.3, 19.8, 30.7},
            {16, 162.8, 173.6, 186.0, 46.5, 60.0, 84.0, 15.7, 20.5, 31.5},
            {17, 165.3, 175.6, 188.0, 49.5, 63.0, 88.0, 16.1, 21.1, 32.1},
            {18, 166.8, 176.9, 189.0, 51.5, 65.0, 90.5, 16.4, 21.6, 32.6},
            {19, 167.7, 177.8, 190.0, 52.5, 66.3, 92.0, 16.6, 21.9, 33.0},
    };

    /** 女童 5-19 岁（WHO 2007 示意）。 */
    private static final double[][] GIRL_5_19 = {
            {5, 101.0, 109.4, 117.5, 14.9, 18.4, 24.0, 13.3, 15.2, 18.4},
            {6, 107.0, 115.0, 123.5, 16.5, 20.6, 27.0, 13.1, 15.3, 19.6},
            {7, 112.5, 120.6, 129.5, 18.2, 23.0, 30.5, 13.1, 15.5, 20.9},
            {8, 117.8, 126.4, 135.5, 20.0, 25.7, 34.5, 13.2, 15.8, 22.4},
            {9, 122.8, 131.8, 141.5, 22.2, 28.6, 39.0, 13.4, 16.2, 23.9},
            {10, 127.8, 137.2, 147.5, 24.5, 32.0, 44.5, 13.7, 16.6, 25.5},
            {11, 132.9, 143.0, 154.0, 27.2, 35.6, 50.5, 14.0, 17.1, 27.1},
            {12, 138.0, 148.8, 160.0, 30.2, 39.7, 57.0, 14.4, 17.7, 28.6},
            {13, 142.8, 154.2, 166.0, 33.5, 44.1, 63.5, 14.8, 18.3, 30.0},
            {14, 146.5, 158.2, 170.0, 36.8, 48.3, 69.5, 15.2, 18.8, 31.2},
            {15, 149.0, 160.8, 173.0, 39.5, 51.4, 74.0, 15.5, 19.3, 32.2},
            {16, 150.5, 162.4, 174.5, 41.5, 53.6, 77.0, 15.8, 19.7, 33.0},
            {17, 151.4, 163.4, 175.5, 43.0, 55.2, 79.0, 16.0, 20.0, 33.6},
            {18, 151.8, 163.8, 176.0, 44.0, 56.4, 80.5, 16.2, 20.2, 34.0},
            {19, 152.0, 164.0, 176.2, 44.6, 57.2, 81.5, 16.3, 20.4, 34.3},
    };

    private static double p15(double p3, double p50) {
        return p50 - (p50 - p3) * 0.6;
    }

    private static double p85(double p50, double p97) {
        return p50 + (p97 - p50) * 0.6;
    }

    private static double pick(double[][] table, int ageYears, int col) {
        for (double[] row : table) {
            if ((int) row[0] == ageYears) {
                return row[col];
            }
        }
        return Double.NaN;
    }

    private static String bandOf(int ageYears) {
        return ageYears <= 5 ? BAND_0_5 : BAND_5_19;
    }

    private static double[][] tableOf(String gender, int ageYears) {
        boolean boy = BOY.equals(gender);
        if (ageYears <= 5) {
            return boy ? BOY_0_5 : GIRL_0_5;
        }
        return boy ? BOY_5_19 : GIRL_5_19;
    }

    /**
     * 返回某年龄的指标锚点数组 [p3, p50, p97]；无数据返回 null。
     */
    private static double[] anchors(String gender, int ageYears, String metric) {
        double[][] table = tableOf(gender, ageYears);
        int base;
        switch (metric) {
            case HEIGHT -> base = 1;
            case WEIGHT -> base = 4;
            case BMI -> base = 7;
            default -> {
                return null;
            }
        }
        double p3 = pick(table, ageYears, base);
        if (Double.isNaN(p3)) {
            return null;
        }
        double p50 = pick(table, ageYears, base + 1);
        double p97 = pick(table, ageYears, base + 2);
        return new double[]{p3, p50, p97};
    }

    /** 标准分位行（P3/P15/P50/P85/P97）；未覆盖的年龄/指标返回 null。 */
    public static GrowthStandardResp.GrowthStandardRow row(String gender, int ageYears, String metric) {
        double[] a = anchors(gender, ageYears, metric);
        if (a == null) {
            return null;
        }
        GrowthStandardResp.GrowthStandardRow r = new GrowthStandardResp.GrowthStandardRow();
        r.setAgeYears(ageYears);
        r.setP3(round1(a[0]));
        r.setP50(round1(a[1]));
        r.setP97(round1(a[2]));
        r.setP15(round1(p15(a[0], a[1])));
        r.setP85(round1(p85(a[1], a[2])));
        return r;
    }

    /** 分位区间名：数值落在哪个分位带。 */
    public static String bandOf(String gender, int ageYears, String metric, double value) {
        double[] a = anchors(gender, ageYears, metric);
        if (a == null || Double.isNaN(value)) {
            return null;
        }
        double p15 = p15(a[0], a[1]);
        double p50 = a[1];
        double p85 = p85(a[1], a[2]);
        if (value < p15) {
            return "<P15";
        }
        if (value < p50) {
            return "P15–P50";
        }
        if (value < p85) {
            return "P50–P85";
        }
        return "≥P85";
    }

    /** BMI 类别：消瘦 / 正常 / 超重 / 肥胖；无阈值数据返回"暂无阈值"。 */
    public static String bmiCategory(String gender, int ageYears, double bmi) {
        double[] a = anchors(gender, ageYears, BMI);
        if (a == null || Double.isNaN(bmi)) {
            return "暂无阈值";
        }
        double p85 = p85(a[1], a[2]);
        if (bmi < a[0]) {
            return "消瘦";
        }
        if (bmi <= p85) {
            return "正常";
        }
        if (bmi <= a[2]) {
            return "超重";
        }
        return "肥胖";
    }

    /** 某性别在指定年龄段的全部岁（升序）。 */
    public static List<Integer> ages(String gender, String ageBand) {
        double[][] table = BAND_0_5.equals(ageBand)
                ? (BOY.equals(gender) ? BOY_0_5 : GIRL_0_5)
                : (BOY.equals(gender) ? BOY_5_19 : GIRL_5_19);
        List<Integer> ages = new ArrayList<>();
        for (double[] row : table) {
            ages.add((int) row[0]);
        }
        return ages;
    }

    /** 年龄段标签校验。 */
    public static boolean validBand(String ageBand) {
        return BAND_0_5.equals(ageBand) || BAND_5_19.equals(ageBand);
    }

    public static boolean validGender(String gender) {
        return BOY.equals(gender) || GIRL.equals(gender);
    }

    public static boolean validMetric(String metric) {
        return HEIGHT.equals(metric) || WEIGHT.equals(metric) || BMI.equals(metric);
    }

    /** 指标单位。 */
    public static String unitOf(String metric) {
        return switch (metric) {
            case HEIGHT -> "cm";
            case WEIGHT -> "kg";
            case BMI -> "kg/m²";
            default -> "";
        };
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
