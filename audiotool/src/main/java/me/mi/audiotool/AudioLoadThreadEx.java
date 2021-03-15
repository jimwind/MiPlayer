package me.mi.audiotool;

import me.mi.audiotool.utils.LogUtil;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

public class AudioLoadThreadEx extends Thread {
    private String file;
    private ArrayList<Short> wfBuf = new ArrayList<>();
    private long totalReadSizeInBytes = 0;

    private int shortsPerDrawSample;//每个用于画的采样数据是从多少short中取(最大/平均)
    private int addCountTemp = 0;
    private int totalDataTemp = 0;
    private int mScaleDown = Constant.WAVEFORM_SCALE_DOWN;
    private final int POINTS_PER_SECOND = Constant.POINTS_PER_SECOND;//1秒10个点

    public AudioLoadThreadEx(String file, int scaleDown) {
        this.file = file;
        int shortCountPerSecond = Constant.getBytesOf(1) / 2;
        shortsPerDrawSample = shortCountPerSecond / POINTS_PER_SECOND;
        if (scaleDown > 0) {
            mScaleDown = scaleDown;
        }
        LogUtil.i("jimwind", "AudioLoadThreadEx shortsPerDrawSample " + shortsPerDrawSample);
    }

    @Override
    public void run() {
        try {
            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[shortsPerDrawSample * 2];
            int readSize = 0;

            while ((readSize = fis.read(buffer)) != -1) {
                totalReadSizeInBytes += readSize;
                short[] buf = new short[shortsPerDrawSample];//byte -> short
                for (int i = 0; i < buffer.length; i += 2) {
                    buf[i / 2] = (short) (((buffer[i + 1] & 0xFF) << 8) | (buffer[i] & 0xFF));
                }
                for (int i = 0; i < buf.length; i++) {
                    addCountTemp++;
                    totalDataTemp += Math.abs(buf[i] / mScaleDown);
                    if (addCountTemp == shortsPerDrawSample) {
                        short s = (short) Math.sqrt((double) totalDataTemp / (double) shortsPerDrawSample);
                        wfBuf.add(s < 1 ? 1 : s);
                        addCountTemp = 0;
                        totalDataTemp = 0;
                    }
                }

            }
            fis.close();
            if (mListener != null) {
                mListener.onLoadFinished(wfBuf, totalReadSizeInBytes);
            }

        } catch (FileNotFoundException e) {
            LogUtil.e("jimwind", "audio load thread exception " + e.toString());
            e.printStackTrace();
        } catch (IOException e) {
            LogUtil.e("jimwind", "audio load thread exception " + e.toString());
            e.printStackTrace();
        }

    }

    public interface Listener {
        void onLoadFinished(ArrayList<Short> allFrames, long totalReadSizeInBytes);
    }

    private Listener mListener;

    public void setListener(Listener listener) {
        mListener = listener;
    }
}
