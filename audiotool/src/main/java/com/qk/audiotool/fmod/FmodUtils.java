package com.qk.audiotool.fmod;


public class FmodUtils {
    private static FmodUtils mInstance;

    private FmodUtils() {

    }

    public static FmodUtils getInstance(Listener listener) {
        synchronized (FmodUtils.class) {
            if (mInstance == null) {
                mInstance = new FmodUtils();
            }
        }
        if (listener != null) {
            mListener = listener;
        }
        return mInstance;
    }

    public synchronized static FmodUtils getInstanceFFT(FFTListener listener) {
        synchronized (FmodUtils.class) {
            if (mInstance == null) {
                mInstance = new FmodUtils();
            }
        }
        if (listener != null) {
            mFftListener = listener;
        }
        return mInstance;
    }

    public native void effects(String[] path, float[] pitch, float[] volume, float[] positions, float[] delays, float[] seconds, int playMillisecondOffset);

    public native void saveEffects(String[] path, float[] pitch, float[] volume, float[] positions, float[] delays, float[] seconds, String outputPath);

    public native void play(String url, float startPlayMilliseconds);

    public native void stop();

    public native boolean isPlaying();

    public void progress(int playMilliseconds, int totalMilliseconds, boolean play) {
//        Log.v("jimwind", "FmodUtils progress "+playMilliseconds+"/"+totalMilliseconds);
        if (mListener != null) {
            mListener.progress(playMilliseconds, totalMilliseconds, play);
        }
    }

    public void fft(float[] left, float[] right, int length, int playMilliseconds, boolean playing) {
        if (mFftListener != null) {
            mFftListener.fft(left, right, length, playMilliseconds, playing);
        }
    }

    static {

        try {
            System.loadLibrary("fmodL");
        } catch (UnsatisfiedLinkError e) {
        }
        try {
            System.loadLibrary("fmod");
        } catch (UnsatisfiedLinkError e) {
        }
        //特效处理的 动态库
        System.loadLibrary("fmod_util");
    }

    public interface Listener {
        void progress(int progress, int total, boolean play);
    }

    private static Listener mListener;

    public interface FFTListener {
        void fft(float[] left, float[] right, int length, int playMilliseconds, boolean playing);
    }

    private static FFTListener mFftListener;
}
