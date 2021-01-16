package com.qk.audiotool.ffmpeg;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.view.Surface;

import com.qk.audiotool.utils.LogUtil;


public class AudioPlayer {
    public native void render(String input, Surface surface);

    public native void sound(String input, String output);

    public native void playAudio(String input, String output);

    public native int Resample(String sourcePath, String targetPath,
                               int sourceSampleRate, int targetSampleRate,
                               int sourceChannels, int targetChannels);

    /**
     * 创建一个AudioTrack对象，用于播放
     *
     * @param nb_channels
     * @return
     */
    public AudioTrack createAudioTrack(int sampleRateInHz, int nb_channels) {
        //固定格式的音频码流
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        LogUtil.i("jason", "nb_channels:" + nb_channels);
        //声道布局
        int channelConfig;
        if (nb_channels == 1) {
            channelConfig = android.media.AudioFormat.CHANNEL_OUT_MONO;
        } else if (nb_channels == 2) {
            channelConfig = android.media.AudioFormat.CHANNEL_OUT_STEREO;
        } else {
            channelConfig = android.media.AudioFormat.CHANNEL_OUT_STEREO;
        }

        int bufferSizeInBytes = AudioTrack.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);

        AudioTrack audioTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRateInHz, channelConfig,
                audioFormat,
                bufferSizeInBytes, AudioTrack.MODE_STREAM);
        //播放
        //audioTrack.play();
        //写入PCM
        //audioTrack.write(audioData, offsetInBytes, sizeInBytes);
        return audioTrack;
    }

    static {
        System.loadLibrary("avutil");
        System.loadLibrary("avcodec");
        System.loadLibrary("swresample");
        System.loadLibrary("avformat");
        System.loadLibrary("swscale");
        System.loadLibrary("avfilter");
        System.loadLibrary("ffmpeg-cmd");
    }

    public void onPlaying(byte[] data) {
        if (mListener != null) {
            mListener.onPlaying(data);
        }
    }

    public interface Listener {
        void onPlaying(byte[] data);
    }

    private Listener mListener;

    public void setListener(Listener listener) {
        mListener = listener;
    }
}
