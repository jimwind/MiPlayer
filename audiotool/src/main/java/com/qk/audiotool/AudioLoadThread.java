package com.qk.audiotool;

import android.content.Context;

import com.qk.audiotool.utils.LogUtil;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

//load pcm
public class AudioLoadThread extends Thread {
    private String file;
    private ArrayList<Short> wfBuf = new ArrayList<>();
    private long totalReadSizeInBytes = 0;

    private int shortsPerDrawSample;//每个用于画的采样数据是从多少short中取(最大/平均)
    private int addCountTemp = 0;
    private int totalDataTemp = 0;
    private int mScaleDown = Constant.WAVEFORM_SCALE_DOWN;

    /**
     * @param file
     * @param context
     * @param width   控件宽度
     */
    public AudioLoadThread(String file, Context context, int width, int scaleDown) throws IllegalArgumentException {
//        if(TextUtils.isEmpty(file) || !new File(file).exists()){
//            throw new IllegalArgumentException("file is empty");
//        }
        LogUtil.i("jimwind", "audio load thread file " + file);
        this.file = file;
        shortsPerDrawSample = Constant.getShortsPerDrawSample();
        if (scaleDown > 0) {
            mScaleDown = scaleDown;
        }
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
            if (mListener != null) {
                mListener.onException();
            }
        } catch (IOException e) {
            LogUtil.e("jimwind", "audio load thread exception " + e.toString());
            e.printStackTrace();
            if (mListener != null) {
                mListener.onException();
            }
        } catch (Exception e) {
            if (mListener != null) {
                mListener.onException();
            }
        }

    }

    public interface Listener {
        void onLoadFinished(ArrayList<Short> allFrames, long totalReadSizeInBytes);

        void onException();
    }

    private Listener mListener;

    public void setListener(Listener listener) {
        mListener = listener;
    }
}
