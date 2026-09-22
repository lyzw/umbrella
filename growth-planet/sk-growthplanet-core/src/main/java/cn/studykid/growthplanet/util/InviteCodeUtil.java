package cn.studykid.growthplanet.util;

import java.security.SecureRandom;

/**
 * 邀请码工具：生成 6 位大写字母+数字邀请码（[A-Z0-9]{6}）。
 * 唯一性由调用方（Service）结合数据库重试保证。
 */
public final class InviteCodeUtil {

    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private InviteCodeUtil() {
    }

    /** 生成一个符合格式的随机邀请码。 */
    public static String randomCode() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    /** 校验格式是否合法（6 位大写字母或数字）。 */
    public static boolean isValidFormat(String code) {
        return code != null && code.matches("[A-Z0-9]{6}");
    }
}
