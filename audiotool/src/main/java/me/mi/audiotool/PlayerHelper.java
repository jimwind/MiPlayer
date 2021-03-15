package me.mi.audiotool;

import android.media.AudioManager;
import android.media.AudioTrack;
import android.text.TextUtils;

import me.mi.audiotool.utils.LogUtil;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

public class PlayerHelper {
    private final String TAG = PlayerHelper.class.getSimpleName();
    private AudioTrack player;
    private DataInputStream dis = null;
    private int bufferSizeInBytes;
    private String file;
    private int mIdentify;

    private ArrayList<Short> inBuf = new ArrayList<>();//缓冲区数据
    private boolean isPlaying = false;
    private boolean isPause = false;

    private float shorts_of_millisecond = Constant.getShortsPerMillisecond();

    private int shortsPerDrawSample;//每个用于画的采样数据是从多少short中取(最大/平均)
    private long totalReadShorts = 0;

    /**
     *
     */
    public PlayerHelper() {
        shortsPerDrawSample = Constant.getShortsPerDrawSample();
        LogUtil.i("jimwind", "record shortsPerDrawSample " + shortsPerDrawSample);
    }

    public boolean isPlaying() {
        return isPlaying || (player != null && player.getPlayState() == AudioTrack.PLAYSTATE_PLAYING);
    }

    public boolean isPause() {
        return isPause || (player != null && player.getPlayState() == AudioTrack.PLAYSTATE_PAUSED);
    }

    public void pause() {
        if (player != null && isPlaying) {
            player.pause();
            LogUtil.i("jimwind", "player helper pause");
            isPause = true;
            isPlaying = false;
        }
    }

    public void resume() {
        skipBytes = 0;
        if (player != null && player.getState() == AudioTrack.STATE_INITIALIZED) {
            LogUtil.i("jimwind", "player helper resume success");
            isPause = false;
            isPlaying = true;
            new PlayThread(file).start();
        } else {
            LogUtil.i("jimwind", "player helper resume failed, " + (player != null));
        }
    }

    private int skipBytes = 0;
    private int skipMillisecond = 0;

    public void seekTo(int millisecond) {
        skipMillisecond = millisecond;
        skipBytes = Constant.getBytesPerMillisecond() * millisecond;
        totalReadShorts = 0;
        new PlayThread(file).start();

    }

    /**
     * 会将播放状态或暂停状态的play中止并清空
     * 因为play将用作播放其它音频文件
     */
    public void interrupt() {
        LogUtil.i("jimwind", "player helper interrupt");
        isPlaying = false;
        isPause = false;
        try {
            if ((player != null) && (player.getState() == AudioTrack.STATE_INITIALIZED)) {
                if (player.getPlayState() != AudioTrack.PLAYSTATE_STOPPED) {
                    player.flush();
                    player.stop();
                }
                player.release();
            }
        } catch (Exception e) {

        }
        player = null;
    }


    public void play(String file) {
        play(file, 0);
    }

    /**
     * @param file
     * @param identify 列表中使用，用于标识某个item
     */
    public boolean play(String file, int identify) {
        if (mThreadIsRunning) {
            LogUtil.e(TAG, "jimwind call play but mThreadIsRunning ");
            return false;
        }
        if (TextUtils.isEmpty(file)) {
            LogUtil.e(TAG, "jimwind call play but file is null ");
            return false;
        }
        if (isPlaying) {
            LogUtil.e(TAG, "jimwind call play but isPlaying ");
            return false;
        }

        skipMillisecond = 0;
        skipBytes = 0;

        this.file = file;
        this.mIdentify = identify;

        totalReadShorts = 0;
        new PlayThread(file).start();
        return true;
    }

    private boolean mThreadIsRunning = false;

    private class PlayThread extends Thread {
        private String file;

        public PlayThread(String file) {
            this.file = file;
        }

        @Override
        public void run() {
            mThreadIsRunning = true;
            //如果player没有初始化，就是从最开始播放
            if (player == null || player.getState() == AudioTrack.STATE_UNINITIALIZED) {
                inBuf.clear();
                try {
                    dis = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
                    dis.skipBytes(skipBytes);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                    mThreadIsRunning = false;
                    return;
                } catch (IOException e) {
                    e.printStackTrace();
                    mThreadIsRunning = false;
                    return;
                }

                bufferSizeInBytes = AudioTrack.getMinBufferSize(
                        Constant.DEFAULT_SAMPLING_RATE,//<<--与原音频采样率一致
                        Constant.DEFAULT_CHANNEL_OUT_CONFIG,//<<--与原音频通道数一致
                        Constant.DEFAULT_AUDIO_FORMAT);

                LogUtil.i("jimwind", "player helper bufferSizeInBytes " + bufferSizeInBytes);
                player = new AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        Constant.DEFAULT_SAMPLING_RATE,//<<--与原音频采样率一致
                        Constant.DEFAULT_CHANNEL_OUT_CONFIG,//<<--与原音频通道数一致
                        Constant.DEFAULT_AUDIO_FORMAT,
                        bufferSizeInBytes,
                        AudioTrack.MODE_STREAM);
                player.setVolume(Constant.DEFAULT_VOLUME);
            }
            byte[] data = new byte[bufferSizeInBytes];
            player.play();
            isPlaying = true;
            isPause = false;
            LogUtil.i("jimwind", "player helper start");
            while (isPlaying && !isPause) {
                int i = 0;
                try {
                    while (dis.available() > 0 && i < data.length) {
                        data[i] = dis.readByte();
                        i++;
                    }
                } catch (IOException e) {
                    LogUtil.e("jimwind", "player helper exception " + e.toString());
                    isPlaying = false;
                    isPause = false;
                    break;
                }
                if (!isPlaying) {
                    LogUtil.e("jimwind", "player helper isPlaying == false");
                    break;
                }
                player.write(data, 0, data.length);

                //播放完毕，会将player释放
                if (i != bufferSizeInBytes) {

                    LogUtil.e("jimwind", "player helper finish");
                    try {
                        player.stop();
                        player.release();
                    } catch (Exception e) {

                    }
                    isPlaying = false;
                    skipMillisecond = 0;
                    if (mListener != null) {
                        mListener.onCompletion();
                    }
                    break;
                }
                totalReadShorts += data.length / 2;
                int totalMillisecond = (int) (totalReadShorts / shorts_of_millisecond);
//                LogUtil.i("jimwind", "player helper totalMillisecond " + totalMillisecond);
                if (mListener != null) {
                    mListener.onPlaying(mIdentify, totalMillisecond + skipMillisecond);
                }
            }

            if (isPause) {
                if (mListener != null) {
                    mListener.onPause();
                }
            }
            mThreadIsRunning = false;
        }
    }

    // 有些列表会更新
    public void updateIdentify(int identify) {
        mIdentify = identify;
    }

    public void setVolume(float volume) {
        if (player != null) {
            player.setVolume(volume);
        }
    }

    public String getFile() {
        return file;
    }

    public interface Listener {
        // 播放完成
        void onCompletion();

        // 播放暂停，音频文件不会变化
        void onPause();

        // 正在播放
        void onPlaying(int identify, int totalMillisecond);
    }

    private Listener mListener;

    public void setListener(Listener listener) {
        mListener = listener;
    }


}
