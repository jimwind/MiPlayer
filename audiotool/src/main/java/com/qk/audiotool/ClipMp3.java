package com.qk.audiotool;

import android.annotation.TargetApi;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;

import com.qk.audiotool.utils.LogUtil;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ClipMp3 {
    //适当的调整SAMPLE_SIZE可以更加精确的裁剪音乐
    private static final int SAMPLE_SIZE = 8820000;

    /**
     * @param inputPath
     * @param outputPath
     * @param start      millisecond
     * @param end        millisecond
     * @return
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public static boolean clip(String inputPath, String outputPath, int start, int end) {
        boolean ret = true;
        LogUtil.i("jimwind", "clip input  " + inputPath);
        LogUtil.i("jimwind", "clip output " + outputPath);
        LogUtil.i("jimwind", "clip start end " + start + "->" + end);
        MediaExtractor extractor = null;
        BufferedOutputStream outputStream = null;
        try {
            extractor = new MediaExtractor();
            extractor.setDataSource(inputPath);
            int track = getAudioTrack(extractor);
            if (track < 0) {
                return false;
            }
            //选择音频轨道
            extractor.selectTrack(track);
            outputStream = new BufferedOutputStream(new FileOutputStream(outputPath));
            start = start * 1000;
            end = end * 1000;
            //跳至开始裁剪位置, 注意是微秒
            extractor.seekTo(start, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            while (true) {
                ByteBuffer buffer = ByteBuffer.allocate(SAMPLE_SIZE);
                int sampleSize = extractor.readSampleData(buffer, 0);
                long timeStamp = extractor.getSampleTime();

                if (timeStamp > end) {
                    break;
                }
                if (sampleSize <= 0) {
                    break;
                }
                byte[] buf = new byte[sampleSize];
                buffer.get(buf, 0, sampleSize);
                //写入文件
                outputStream.write(buf);
                //音轨数据往前读
                extractor.advance();
            }

        } catch (IOException e) {
            LogUtil.e("jimwind", "clip mp3 exception " + e.toString());
            ret = false;
            e.printStackTrace();
        } finally {
            if (extractor != null) {
                extractor.release();
            }
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    ret = false;
                }
            }
        }
        return ret;
    }

    /**
     * 获取音频数据轨道
     *
     * @param extractor
     * @return
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private static int getAudioTrack(MediaExtractor extractor) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("audio")) {
                return i;
            }
        }
        return -1;
    }
}
