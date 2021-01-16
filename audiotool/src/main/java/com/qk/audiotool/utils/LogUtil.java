package com.qk.audiotool.utils;

import android.util.Log;

public class LogUtil {

    /**
     * Log基本方法
     *
     * @param level
     * @param tag
     * @param s
     */
    private synchronized static void log(int level, String tag, String s) {

    }
    public static void i(String tag, String s) {
        log(Log.INFO, tag, s);
    }
    public static void e(String tag, String s) {
        log(Log.INFO, tag, s);
    }
    public static void w(String tag, String s) {
        log(Log.INFO, tag, s);
    }
    public static void v(String tag, String s) {
        log(Log.INFO, tag, s);
    }
}
