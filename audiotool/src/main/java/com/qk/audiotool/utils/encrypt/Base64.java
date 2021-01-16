package com.qk.audiotool.utils.encrypt;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Base64(ksoap2-android-assembly-2.5.4-jar-with-dependencies.jar中的源码)
 *
 */
public class Base64 {

    private static final char[] charTab = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

    public Base64() {
    }

    public static String encode(byte[] data) {
        return encode(data, 0, data.length, null).toString();
    }

    public static StringBuffer encode(byte[] data, int start, int len, StringBuffer buf) {
        if(buf == null) {
            buf = new StringBuffer(data.length * 3 / 2);
        }

        int end = len - 3;
        int i = start;
        int n = 0;

        int d;
        while(i <= end) {
            d = (data[i] & 255) << 16 | (data[i + 1] & 255) << 8 | data[i + 2] & 255;
            buf.append(charTab[d >> 18 & 63]);
            buf.append(charTab[d >> 12 & 63]);
            buf.append(charTab[d >> 6 & 63]);
            buf.append(charTab[d & 63]);
            i += 3;
            if(n++ >= 14) {
                n = 0;
//                buf.append("\r\n");
            }
        }

        if(i == start + len - 2) {
            d = (data[i] & 255) << 16 | (data[i + 1] & 255) << 8;
            buf.append(charTab[d >> 18 & 63]);
            buf.append(charTab[d >> 12 & 63]);
            buf.append(charTab[d >> 6 & 63]);
            buf.append("=");
        } else if(i == start + len - 1) {
            d = (data[i] & 255) << 16;
            buf.append(charTab[d >> 18 & 63]);
            buf.append(charTab[d >> 12 & 63]);
            buf.append("==");
        }

        return buf;
    }

    static int decode(char c) {
        if(c >= 65 && c <= 90) {
            return c - 65;
        } else if(c >= 97 && c <= 122) {
            return c - 97 + 26;
        } else if(c >= 48 && c <= 57) {
            return c - 48 + 26 + 26;
        } else {
            switch(c) {
                case '+':
                    return 62;
                case '/':
                    return 63;
                case '=':
                    return 0;
                default:
                    throw new RuntimeException("unexpected code: " + c);
            }
        }
    }

    public static byte[] decode(String s) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        try {
            decode(s, bos);
        } catch (IOException var3) {
            throw new RuntimeException();
        }

        return bos.toByteArray();
    }

    public static void decode(String s, OutputStream os) throws IOException {
        int i = 0;
        int len = s.length();

        while(true) {
            while(i < len && s.charAt(i) <= 32) {
                ++i;
            }

            if(i == len) {
                break;
            }

            int tri = (decode(s.charAt(i)) << 18) + (decode(s.charAt(i + 1)) << 12) + (decode(s.charAt(i + 2)) << 6) + decode(s.charAt(i + 3));
            os.write(tri >> 16 & 255);
            if(s.charAt(i + 2) == 61) {
                break;
            }

            os.write(tri >> 8 & 255);
            if(s.charAt(i + 3) == 61) {
                break;
            }

            os.write(tri & 255);
            i += 4;
        }

    }
}
