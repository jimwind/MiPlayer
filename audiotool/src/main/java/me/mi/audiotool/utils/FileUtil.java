package me.mi.audiotool.utils;

import android.os.Environment;
import android.os.StatFs;
import android.text.TextUtils;

import me.mi.audiotool.utils.encrypt.MD5;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 文件操作工具
 */
public class FileUtil {

    private static final String TAG = FileUtil.class.getSimpleName();

    /**
     * 检查某文件是否存在
     *
     * @return
     */
    public static boolean checkFileExist(String filePath) {
        try {
            File f = new File(filePath);
            if (f.exists()) {
                return true;
            }
        } catch (Exception e) {
        }
        return false;
    }

    /**
     * 检查某文件是否存在
     *
     * @param dirPath
     * @param fileName
     * @return
     */
    public static boolean checkFileExist(String dirPath, String fileName) {
        String path = dirPath + "/" + fileName;
        return checkFileExist(path);
    }

    /**
     * 获取某目录下的剩余空间
     *
     * @return
     */
    public static long getDirRemainSize(File dir) {
        StatFs stat = new StatFs(dir.getPath());
        return (long) stat.getAvailableBlocks() * stat.getBlockSize();
    }

    /**
     * 获取某目录下的总空间
     *
     * @return
     */
    public static long getDirTotalSize(File dir) {
        StatFs stat = new StatFs(dir.getPath());
        return (long) stat.getBlockCount() * stat.getBlockSize();
    }



    /**
     * Create a directory if it doesn't exist, otherwise do nothing.
     *
     * @param dirPath The path of directory.
     * @return {@code true}: exists or creates successfully<br>{@code false}: otherwise
     */
    public static boolean createOrExistsDir(final String dirPath) {
        return createOrExistsDir(getFileByPath(dirPath));
    }

    public static boolean createOrExistsDir(final File file) {
        return file != null && (file.exists() ? file.isDirectory() : file.mkdirs());
    }
    public static File getFileByPath(final String filePath) {
        return isSpace(filePath) ? null : new File(filePath);
    }

    private static boolean isSpace(final String s) {
        if (s == null) {
            return true;
        }
        for (int i = 0, len = s.length(); i < len; ++i) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }
    /**
     * 根据当前的时间生成相应的文件名
     * 实例 dir/record_20160101_13_15_12.suffix
     */
    public static String getRecordFilePath(String dirTag, String suffix) {
        String fileDir = getRecordTempDir(MD5.md5(dirTag));
        if (!createOrExistsDir(fileDir)) {

        }
        String fileName = String.format(Locale.getDefault(), "record_%s",
                getNowString(new SimpleDateFormat("yyyyMMdd_HH_mm_ss_SSS", Locale.SIMPLIFIED_CHINESE)));
        return String.format(Locale.getDefault(), "%s%s."+suffix, fileDir, fileName);
    }

    /**
     *
     * @param dirTag 识别不同的用户
     * @return
     */
    public static String getRecordTempDir(String dirTag){
        String fileDir = String.format(Locale.getDefault(), "%s/Record/temp/"+dirTag+"/", Environment.getExternalStorageDirectory().getAbsolutePath());
        if (!createOrExistsDir(fileDir)) {

        }
        return fileDir;
    }
    public static String getNowString(final java.text.DateFormat format) {
        return millis2String(System.currentTimeMillis(), format);
    }
    /**
     * Milliseconds to the formatted time string.
     *
     * @param millis The milliseconds.
     * @param format The format.
     * @return the formatted time string
     */
    public static String millis2String(final long millis, final java.text.DateFormat format) {
        return format.format(new Date(millis));
    }
    public static void delFile(String filePath) {
        if(TextUtils.isEmpty(filePath)){
            return;
        }
        File file = new File(filePath);
        if (file.isFile()) {
            if (file.exists()) {
                file.delete();
            }
        }
    }
}
