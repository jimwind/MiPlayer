package com.qk.audiotool;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnBufferingUpdateListener;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.media.audiofx.Visualizer;
import android.net.Uri;
import android.text.TextUtils;

import com.qk.audiotool.utils.LogUtil;

import java.io.IOException;

/*
 * author: mi.gao
 */
public class MediaPlayerManager implements OnBufferingUpdateListener,
        OnCompletionListener, OnPreparedListener, MediaPlayer.OnErrorListener, MediaPlayer.OnSeekCompleteListener {
    private MediaPlayer mMediaPlayer;
    private Context mContext;
    private String mUrl;
    private int mMillisecond;
    private int mIdentify;//在列表中播放时，如果有一个正在播放，现在要播放另一个，先停止上一个，并更新上一个的状态，一般用position
    private Visualizer mVisualizer;

    public MediaPlayerManager(Context context) {
        mContext = context;
    }

    public boolean play(String url, int identify) {
        return playSeekTo(url, 0, identify);
    }

    public boolean playSeekTo(String url, int millisecond, int identify) {
        if (TextUtils.isEmpty(url)) {
            return false;
        }
//        LogUtil.e("jimwind","playSeekTo "+ LogUtil.getStackTraceString(new Throwable()));
        LogUtil.i("jimwind", "MediaPlayerManager url:" + url);
        LogUtil.i("jimwind", "MediaPlayerManager millisecond:" + millisecond);
        mMillisecond = millisecond;
        if (url.equals(mUrl)) {
            if (mMediaPlayer != null) {
                try {
                    mMediaPlayer.seekTo(millisecond);
                    return true;
                } catch (IllegalStateException e) {
                    return false;
                }
            } else {
                return playAsync(url);
            }
        } else {
            if (mMediaPlayer != null) {
                if (mMediaPlayer.isPlaying()) {
                    mMediaPlayer.stop();
                    //this is the new listener, but old identify
                    mListener.onPlayStop(mIdentify);
                }
            }
            mUrl = url;
            mIdentify = identify;
            return playAsync(url);
        }
    }

    private boolean playAsync(String url) {
        if (TextUtils.isEmpty(url)) {
            return false;
        }
        initPlayer();
        try {
            mMediaPlayer.setDataSource(url);
            mMediaPlayer.prepareAsync();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    //外部调用停止,说明播放完全停止
    public void stop() {
        if (mMediaPlayer != null) {
            try {
                mMediaPlayer.stop();
            } catch (IllegalStateException e) {

            }
            if (mMediaPlayer != null) {
                mMediaPlayer.release();
            }
            mMediaPlayer = null;
        }
        if (mListener != null) {
            mListener.onPlayStop(mIdentify);
        }

        mPlayState = State.STOP;
        if (mVisualizer != null) {
            mVisualizer.setEnabled(false);
        }
    }

    //外部调用
    public void pause() {
        //调用pause不一定成功，可能是某一个音频刚好播放完毕，
        if (mMediaPlayer != null) {
            mMediaPlayer.pause();
        }
        mPlayState = State.PAUSE;
    }

    //外部调用
    public void resume() {
        if (mMediaPlayer != null) {
            mMediaPlayer.start();
        }
        mPlayState = State.PLAY;
    }

    public boolean isPlaying() {
        if (mMediaPlayer != null) {
            try {
                return mMediaPlayer.isPlaying();
            } catch (IllegalStateException e) {
                return false;
            }
        }
        return false;
    }

    public boolean isPause() {
        return mPlayState == State.PAUSE;
    }

    private void initPlayer() {
        if (mMediaPlayer != null) {
            mMediaPlayer.release();
        }
        try {
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setOnCompletionListener(this);
            mMediaPlayer.setOnBufferingUpdateListener(this);
            mMediaPlayer.setOnPreparedListener(this);
            mMediaPlayer.setOnErrorListener(this);
            mMediaPlayer.setScreenOnWhilePlaying(true);
            mMediaPlayer.setOnSeekCompleteListener(this);
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            setupVisualizer();

        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void onPrepared(MediaPlayer mp) {
        if (mp.equals(mMediaPlayer)) {
            mp.seekTo(mMillisecond);
        } else {
            try {
                mp.stop();
            } catch (IllegalStateException e) {

            }
            mp.release();
        }
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (mp.equals(mMediaPlayer)) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        } else {
            try {
                mp.stop();
            } catch (IllegalStateException e) {

            }
            mp.release();
        }

        if (mListener != null) {
            mListener.onPlayFinish(true, "");
        }
        if (mVisualizer != null) {
            mVisualizer.setEnabled(false);
        }
    }

    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {

    }

    @Override
    public boolean onError(MediaPlayer mediaPlayer, int what, int extra) {
        if (mediaPlayer.equals(mMediaPlayer)) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        } else {
            mediaPlayer.stop();
            mediaPlayer.release();
        }
        if (mListener != null) {
            mListener.onPlayFinish(false, "error: what " + what + " extra:" + extra);
        }
        return false;
    }

    @Override
    public void onSeekComplete(MediaPlayer mp) {
        if (mp.equals(mMediaPlayer)) {
            mp.start();
        } else {
            mp.stop();
            mp.release();
        }
    }

    //音频时长
    private int duration;

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    /**
     * 获取当前播放进度
     *
     * @return
     */
    public int getPlayMilliseconds() {
        try {
            return mMediaPlayer != null && mMediaPlayer.isPlaying() ? mMediaPlayer.getCurrentPosition() : 0;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * @param context
     * @param audioUrl
     * @return ms 毫秒
     */
    public static int getDuration(Context context, String audioUrl) {
        if (TextUtils.isEmpty(audioUrl)) {
            return 0;
        }
        int duration = 0;
        MediaPlayer mediaPlayer = MediaPlayer.create(context, Uri.parse(audioUrl));
        if (mediaPlayer != null) {
            duration = mediaPlayer.getDuration();
            mediaPlayer.stop();
            mediaPlayer.release();
        }
        return duration;
    }

    private State mPlayState = State.STOP;

    public enum State {
        PLAY, PAUSE, STOP
    }

    public interface Listener {
        void onPlayFinish(boolean success, String errorMsg);

        void onPlayStop(int identify);
    }

    private Listener mListener;

    public void setListener(Listener listener) {
        mListener = listener;
    }

    private void setupVisualizer() {
        if (mVisualizer != null) {
            mVisualizer.setEnabled(false);
            mVisualizer = null;
        }
        if (mVisualizer == null) {
            mVisualizer = new Visualizer(mMediaPlayer.getAudioSessionId());
            //设置需要转换的音乐内容长度，专业的说这就是采样，该采样值一般为2的指数倍，如64,128,256,512,1024。这里我设置了128，原因是长度越长，FFT算法运行时间更长。
            mVisualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[0]);
            //rate      表示采样的周期，即隔多久采样一次，联系前文就是隔多久采样128个数据，本文设置为512mHz更新一次(Visualizer.getMaxCaptureRate()/2)
            //iswave    是波形信号
            //isfft     是FFT信号，表示是获取波形信号还是频域信号
            mVisualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(Visualizer visualizer, byte[] waveform, int samplingRate) {

                }

                @Override
                public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
                    LogUtil.i("jimwind", "MediaPlayerManager onFftDataCapture");
                }
            }, Visualizer.getMaxCaptureRate() / 2, false, true);
            mVisualizer.setEnabled(true);
        }

    }
}