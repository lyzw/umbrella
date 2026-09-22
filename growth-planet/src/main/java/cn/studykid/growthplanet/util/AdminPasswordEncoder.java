package cn.studykid.growthplanet.util;

import org.springframework.stereotype.Component;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 后台账号密码哈希：JDK 内置 PBKDF2WithHmacSHA256（无需引入 spring-security）。
 * 存储：salt(Base64) + hash(Base64)。修改密码/重置时重新生成 salt。
 */
@Component
public class AdminPasswordEncoder {

    private static final int ITERATIONS = 65_536;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_LENGTH = 16;

    public String genSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    public String encode(String rawPassword, String salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(
                    rawPassword.toCharArray(),
                    Base64.getDecoder().decode(salt),
                    ITERATIONS, KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("密码哈希失败", e);
        }
    }

    public boolean matches(String rawPassword, String salt, String storedHash) {
        return storedHash != null && encode(rawPassword, salt).equals(storedHash);
    }
}
