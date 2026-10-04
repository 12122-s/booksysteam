package com.library.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 密码哈希兼容性测试。
 *
 * <p>下面每个哈希都是用 Python 的 werkzeug.security.generate_password_hash
 * 预先生成的（明文见测试名），用来保证 Java 版能校验 Python 版写进数据库的老密码 ——
 * 这是从 Python 版平滑升级到 Java 版的关键。
 */
class PasswordUtilTest {

    // 明文 123456，werkzeug 3 默认（scrypt）
    private static final String SCRYPT_123456 =
            "scrypt:32768:8:1$mzQmYC7nYSb8FwAJ$293652bb4b6922b015cb983c77f159a"
                    + "657560d116d2587973b40720aea94051b42ca5e1ed8a7fbebee3440cc6fb493c1c9db0c1107b8"
                    + "4fe06e25408321ea9262";

    // 明文 123456，pbkdf2:sha256:600000
    private static final String PBKDF2_600K_123456 =
            "pbkdf2:sha256:600000$1Z1iTXUAQv1ci2D9$82651a32e2db3bb8566b53e132c06a04a2cd2422"
                    + "7127cdce385bc715afeeda9f";

    // 明文 123456，老 werkzeug 默认 pbkdf2:sha256:260000
    private static final String PBKDF2_260K_123456 =
            "pbkdf2:sha256:260000$cC47mpD0hnP1CXrf$ce9a1db2f413631fa8a382471e64daf456accc4956c6"
                    + "784034bcfd66c7ed5759";

    // 明文 abc12345，werkzeug 3 默认（scrypt）
    private static final String SCRYPT_ABC =
            "scrypt:32768:8:1$GSvC9xboYg06brkX$9ea5089f516b9e7a270c83be53f57bef21507a0953c2cb"
                    + "a3fff5e359685d9fdb340f67f8e8b94daa22f932351254460c18583d193443f9235f7aec8184feb47d";

    // 明文 abc12345，pbkdf2:sha256:600000
    private static final String PBKDF2_600K_ABC =
            "pbkdf2:sha256:600000$Bbhjxeu5jb3Bbt0L$5fe9d8e6862ca902b5d05b333331e00eb80469ad4936"
                    + "9bc0375036887d97625a";

    @Test
    void javaHashIsSelfConsistent() {
        String hashed = PasswordUtil.hash("s3cret-密码");
        assertTrue(hashed.startsWith("pbkdf2:sha256:"));
        assertTrue(PasswordUtil.check(hashed, "s3cret-密码"));
        assertFalse(PasswordUtil.check(hashed, "s3cret-密馬"));
        assertFalse(PasswordUtil.check(hashed, "wrong"));
    }

    @Test
    void checksWerkzeugScryptHash() {
        assertTrue(PasswordUtil.check(SCRYPT_123456, "123456"));
        assertFalse(PasswordUtil.check(SCRYPT_123456, "123457"));
        assertTrue(PasswordUtil.check(SCRYPT_ABC, "abc12345"));
        assertFalse(PasswordUtil.check(SCRYPT_ABC, "ABC12345"));
    }

    @Test
    void checksWerkzeugPbkdf2Hash() {
        assertTrue(PasswordUtil.check(PBKDF2_600K_123456, "123456"));
        assertTrue(PasswordUtil.check(PBKDF2_260K_123456, "123456"));
        assertTrue(PasswordUtil.check(PBKDF2_600K_ABC, "abc12345"));
        assertFalse(PasswordUtil.check(PBKDF2_600K_ABC, "abc12344"));
    }

    @Test
    void rejectsMalformedHashes() {
        assertFalse(PasswordUtil.check(null, "x"));
        assertFalse(PasswordUtil.check("", "x"));
        assertFalse(PasswordUtil.check("not-a-hash", "x"));
        assertFalse(PasswordUtil.check("scrypt:32768:8:1$salt", "x"));
        assertFalse(PasswordUtil.check("unknown:sha256$salt$abcdef", "x"));
        assertFalse(PasswordUtil.check(SCRYPT_123456, null));
    }
}
