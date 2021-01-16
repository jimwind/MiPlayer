package com.qk.audiotool.utils.encrypt;

import android.text.TextUtils;

import com.qk.audiotool.utils.LogUtil;

import java.security.Key;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;
import javax.crypto.spec.IvParameterSpec;

/**
 * Des+Base64加解密工具
 *
 * @author yl
 */
public class Des {

    private static final String TAG = Des.class.getSimpleName();

    private static final String ALGORITHM_DES = "DES/CBC/PKCS5Padding";
    private static final String base = "qk@_"; // 密钥和向量的前缀部分
    public static String key = ""; // 密钥(长度不能够小于8位字节)
    public static String iv = ""; // 向量

    public static String getKey() {
        return key;
    }

    public static void setKey(String key) {
        Des.key = base + key;
//        LogUtil.i(TAG, "setKey:" + Des.key);
    }

    public static String getIv() {
        return iv;
    }

    public static void setIv(String iv) {
        Des.iv = base + iv;
//        LogUtil.i(TAG, "setIv:" + Des.iv);
    }

    /**
     * DES算法，加密
     *
     * @param data 待解密字符串
     * @param key  密钥
     * @param iv   向量
     * @return
     */
    public static String encode(String data, String key, String iv) {
        if (!TextUtils.isEmpty(data)) {
//            LogUtil.i(TAG, "key:" + key + " iv:" + iv);
//            LogUtil.i(TAG, "data:" + data);
            try {
                DESKeySpec dks = new DESKeySpec(key.getBytes());
                SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("DES");
                // key的长度不能够小于8位字节
                Key secretKey = keyFactory.generateSecret(dks);
                Cipher cipher = Cipher.getInstance(ALGORITHM_DES);
                AlgorithmParameterSpec paramSpec = new IvParameterSpec(iv.getBytes());
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, paramSpec);
                byte[] bytes = cipher.doFinal(data.getBytes());
                String result = Base64.encode(bytes);
//                LogUtil.i(TAG, "result:" + result);
                return result;
            } catch (Exception e) {
                LogUtil.e(TAG, "encode e:" + e.toString());
            }
        }
        return "";
    }

    /**
     * DES算法，加密
     *
     * @param data 待加密字符串
     */
    public static String encode(String data) {
        return encode(data, key, iv);
    }

    /**
     * DES算法，解密
     *
     * @param data 待解密字符串
     * @param key  密钥
     * @param iv   向量
     * @return
     */
    public static String decode(String data, String key, String iv) {
//        LogUtil.e(TAG, "decode: key " + key + " iv " + iv);
//        LogUtil.i(TAG, "data:" + data);
        if (!TextUtils.isEmpty(data)) {
            try {
                byte[] result = Base64.decode(data);
                DESKeySpec dks = new DESKeySpec(key.getBytes());
                SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("DES");
                // key的长度不能够小于8位字节
                Key secretKey = keyFactory.generateSecret(dks);
                Cipher cipher = Cipher.getInstance(ALGORITHM_DES);
                AlgorithmParameterSpec paramSpec = new IvParameterSpec(iv.getBytes());
                cipher.init(Cipher.DECRYPT_MODE, secretKey, paramSpec);
                result = cipher.doFinal(result);
//                LogUtil.i(TAG, "result:" + new String(result));
                return new String(result);
            } catch (Exception e) {
                LogUtil.e(TAG, "decode e:" + e.toString());
            }
        }
        return "";
    }

    /**
     * DES算法，解密
     *
     * @param data 待解密字符串
     */
    public static String decode(String data) {
        return decode(data, key, iv);
    }

}
