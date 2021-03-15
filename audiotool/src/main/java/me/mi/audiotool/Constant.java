package me.mi.audiotool;


import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;

import me.mi.audiotool.utils.DesUtils;
import me.mi.audiotool.utils.FileUtil;

//mi.gao
public class Constant {
    //这些参数值不可修改
    public static final int DEFAULT_AUDIO_SOURCE = MediaRecorder.AudioSource.MIC;

    public static final int DEFAULT_SAMPLING_RATE = 44100;//16000;//

    public static final int DEFAULT_CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO;

    public static final int CHANNEL = 2;

    public static final int DEFAULT_AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;//采样精度 16bit

    public static final int ENCODING_PCM_BIT = 16;

    public static final int DEFAULT_CHANNEL_OUT_CONFIG = AudioFormat.CHANNEL_OUT_STEREO;

    //录音是单声道，背景音是双声道
    public static final int SHORT_AUDIO_SECONDS_MAX = 3600;//1h 60;60s
    public static final int SHORT_AUDIO_MILLISECONDS_MAX = 3600000;//1h   60000;//60s

    public static int getBytesPerMillisecond() {
        return ENCODING_PCM_BIT * DEFAULT_SAMPLING_RATE * CHANNEL / 8 / 1000;
    }

    public static float getShortsPerMillisecond() {
        return ENCODING_PCM_BIT * DEFAULT_SAMPLING_RATE * CHANNEL / 8f / 2f / 1000f;
    }

    //十分一之秒的数据量
    public static int getBytesPerDeciSecond() {
        return ENCODING_PCM_BIT * DEFAULT_SAMPLING_RATE * CHANNEL / 8 / 10;
    }

    //    public static int getBytesOf60s(){
//        return ENCODING_PCM_BIT * DEFAULT_SAMPLING_RATE * CHANNEL / 8 * 60;
//    }
//
//    public static int getBytesOf60s(int sampleRate, int channel){
//        return ENCODING_PCM_BIT * sampleRate * channel / 8 * 60;
//    }
//    public static int getBytesOf20s(){
//        return ENCODING_PCM_BIT * DEFAULT_SAMPLING_RATE * CHANNEL / 8 * 20;
//    }
    public static int getBytesOf(int seconds) {
        return ENCODING_PCM_BIT / 8 * DEFAULT_SAMPLING_RATE * CHANNEL * seconds;
    }

    public static int getBytesOf(int sampleRate, int channel, int seconds) {
        return ENCODING_PCM_BIT * sampleRate * channel / 8 * seconds;
    }

    /**
     * 在宽为width的控件中，在固定波形宽和空隙的情况下
     * 最多能显示采样数量
     */
    private static float getDrawSampleCountOnWidth(Context context, int width) {
        if (width == 0) {
            Log.e("jimwind", "getDrawSampleCountOnWidth width is zero!!!");
            return 1;
        }
        //线宽0.5dp 空隙也是0.5dp
        float sampleWidth = getSampleWidth(context);

        float sampleCount = width / sampleWidth;
        Log.i("jimwind", "getDrawSampleCountOnWidth " + sampleCount);
        return sampleCount;
    }

    // 一个采样的宽度 （POINTS_PER_SECOND 表示一秒取的采样数，这样可知一秒的宽度）
    public static float getSampleWidth(Context context) {
        float sampleWidth = DesUtils.dp2px(context, WAVEFORM_SAMPLE_WIDTH + WAVEFORM_SAMPLE_SPACE);
        sampleWidth *= scale;
        return sampleWidth;
//        return 1;
    }

    public static final float DEFAULT_SCALE = 0.1f;
    public static float scale = DEFAULT_SCALE;
    public static float SCALE_DOWN_MAX = 1f / 599f;

    // 每像素是多少毫秒 误差问题
    public static float getMillisecondsPerPixel(Context context) {
        return 1000f / (getSampleWidth(context) * POINTS_PER_SECOND);
    }

    // 每毫秒是多少像素 误差问题
    public static float getPixelsPerMillisecond(Context context) {
        return (getSampleWidth(context) * POINTS_PER_SECOND) / 1000f;
    }

    //
    public static float getSampleStrokeWidth(Context context) {
        float sampleWidth = DesUtils.dp2px(context, WAVEFORM_SAMPLE_WIDTH);
        return sampleWidth;
//        return 1;
    }

    /**
     * 在宽度为width的控件上显示秒数为seconds
     * 采样率是DEFAULT_SAMPLING_RATE, 通道数是CHANNEL
     *
     * @return 所画的帧是从多少个shorts中取平均
     */
    public static int getShortsPerDrawSample() {
        // 一秒画几个点，总共几秒
        float count = Constant.POINTS_PER_SECOND;
        //seconds总共的shorts
        int totalShorts = Constant.getBytesOf(1) / 2;
        //每个采样从多少short中取平均
        int shortsPerDrawSample = (int) (totalShorts / count);
        return shortsPerDrawSample;
    }

    /**
     * 在宽度为width的控件上显示秒数为seconds
     *
     * @param context
     * @param width      控件宽度
     * @param seconds    总显示秒数
     * @param sampleRate 采样率 16000 | 44100 ...
     * @param channels   通道数 1 | 2
     * @return
     */
    public static int getShortsPerDrawSample(Context context, int width, int seconds, int sampleRate, int channels) {
        //屏幕上总共最多可显示的采样数
        float count = Constant.getDrawSampleCountOnWidth(context, width);
        //seconds总共的shorts
        int totalShorts = Constant.getBytesOf(sampleRate, channels, seconds) / 2;
        //每个采样从多少short中取平均
        int shortsPerDrawSample = (int) (totalShorts / count);
        Log.i("jimwind", "getShortsPerDrawSample totalShorts:" + totalShorts + " count:" + count + " shortsPerDrawSample:" + shortsPerDrawSample);

        return shortsPerDrawSample;
    }

    /**
     * 秒 -> 像素
     *
     * @param secondsInWidth 在width中显示的秒数
     * @param width          控件宽度
     * @param seconds        需要转为pixels的秒数
     * @return
     */
    public static float getPixelsOfSecond(float secondsInWidth, float width, float seconds) {
        return width * seconds / secondsInWidth;
    }

    /**
     * seconds秒有几帧数据
     *
     * @param seconds
     * @return
     */
    public static int getFramesOfSecond(int seconds, Context context, int width, float maxSecondsOnWidth) {
        //width上总共最多可显示的采样数
        float frames = Constant.getDrawSampleCountOnWidth(context, width);
        float framesPerSecond = frames / maxSecondsOnWidth;
        return (int) (seconds * framesPerSecond);
    }

    public static final String RECORD_DIR = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Record/audio_tool/";
    //刚录的录音文件
    @Deprecated
    public static final String recordCutPCM = "record_cut.pcm";//录音裁剪文件
    @Deprecated
    public static final String recordCutWav = "record_cut.wav";//录音裁剪wav
    @Deprecated
    public static final String recordCutPitchWav = "record_cut_pitch.wav";//录音裁剪wav变声
    @Deprecated
    public static final String recordCutPitchPCM = "record_cut_pitch.pcm";//录音裁剪wav变声后转pcm
    @Deprecated
    public static final String localRecordCut = "local_record_cut.mp3";//本地录音裁剪文件 解码后给 recordCutPCM
    //背景文件
    public static final String bgFile = "bg.mp3"; //背景音原文件
    @Deprecated
    public static final String bgCutFile = "bg_cut.mp3"; //背景音裁剪后文件
    @Deprecated
    public static final String bgCutPCM = "bg_cut.pcm";//背景音裁剪后解码成pcm文件
    //混音
    @Deprecated
    public static final String mixPCM = "mix.pcm";//混音后文件
    @Deprecated
    public static final String mixMP3 = "mix.mp3";//混音后MP3文件

    //-------------------------------------------------------------------------------------
    //v1
    public static final String recordWAV = "record.wav";//v1 录音完后要生成wav
    //选择本地录音文件
    public static final String localRecord = "local_record.mp3";//本地录音文件

    public static String getFullPath(String fileName) {
        FileUtil.createOrExistsDir(RECORD_DIR);
        return RECORD_DIR + fileName;
    }

    public static final float WAVEFORM_SAMPLE_WIDTH = 4f;//dp
    public static final float WAVEFORM_SAMPLE_SPACE = 0f;


    /**
     * v1版本
     *
     * @param sampleRateInHz
     * @param channel
     * @param audioFormat
     * @param samplesPerSecond
     * @return
     */
    public static int getBytesPerSample(int sampleRateInHz, int channel, int audioFormat, int samplesPerSecond) {
        Log.v("jimwind", "getBytesPerSample " + sampleRateInHz + "/" + channel + "/" + audioFormat + "/" + samplesPerSecond);
        //一秒总数据
        int totalBytes = ENCODING_PCM_BIT * sampleRateInHz * channel / 8;
        int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        if (channel == 1) {
            channelConfig = AudioFormat.CHANNEL_IN_MONO;
        } else if (channel == 2) {
            channelConfig = AudioFormat.CHANNEL_IN_STEREO;
        }
        //每次返回多少数据
        int bufferSizeInBytes = AudioTrack.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
        //一秒返回多少次
        int times = totalBytes / bufferSizeInBytes;
        //确定一秒多少采样 samplesPerSecond = 150
        int bytesPerSample = totalBytes / samplesPerSecond;
        Log.v("jimwind", "bytesPerSample " + bytesPerSample);
        //多少采样取平均   totalBytes / 150
        return bytesPerSample;
    }

    public static final int WAVEFORM_SCALE_DOWN = 10;

    //在编辑页，一秒取10个点画。
    public static final int POINTS_PER_SECOND = 20;

    public static final float DEFAULT_VOLUME = 1.0f;
}