package util;

import java.security.SecureRandom;

/**
 * BCrypt password hashing implementation.
 *
 * Based on the standard OpenBSD Blowfish password hashing algorithm by Niels Provos
 * and David Mazieres, ported to Java by Damien Miller.
 *
 * Provides:
 *   - BCrypt.hashpw(password, BCrypt.gensalt(log_rounds))
 *   - BCrypt.checkpw(password, candidate_hash)
 */
public class BCrypt {

    private static final int BCRYPT_SALT_LEN = 16;
    private static final int BLOWFISH_NUM_ROUNDS = 16;

    // P-box and S-boxes initialized from standard Blowfish constants
    private static final int[] P_orig = {
        0x243f6a88, 0x85a308d3, 0x13198a2e, 0x03707344,
        0xa4093822, 0x299f31d0, 0x082efa98, 0xec4e6c89,
        0x452821e6, 0x38d01377, 0xbe5466cf, 0x34e90c6c,
        0xc0ac29b7, 0xc97c50dd, 0x3f84d5b5, 0xb5470917,
        0x9216d5d9, 0x8979fb1b
    };

    private static final int[] S_orig = {
        0xd1310ba6, 0x98dfb5ac, 0x2ffd72db, 0xd01adfb7,
        0xb8e1afed, 0x6a267e96, 0xba7c9045, 0xf12c7f99,
        0x24a19947, 0xb3916cf7, 0x0801f2e2, 0x858efc16,
        0x636920d8, 0x71574e69, 0xa458fea3, 0xf4933d7e,
        0x0d95748f, 0x728eb658, 0x718bcd58, 0x82154aee,
        0x7b54a41d, 0xc25a59b5, 0x9c30d539, 0x2af26013,
        0xc5d1b023, 0x286085f0, 0xca417918, 0xb8db38ef,
        0x8e79dcb0, 0x603a180e, 0x6c9e0e8b, 0xb01e8a3e,
        0xd71577c1, 0xbd314b27, 0x78af2fda, 0x55605c60,
        0xe65525f3, 0xaa55ab94, 0x57489862, 0x63e81440,
        0x55ca396a, 0x2aab10b6, 0xb4cc5c34, 0x1141e8ce,
        0xa15486af, 0x7c72e993, 0xb3ee1411, 0x636fbc2a,
        0x2ba9c55d, 0x741831f6, 0xce5c3e16, 0x9b87931e,
        0xafd6ba33, 0x6c24cf5c, 0x7a325381, 0x28958677,
        0x3b8f4898, 0x6b4bb9af, 0xc4bfe81b, 0x66282193,
        0x61d809cc, 0xfb21a991, 0x487cac60, 0x5dec8032,
        0xef845d5d, 0xe98575b1, 0xdc262302, 0xeb651b88,
        0x23893e81, 0xd396acc5, 0x0f6d6ff3, 0x83f44239,
        0x2e0b4482, 0xa4842004, 0x69c8f04a, 0x9e1f9b5e,
        0x21c66842, 0xf6e96c9a, 0x670c9c61, 0xabd388f0,
        0x6a51a0d2, 0xd8542f68, 0x960fa728, 0xab5133a3,
        0x6eef0b6c, 0x137a3be4, 0xba3bf050, 0x7efb2bbe,
        0x9b1143a6, 0xb1053a53, 0xa6472dd7, 0x20349880,
        0xecd6f2fe, 0x6f6636ac, 0x92f9e414, 0x904a8b79,
        0x45402c42, 0x25a396e9, 0xb4e88b64, 0x8c7921a2,
        0x19213824, 0x3d001614, 0x4031649c, 0xc610c144,
        0xd1593c66, 0x90b9687e, 0x7de68046, 0x2e06c374,
        0x49f57ebf, 0xcb997cb2, 0x4a7e94e5, 0x5101a0db,
        0x03038d15, 0x51f335b1, 0x37e24687, 0xef26da68,
        0x78536f9a, 0x6a0c0e9e, 0x187a77e4, 0x56a64010,
        0x74c93544, 0xa3741544, 0xcd69352e, 0x05b22b7d,
        0x298495a8, 0x6e06b384, 0x7e727e02, 0xedd659f7,
        0x76239f60, 0x9a367469, 0x7f23a85b, 0x6332ec90,
        0x629555c2, 0xc7c5222f, 0x0f8f8b1c, 0x89b6b772,
        0x83df757b, 0x5e839e55, 0x696b7978, 0x3b3f46f3,
        0xc4e58a74, 0xc75a1336, 0x89078f4a, 0xc76a5924,
        0x9f5a7065, 0xd0309e1e, 0x96041697, 0x9c3d4a25,
        0x320f2618, 0x93309a47, 0x573f1505, 0x550d2426,
        0x0a1099ec, 0x5535c8e3, 0x8be5e381, 0x9482f347,
        0x7a303649, 0x599059f3, 0x9a58b292, 0x64e72cb9,
        0x7269e802, 0x485f269a, 0x591e84aa, 0xf6c805cd,
        0xefb783f9, 0x6cf2de7d, 0x648356bb, 0x34d58814,
        0x7cb5c8a7, 0x47dcfa52, 0xb68b209e, 0x6abb63f1,
        0xd1d293d0, 0x49896dc4, 0xb801df93, 0x2213e4b0,
        0x91d37446, 0x7a32b216, 0x4b786cbe, 0x77b819f7,
        0x815f91d8, 0x2214470c, 0xb9e4871e, 0xc32a8439,
        0xd72b7a94, 0x25a34e56, 0x205ea122, 0x11162464,
        0x45fe155a, 0xb07d579d, 0xddae4ec6, 0x66f6cecc,
        0xf2dd6a6a, 0x43b8c3ac, 0x5109b85c, 0xb734dbfa,
        0x2098670b, 0x58c0c46f, 0x217ef28d, 0x14041b31,
        0xd6687071, 0x54668b59, 0xd0011400, 0x600f7457,
        0xa7f45778, 0x1b4efb4d, 0xd47eb86d, 0x4102657e,
        0x815e98f7, 0xb500d02b, 0x6a1ef8a4, 0x05b5aa8b,
        0x9eef1108, 0x177435f3, 0xb06c9e05, 0x2e8f1707,
        0x8e5f2991, 0x584f18db, 0xdd1fb7aa, 0x86720d20,
        0x78f24419, 0x464243b8, 0x933086eb, 0x27b87612,
        0x56a6ecaa, 0x76b668fa, 0x47f3ca7d, 0x3d3170a4,
        0x41b83d8e, 0x19277dcf, 0x64e1c01e, 0x9a8ccf84,
        0xb7c8d9df, 0x5d471df4, 0x6d1136b6, 0xd035cf57,
        0x521e1494, 0x1a8775f0, 0x42562479, 0x2289657b,
        0xdc2e0b57, 0x7b66df42, 0xdcf4bcf2, 0x8684784a,
        0x29ef9545, 0x91834273, 0x904a4413, 0xba1ffcd8,
        0xd5e3b522, 0xd8090558, 0x718a7a22, 0x9e31d476,
        0x6e9da89a, 0x729b8214, 0x1b191547, 0xb0696982,
        0x94084f7b, 0x4858b459, 0xbcf12036, 0x044670cb
    };

    private static final char[] base64_code = {
        '.', '/', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J',
        'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V',
        'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h',
        'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't',
        'u', 'v', 'w', 'x', 'y', 'z', '0', '1', '2', '3', '4', '5',
        '6', '7', '8', '9'
    };

    private static final byte[] index_64 = {
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0, 1,
        54, 55, 56, 57, 58, 59, 60, 61, 62, 63, -1, -1, -1, -1, -1, -1,
        -1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
        17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, -1, -1, -1, -1, -1,
        -1, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42,
        43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, -1, -1, -1, -1, -1
    };

    private static final int[] bf_crypt_ciphertext = {
        0x4f727068, 0x65616e42, 0x65686f6c,
        0x64657253, 0x63727970, 0x74486173
    };

    private int[] P;
    private int[] S;

    private static void encode_base64(byte[] d, int len, StringBuilder rs) {
        int off = 0;
        while (off < len) {
            int c1 = d[off++] & 0xff;
            rs.append(base64_code[(c1 >> 2) & 0x3f]);
            int c2 = 0;
            if (off < len) {
                c2 = d[off++] & 0xff;
                rs.append(base64_code[((c1 & 0x03) << 4) | ((c2 >> 4) & 0x0f)]);
            } else {
                rs.append(base64_code[(c1 & 0x03) << 4]);
                break;
            }
            if (off < len) {
                int c3 = d[off++] & 0xff;
                rs.append(base64_code[((c2 & 0x0f) << 2) | ((c3 >> 6) & 0x03)]);
                rs.append(base64_code[c3 & 0x3f]);
            } else {
                rs.append(base64_code[(c2 & 0x0f) << 2]);
                break;
            }
        }
    }

    private static byte char64(char x) {
        if (x > 127) return -1;
        return index_64[x];
    }

    private static byte[] decode_base64(String s, int maxolen) {
        StringBuilder rs = new StringBuilder();
        int off = 0, slen = s.length(), olen = 0;
        byte[] ret = new byte[maxolen];
        while (off < slen - 1 && olen < maxolen) {
            byte c1 = char64(s.charAt(off++));
            byte c2 = char64(s.charAt(off++));
            if (c1 == -1 || c2 == -1) break;
            ret[olen++] = (byte) ((c1 << 2) | ((c2 & 0x30) >> 4));
            if (off >= slen || olen >= maxolen) break;
            byte c3 = char64(s.charAt(off++));
            if (c3 == -1) break;
            ret[olen++] = (byte) (((c2 & 0x0f) << 4) | ((c3 & 0x3c) >> 2));
            if (off >= slen || olen >= maxolen) break;
            byte c4 = char64(s.charAt(off++));
            ret[olen++] = (byte) (((c3 & 0x03) << 6) | c4);
        }
        return ret;
    }

    private void encipher(int[] lr, int off) {
        int l = lr[off];
        int r = lr[off + 1];
        l ^= P[0];
        for (int i = 0; i <= 14; i += 2) {
            r ^= ((S[(l >>> 24)] + S[0x100 | ((l >>> 16) & 0xff)]) ^ S[0x200 | ((l >>> 8) & 0xff)]) + S[0x300 | (l & 0xff)] ^ P[i + 1];
            l ^= ((S[(r >>> 24)] + S[0x100 | ((r >>> 16) & 0xff)]) ^ S[0x200 | ((r >>> 8) & 0xff)]) + S[0x300 | (r & 0xff)] ^ P[i + 2];
        }
        lr[off] = r ^ P[17];
        lr[off + 1] = l;
    }

    private static int streamtoword(byte[] data, int[] offp) {
        int i;
        int word = 0;
        int off = offp[0];
        for (i = 0; i < 4; i++) {
            word = (word << 8) | (data[off] & 0xff);
            off = (off + 1) % data.length;
        }
        offp[0] = off;
        return word;
    }

    private void init_key() {
        P = (int[]) P_orig.clone();
        S = (int[]) S_orig.clone();
    }

    private void ekskey(byte[] data, byte[] key) {
        int i;
        int[] offp = {0};
        int[] lr = {0, 0};
        int plen = P.length, slen = S.length;

        for (i = 0; i < plen; i++)
            P[i] = P[i] ^ streamtoword(key, offp);

        offp[0] = 0;
        for (i = 0; i < plen; i += 2) {
            lr[0] ^= streamtoword(data, offp);
            lr[1] ^= streamtoword(data, offp);
            encipher(lr, 0);
            P[i] = lr[0];
            P[i + 1] = lr[1];
        }

        for (i = 0; i < slen; i += 2) {
            lr[0] ^= streamtoword(data, offp);
            lr[1] ^= streamtoword(data, offp);
            encipher(lr, 0);
            S[i] = lr[0];
            S[i + 1] = lr[1];
        }
    }

    private byte[] crypt_raw(byte[] password, byte[] salt, int log_rounds) {
        int rounds = 1 << log_rounds;
        init_key();
        ekskey(salt, password);
        for (int i = 0; i < rounds; i++) {
            ekskey(password, salt);
            ekskey(salt, password);
        }

        int[] cdata = (int[]) bf_crypt_ciphertext.clone();
        int clen = cdata.length;
        for (int i = 0; i < 64; i++) {
            for (int j = 0; j < clen; j += 2)
                encipher(cdata, j);
        }

        byte[] ret = new byte[clen * 4];
        for (int i = 0, j = 0; i < clen; i++) {
            ret[j++] = (byte) ((cdata[i] >> 24) & 0xff);
            ret[j++] = (byte) ((cdata[i] >> 16) & 0xff);
            ret[j++] = (byte) ((cdata[i] >> 8) & 0xff);
            ret[j++] = (byte) (cdata[i] & 0xff);
        }
        return ret;
    }

    public static String hashpw(String password, String salt) {
        if (password == null || salt == null) {
            throw new IllegalArgumentException("Password and salt must not be null");
        }
        if (salt.length() < 28) {
            throw new IllegalArgumentException("Invalid salt length");
        }

        int off = 0;
        if (salt.charAt(0) != '$' || salt.charAt(1) != '2') {
            throw new IllegalArgumentException("Invalid salt revision");
        }
        if (salt.charAt(2) == '$') {
            off = 3;
        } else {
            off = 4;
        }

        if (salt.charAt(off + 2) > '$') {
            throw new IllegalArgumentException("Missing salt rounds delimiter");
        }

        int log_rounds = Integer.parseInt(salt.substring(off, off + 2));
        if (log_rounds < 4 || log_rounds > 31) {
            throw new IllegalArgumentException("Invalid log_rounds: " + log_rounds);
        }

        int real_salt_start = off + 3;
        String real_salt = salt.substring(real_salt_start, real_salt_start + 22);
        byte[] salt_bytes = decode_base64(real_salt, BCRYPT_SALT_LEN);

        byte[] pwbytes;
        try {
            byte[] raw = password.getBytes("UTF-8");
            pwbytes = new byte[raw.length + 1];
            System.arraycopy(raw, 0, pwbytes, 0, raw.length);
            pwbytes[raw.length] = 0;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        BCrypt b = new BCrypt();
        byte[] hashed = b.crypt_raw(pwbytes, salt_bytes, log_rounds);

        StringBuilder res = new StringBuilder();
        res.append("$2a$");
        if (log_rounds < 10) res.append("0");
        res.append(log_rounds);
        res.append("$");
        encode_base64(salt_bytes, salt_bytes.length, res);
        encode_base64(hashed, bf_crypt_ciphertext.length * 4 - 1, res);
        return res.toString();
    }

    public static String gensalt(int log_rounds) {
        if (log_rounds < 4 || log_rounds > 31) {
            throw new IllegalArgumentException("Log rounds must be between 4 and 31");
        }
        SecureRandom random = new SecureRandom();
        byte[] rnd = new byte[BCRYPT_SALT_LEN];
        random.nextBytes(rnd);

        StringBuilder rs = new StringBuilder();
        rs.append("$2a$");
        if (log_rounds < 10) rs.append("0");
        rs.append(log_rounds);
        rs.append("$");
        encode_base64(rnd, rnd.length, rs);
        return rs.toString();
    }

    public static String gensalt() {
        return gensalt(12);
    }

    public static boolean checkpw(String plaintext, String hashed) {
        if (plaintext == null || hashed == null) {
            return false;
        }
        try {
            return equalsConstantTime(hashed, hashpw(plaintext, hashed));
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean equalsConstantTime(String a, String b) {
        if (a == null || b == null) return false;
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
