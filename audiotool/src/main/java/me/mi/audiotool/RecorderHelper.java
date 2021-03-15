package me.mi.audiotool;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.os.Process;
import android.text.TextUtils;

import me.mi.audiotool.utils.FileUtil;
import me.mi.audiotool.utils.LogUtil;
import me.webrtc.jni.WebRtcUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

/**
 * 录音工具 mi.gao
 */
public class RecorderHelper {
    private AudioRecord mAudioRecord = null;
    private int mMinBufferSize;
    private byte[] mPCMBuffer;
    private boolean mIsRecording = false;
    private boolean mIsPause = false;

    private File tmpFile = null;//用于暂停功能的录音文件pcm
    private File outputFile = null;//最终的录音文件pcm
    private ArrayList<Short> inBuf = new ArrayList<>();//缓冲区数据

    private int shortsPerDrawSample;//每个用于画的采样数据是从多少short中取(最大/平均)
    private int totalReadShorts = 0;

    private int totalDataTemp = 0;
    private int addCountTemp = 0;

    private String mDirTag = "";
    private boolean mIsHeadsetPlugIn = false;

    private static RandomAccessFile bgFile;

    public static void setBg(String bg) {
        setBg(bg, 0);
    }

    public static void setBg(String bg, int seekToMs) {
        LogUtil.i("jimwind", "RecorderHelper setBg " + seekToMs);
        if (TextUtils.isEmpty(bg)) {
            bgFile = null;
            return;
        }
        try {
            bgFile = new RandomAccessFile(bg, "rw");
            if (seekToMs != 0) {
                bgFile.seek(Constant.getBytesPerMillisecond() * seekToMs);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static float mDB = Constant.DEFAULT_VOLUME;

    public static void setDb(float db) {
        mDB = db;
        LogUtil.i("jimwind", "RecorderHelper setDB mDB " + mDB);
    }

    /**
     * 在width要显示seconds的波
     *
     * @param dirTag 录音目录加个用户信息，不同用户使用不同的目录
     */
    public RecorderHelper(String dirTag) {
        shortsPerDrawSample = Constant.getShortsPerDrawSample();
        mDirTag = dirTag;
    }

    /**
     * Start recording. Create an encoding thread. Start record from this
     * thread.
     *
     * @throws IOException initAudioRecorder throws
     */
    private synchronized void startRecording() throws IOException {
        if (mIsRecording) {
            return;
        }
        if (mAudioRecord == null) {
            if (!initAudioRecorder()) {
                return;
            }
        }
        if (outputFile == null) {
            outputFile = tmpFile = new File(FileUtil.getRecordFilePath(mDirTag, "pcm"));
        } else {
            tmpFile = new File(FileUtil.getRecordFilePath(mDirTag, "pcm"));
        }

        try {
            mAudioRecord.startRecording();
            LogUtil.i("jimwind", "record helper start recording");
        } catch (IllegalStateException e) {
            mIsRecording = false;
            LogUtil.e("jimwind", "record helper exception 1:" + e.getMessage());
            throw new IOException();
        } catch (Throwable e) {
            mIsRecording = false;
            LogUtil.e("jimwind", "record helper exception 2:" + e.getMessage());
            throw new IOException();
        }

        new Thread() {

            @Override
            public void run() {
                WebRtcUtils.webRtcAgcInit(0, 255, Constant.DEFAULT_SAMPLING_RATE);
                WebRtcUtils.webRtcNsInit(Constant.DEFAULT_SAMPLING_RATE);

                FileOutputStream fos = null;
                //设置线程权限
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
                mIsRecording = true;
                mIsPause = false;
                int readTimes = 0;
                float shorts_of_millisecond = Constant.getShortsPerMillisecond();
                try {
                    fos = new FileOutputStream(tmpFile);
                    while (mIsRecording) {
                        int readSize = mAudioRecord.read(mPCMBuffer, 0, mMinBufferSize);
                        readTimes++;
                        if (readTimes < 8) {//魔术数字：因为启动录音最开始的数据是形成断音 实验后决定先去掉 mi.gao 2019/11/28
                            continue;
                        }
                        // 带降噪增益，目前只支持16000/32000 录音有混音，所以用了44100，不能降噪增益了
                        if (false) {
                            // 降噪增益
                            short[] shortData = new short[mPCMBuffer.length >> 1];
                            short[] processData = new short[mPCMBuffer.length >> 1];
                            ByteBuffer.wrap(mPCMBuffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortData);

                            short[] nsProcessData;
                            nsProcessData = WebRtcUtils.webRtcNsProcess(Constant.DEFAULT_SAMPLING_RATE, shortData.length, shortData);
                            WebRtcUtils.webRtcAgcProcess(nsProcessData, processData, shortData.length);
                            fos.write(shortsToBytes(processData));
                            fos.flush();

                            if (readSize > 0) {
                                if (mListener != null) {
                                    int totalMillisecond = 0;
                                    int length = readSize / 2;
                                    for (int i = 0; i < length; i++) {
                                        addCountTemp++;
                                        totalReadShorts++;
                                        totalDataTemp += Math.abs(processData[i] / Constant.WAVEFORM_SCALE_DOWN);
//                                        if(Math.abs(processData[i]) > maxPerDrawSample){
//                                            maxPerDrawSample = Math.abs(processData[i]);
//                                        }
                                        totalMillisecond = (int) (totalReadShorts / shorts_of_millisecond);
                                        if (addCountTemp == shortsPerDrawSample) {
                                            short s = (short) Math.sqrt((double) totalDataTemp / (double) shortsPerDrawSample);
                                            inBuf.add((s < 1 ? 1 : s));
                                            totalDataTemp = 0;
//                                        inBuf.add((short) Math.sqrt(maxPerDrawSample));
//                                        maxPerDrawSample = 0;
                                            addCountTemp = 0;
                                            mListener.onRecording(inBuf, totalMillisecond);
                                        }
                                    }
                                    if (totalMillisecond >= Constant.SHORT_AUDIO_MILLISECONDS_MAX + 256) {//加256毫秒
                                        mIsRecording = false;
                                        mListener.onRecording(inBuf, totalMillisecond);
                                        LogUtil.i("jimwind", "record helper totalReadShorts " + totalReadShorts);
                                        break;
                                    }
                                }
                            }
                        } else {
                            byte[] mix = mPCMBuffer;
                            if (bgFile != null) {
                                try {
                                    // 耳机插入才混音，不插耳机直接录外放音了
                                    if (mIsHeadsetPlugIn) {
                                        // 根据录音得到的数据长度，读取背景音文件中对应长度数据
                                        byte[] bg = new byte[readSize];
                                        if (bgFile.getFilePointer() >= bgFile.length()) {
                                            bgFile.seek(0);
                                        }
                                        bgFile.read(bg);
                                        byte[] dbBg = new byte[readSize];
                                        AudioUtil.amplifyPCMData(bg, readSize, dbBg, 16, mDB);

                                        byte[][] allAudioBytes = new byte[2][];
                                        allAudioBytes[0] = dbBg;
                                        allAudioBytes[1] = mPCMBuffer;
                                        // 实时混音
                                        mix = mixRawAudioBytes(allAudioBytes);
                                    } else {
                                        mix = mPCMBuffer;
                                    }
                                } catch (Exception e) {
                                    // 文件随时会被置空，表示背景音停了
                                }
                            }
                            // 将混音数据存入文件
                            fos.write(mix, 0, readSize);
                            fos.flush();

                            if (readSize > 0) {
//                                byte[] tmpBuf = new byte[readSize];
//                                System.arraycopy(mPCMBuffer, 0, tmpBuf, 0, readSize);
                                if (mListener != null) {
                                    short[] buf = new short[readSize / 2];
                                    //byte 转 short， 长度减一半，注意，这里是单声道
                                    for (int i = 0; i < readSize; i += 2) {
                                        buf[i / 2] = (short) (((mix[i + 1] & 0xFF) << 8) | (mix[i] & 0xFF));
                                    }
                                    int totalMillisecond = 0;
                                    for (int i = 0; i < buf.length; i++) {
                                        addCountTemp++;
                                        totalReadShorts++;
                                        totalDataTemp += Math.abs(buf[i] / Constant.WAVEFORM_SCALE_DOWN);
//                                        if(Math.abs(buf[i]) > maxPerDrawSample){
//                                            maxPerDrawSample = Math.abs(buf[i]);
//                                        }
                                        totalMillisecond = (int) (totalReadShorts / shorts_of_millisecond);
                                        if (addCountTemp == shortsPerDrawSample) {
                                            inBuf.add((short) Math.sqrt((double) totalDataTemp / (double) shortsPerDrawSample));
                                            totalDataTemp = 0;
//                                            inBuf.add((short) Math.sqrt(maxPerDrawSample));
//                                            maxPerDrawSample = 0;
                                            addCountTemp = 0;
                                            mListener.onRecording(inBuf, totalMillisecond);
                                        }
                                    }
                                    if (totalMillisecond >= Constant.SHORT_AUDIO_MILLISECONDS_MAX + 256) {//加256毫秒
                                        mIsRecording = false;
                                        mListener.onRecording(inBuf, totalMillisecond);
                                        LogUtil.i("jimwind", "record helper totalReadShorts " + totalReadShorts);
                                        break;
                                    }
                                }
                            }

                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    LogUtil.e("jimwind", "record helper exception 3: " + e.toString());
                } finally {
                    WebRtcUtils.webRtcNsFree();
                    WebRtcUtils.webRtcAgcFree();
                }

                // release and finalize audioRecord
                if (mAudioRecord == null) {
                    return;
                }
                try {
                    mAudioRecord.stop();
                } catch (IllegalStateException e) {
                    LogUtil.e("jimwind", "record helper exception 5: " + e.toString());
                }
                //不是第一个录音文件，则合并到第一个
                if (!outputFile.getAbsolutePath().equals(tmpFile.getAbsolutePath())) {
                    try {
                        InputStream in = new FileInputStream(tmpFile);
                        OutputStream out = new FileOutputStream(outputFile, true);
                        byte[] buf = new byte[mMinBufferSize];
                        int len;
                        while ((len = in.read(buf)) > 0) {
                            out.write(buf, 0, len);
                        }
                        in.close();
                        out.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                        LogUtil.e("jimwind", "record helper exception 6: " + e.toString());
                    }
                    tmpFile.delete();
                }

                if (!mIsPause) {
                    if (mAudioRecord != null) {
                        mAudioRecord.release();
                        mAudioRecord = null;
                    }
                }
                if (mListener != null) {
                    mListener.onRecord(outputFile.getAbsolutePath());
                }

            }
        }.start();


    }

    /**
     * 全局：
     * AudioRecord  audioSource     MIC
     * sampleRateInHz  16000
     * channelConfig   单声道
     * audioFormat     16bit
     * bufferSizeInBytes
     *
     * @return 1280 16000/(1280/2) = 25 根据log，40ms返回一次，40ms*25 = 1s
     */
    private boolean initAudioRecorder() {
        mMinBufferSize = AudioRecord.getMinBufferSize(Constant.DEFAULT_SAMPLING_RATE,
                Constant.DEFAULT_CHANNEL_CONFIG, Constant.DEFAULT_AUDIO_FORMAT);
        LogUtil.i("jimwind", "recorder helper mMinBufferSize original " + mMinBufferSize);
        try {
            /* Setup audio recorder */
            mAudioRecord = new AudioRecord(Constant.DEFAULT_AUDIO_SOURCE,
                    Constant.DEFAULT_SAMPLING_RATE, AudioFormat.CHANNEL_IN_STEREO,
                    Constant.DEFAULT_AUDIO_FORMAT,
                    mMinBufferSize);

        } catch (IllegalArgumentException e) {
            LogUtil.e("jimwind", "init audio record exception " + e.toString());
            return false;
        }
        mPCMBuffer = new byte[mMinBufferSize];
        return true;
    }

    public interface Listener {
        void onRecord(String file);

        void onRecording(ArrayList<Short> data, int totalMillisecond);
//        void onTimeChanged(int second, int deci_second);
    }

    private Listener mListener;

    public void setListener(Listener listener) {
        mListener = listener;
    }


    public synchronized void start() {
        try {
            inBuf.clear();
            totalReadShorts = 0;
            totalDataTemp = 0;
            addCountTemp = 0;
            startRecording();
        } catch (IOException e) {
            LogUtil.e("jimwind", "record helper exception 7:" + e.getMessage());
            e.printStackTrace();

        }
    }

    public synchronized void resume() {
        try {
            startRecording();
        } catch (IOException e) {
            LogUtil.e("jimwind", "record helper exception 8:" + e.getMessage());
            e.printStackTrace();
        }
    }

    public synchronized void pause() {
        mIsRecording = false;
        mIsPause = true;
    }

    public synchronized void stop() {
        mIsRecording = false;
        if (mIsPause) {//如果是暂停状态是选择停止!
            if (mAudioRecord != null) {
                mAudioRecord.release();
                mAudioRecord = null;
            }
        }
        mIsPause = false;
    }

    public void setHeadsetPlugIn(boolean headsetPlugIn) {
        mIsHeadsetPlugIn = headsetPlugIn;
    }

    public boolean isRecording() {
        return mIsRecording;
    }

    public boolean isPause() {
        return mIsPause;
    }

    private byte[] shortsToBytes(short[] data) {
        byte[] buffer = new byte[data.length * 2];
        int shortIndex, byteIndex;
        shortIndex = byteIndex = 0;
        for (; shortIndex != data.length; ) {
            buffer[byteIndex] = (byte) (data[shortIndex] & 0x00FF);
            buffer[byteIndex + 1] = (byte) ((data[shortIndex] & 0xFF00) >> 8);
            ++shortIndex;
            byteIndex += 2;
        }
        return buffer;
    }

    /**
     * 因为异常或其它非正常原因退出，可恢复上次录音
     *
     * @param history
     * @param frames
     */
    public int setHistoryRecord(String history, ArrayList<Short> frames) {
        outputFile = tmpFile = new File(history);
        mIsPause = true;
        inBuf.addAll(frames);
        totalReadShorts = inBuf.size() * shortsPerDrawSample;
        int totalMillisecond = (int) (totalReadShorts / Constant.getShortsPerMillisecond());
        return totalMillisecond;
    }

    byte[] mixRawAudioBytes(byte[][] bMulRoadAudioes) {
        if (bMulRoadAudioes == null || bMulRoadAudioes.length == 0)
            return null;

        byte[] realMixAudio = bMulRoadAudioes[0];

        if (bMulRoadAudioes.length == 1)
            return realMixAudio;

        for (int rw = 0; rw < bMulRoadAudioes.length; ++rw) {
            if (bMulRoadAudioes[rw].length != realMixAudio.length) {
                LogUtil.e("app", "column of the road of audio + " + rw + " is diffrent.");
                return null;
            }
        }
        int row = bMulRoadAudioes.length;
        int column = realMixAudio.length / 2;
        short[][] sMulRoadAudioes = new short[row][column];

        for (int r = 0; r < row; ++r) {
            for (int c = 0; c < column; ++c) {
                sMulRoadAudioes[r][c] = (short) ((bMulRoadAudioes[r][c * 2] & 0xff) | (bMulRoadAudioes[r][c * 2 + 1] & 0xff) << 8);
            }
        }
        short[] sMixAudio = new short[column];
        int mixVal;
        int sr = 0;
        for (int sc = 0; sc < column; ++sc) {
            mixVal = 0;
            sr = 0;
            for (; sr < row; ++sr) {
                mixVal += sMulRoadAudioes[sr][sc];
            }
            sMixAudio[sc] = (short) (mixVal / row);
        }
        for (sr = 0; sr < column; ++sr) {
            realMixAudio[sr * 2] = (byte) (sMixAudio[sr] & 0x00FF);
            realMixAudio[sr * 2 + 1] = (byte) ((sMixAudio[sr] & 0xFF00) >> 8);
        }
        return realMixAudio;
    }
}
