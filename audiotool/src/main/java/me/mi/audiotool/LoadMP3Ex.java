package me.mi.audiotool;

import android.content.Context;
import android.util.Log;

import me.mi.audiotool.ffmpeg.AudioPlayer;
import me.mi.audiotool.utils.FileUtil;
import me.mi.audiotool.utils.LogUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ShortBuffer;

import me.rosuh.libmpg123.MPG123;

public class LoadMP3Ex {

    private final String TAG = "LoadMP3Ex";

    private Context context;
    private MPG123 mMp3Decoder;
    private int mFileSize;
    private File mInputFile;
    private AudioBean mAudioBean;
    private String mPCMFilePath;
    private String mPCMFilePathTemp;

    private final int POINTS_PER_SECOND = Constant.POINTS_PER_SECOND;//1秒10个点
    private int shortsPerDrawSample = 0;

    public LoadMP3Ex(Context context, String mp3) {
        this.context = context;
        LogUtil.i(TAG, "Load mp3 " + mp3);
        mInputFile = new File(mp3);
        mFileSize = (int) mInputFile.length();
        mMp3Decoder = new MPG123(mp3);
        mAudioBean = new AudioBean();
        mAudioBean.setChannels(mMp3Decoder.getNumChannels());
        mAudioBean.setSampleRate(mMp3Decoder.getRate());
        int shortCountPerSecond = Constant.getBytesOf(mMp3Decoder.getRate(), mMp3Decoder.getNumChannels(), 1) / 2;
        shortsPerDrawSample = shortCountPerSecond / POINTS_PER_SECOND;
        if (mAudioBean.getChannels() == 0 || mAudioBean.getSampleRate() == 0) {
            Log.i(TAG, "jimwind mp3 " + mp3);
            Log.i(TAG, "jimwind channels:" + mAudioBean.getChannels() + " sampleRate:" + mAudioBean.getSampleRate() + " shortsPerDrawSample " + shortsPerDrawSample);
        }
    }

    public AudioBean getAudioInfo() {
        mMp3Decoder.seek(0);
        ShortBuffer buffer = decodeToBuffer();
        int rate = mMp3Decoder.getRate();
        int channels = mMp3Decoder.getNumChannels();
        if (rate > 0 && channels > 0) {
            mAudioBean.setSeconds(buffer.position() / (rate * channels));
        }
        mAudioBean.setFrames(convertToWaveform(buffer));
        return mAudioBean;
    }

    /**
     * 解析MP3文件 生成PCM
     *
     * @param pcmFilePath 最终生成的pcm文件
     * @param pcmFileTemp temp file（用于重采样的源文件）
     * @return
     */
    public AudioBean getAudioInfo(String pcmFilePath, String pcmFileTemp) {
        LogUtil.i(TAG, "jimwind getAudioInfo pcm file:" + pcmFilePath);

        mPCMFilePath = pcmFilePath;
        mPCMFilePathTemp = pcmFileTemp;
        mMp3Decoder.seek(0);
        ShortBuffer buffer = decodeToBuffer();
        int rate = mMp3Decoder.getRate();
        int channels = mMp3Decoder.getNumChannels();
        if (rate > 0 && channels > 0) {
            mAudioBean.setSeconds(buffer.position() / (rate * channels));
        }
        mAudioBean.setFrames(convertToWaveform(buffer));
        // 如果采样率一致，就直接使用
        if (rate == Constant.DEFAULT_SAMPLING_RATE) {
            new File(mPCMFilePathTemp).renameTo(new File(mPCMFilePath));
        } else { // 否则重采样
            AudioPlayer a = new AudioPlayer();
            // 重采样
            a.Resample(mPCMFilePathTemp, mPCMFilePath, rate, Constant.DEFAULT_SAMPLING_RATE, 2, 2);
            FileUtil.delFile(mPCMFilePathTemp);
//            try {
//                FileInputStream fis = new FileInputStream(mPCMFilePathTemp);
//                FileOutputStream fos = new FileOutputStream(mPCMFilePath);
//                new SSRC(fis, fos,
//                        rate,
//                        Constant.DEFAULT_SAMPLING_RATE,
//                        2,
//                        2,
//                        channels,
//                        Integer.MAX_VALUE, 0, 0, true);
//            } catch (FileNotFoundException e) {
//                e.printStackTrace();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
        }

        return mAudioBean;
    }

    /**
     * 用MPG123 解析mp3文件
     *
     * @return
     */
    private ShortBuffer decodeToBuffer() {
        FileOutputStream fos = null;
        ShortBuffer buffer = ShortBuffer.allocate((1 << 20));
        try {
            fos = new FileOutputStream(mPCMFilePathTemp);
//        short[] pcm = new short[2304];//魔术数字吧？因为发现readFrame不管pcm多大，samples值是2304
            int tot_size_read = 0;
            while (true) {
//            int samples = mMp3Decoder.readFrame(pcm);
                short[] pcm = mMp3Decoder.readFrame();
                int samples = pcm.length;
                LogUtil.v(TAG, "Load mp3 samples " + samples);
                if (samples == 0 || pcm == null) {
                    break;
                } else {
                    tot_size_read += samples;
                    if (buffer.remaining() < samples) {
                        // Getting a rough estimate of the total size, allocate 20% more, and
                        // make sure to allocate at least 5MB more than the initial size.
                        int position = buffer.position();
                        int newSize = (int) ((position * (1.0 * mFileSize / tot_size_read)) * 1.2);
                        if (newSize - position < samples + 5 * (1 << 20)) {
                            newSize = position + samples + 5 * (1 << 20);
                        }
                        ShortBuffer newDecodedBytes = null;
                        // Try to allocate memory. If we are OOM, try to run the garbage collector.
                        int retry = 10;
                        while (retry > 0) {
                            try {
                                newDecodedBytes = ShortBuffer.allocate(newSize);
                                break;
                            } catch (OutOfMemoryError oome) {
                                // setting android:largeHeap="true" in <application> seem to help not
                                // reaching this section.
                                oome.printStackTrace();
                                retry--;
                            }
                        }
                        if (retry == 0) {
                            // Failed to allocate memory... Stop reading more data and finalize the
                            // instance with the data decoded so far.
                            break;
                        }
                        //ByteBuffer newDecodedBytes = ByteBuffer.allocate(newSize);
                        buffer.rewind();
                        newDecodedBytes.put(buffer);
                        buffer = newDecodedBytes;
                    }

                    buffer.put(pcm, 0, samples);
                    if (fos != null) {
                        try {
                            fos.write(shortsToBytes(pcm));
                            fos.flush();
                        } catch (IOException e) {
                            e.printStackTrace();
                            LogUtil.i(TAG, "jimwind write exception " + e.getMessage());
                        }
                    }
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return buffer;
    }

    public static byte[] shortsToBytes(short[] data) {
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
     * 转成波形
     * param buffer
     *
     * @return
     */
    private short[] convertToWaveform(ShortBuffer buffer) {
        if (shortsPerDrawSample == 0) {
            return null;
        }
        int numFrames = buffer.position() / shortsPerDrawSample;//getSamplesPerFrame();
        if (buffer.position() % shortsPerDrawSample != 0) {
            numFrames++;
        }
        LogUtil.i(TAG, "convertToWaveForm numFrames:" + numFrames + " buffer.position():" + buffer.position() + " shortsPerDrawSample:" + shortsPerDrawSample);
        buffer.rewind();
        int j;
        int gain, value;
        short[] frames = new short[numFrames];
        //总的short数
        for (int i = 0; i < numFrames; i++) {
            //取最大值
//            gain = -1;
//            for (j = 0; j < shortsPerDrawSample; j++) {
//                value = 0;
//                for (int k = 0; k < mAudioBean.getChannels(); k++) {
//                    if (buffer.remaining() > 0) {
//                        value += java.lang.Math.abs(buffer.get());
//                    }
//                }
//                value /= mAudioBean.getChannels();
//                if (gain < value) {
//                    gain = value;
//                }
//            }
            //取平均数
            value = 0;
            for (j = 0; j < shortsPerDrawSample; j++) {
                if (buffer.remaining() > 0) {
                    value += java.lang.Math.abs(buffer.get());
                }
            }
            gain = value / shortsPerDrawSample;

            frames[i] = (short) Math.sqrt(gain);  // here gain = sqrt(max value of 1st channel)...
        }
        return frames;
    }
}
