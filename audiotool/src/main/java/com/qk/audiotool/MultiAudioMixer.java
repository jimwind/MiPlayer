package com.qk.audiotool;


import com.qk.audiotool.utils.LogUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public abstract class MultiAudioMixer {
    private OnAudioMixListener mOnAudioMixListener;
    private float dbRecord = 1.0f;
    private float dbBg = 1.0f;

    public static MultiAudioMixer createAudioMixer() {
        return new AverageAudioMixer();
    }

    public void setOnAudioMixListener(OnAudioMixListener l) {
        this.mOnAudioMixListener = l;
    }


    //-96 ... 0;
    public void setDb(double dbBg, double dbRecord) {
        LogUtil.i("jimwind", "MultiAudioMixer bg/record source " + dbBg + "/" + dbRecord);
        this.dbBg = AudioUtil.computeDB(dbBg);//(float) Math.pow(10, dbBg/20.0);
        this.dbRecord = AudioUtil.computeDB(dbRecord);//(float)Math.pow(10, dbRecord/20);
        LogUtil.i("jimwind", "MultiAudioMixer bg/record pow " + this.dbBg + "/" + this.dbRecord);
    }

    //pData原始音频byte数组，nLen原始音频byte数组长度，data2转换后新音频byte数组，nBitsPerSample每个采样用16bit存储，multiple表示Math.pow()返回值
    public int amplifyPCMData(byte[] pData, int nLen, byte[] data2, int nBitsPerSample, float multiple) {
        int nCur = 0;
        if (16 == nBitsPerSample) {
            while (nCur < nLen) {
                short volum = getShort(pData, nCur);

                volum = (short) (volum * multiple);

                data2[nCur] = (byte) (volum & 0xFF);
                data2[nCur + 1] = (byte) ((volum >> 8) & 0xFF);
                nCur += 2;
            }

        }
        return 0;
    }

    private short getShort(byte[] data, int start) {
        return (short) ((data[start] & 0xFF) | (data[start + 1] << 8));
    }

    private void read(FileInputStream fis, float db, long max) throws IOException {
        int readSize = 0;
        int total = 0;

        byte[] buffer = new byte[1024];
        byte[][] allAudioBytes = new byte[2][];
        while (true) {
            readSize = fis.read(buffer, 0, buffer.length);
            if (readSize == -1) {
                break;
            }
            total += readSize;
            allAudioBytes[0] = new byte[readSize];
            amplifyPCMData(buffer, readSize, allAudioBytes[0], 16, db);

            allAudioBytes[1] = new byte[readSize];

            byte[] mixBytes = mixRawAudioBytes(allAudioBytes);
            if (mixBytes != null && mOnAudioMixListener != null) {
                mOnAudioMixListener.onMixing(mixBytes);
            }
            if (max != 0 && total >= max) {
                LogUtil.i("jimwind", "read total " + total + " max " + max);
                break;
            }
        }
    }

    /**
     * @param record
     * @param bg
     * @param recordBegin millisecond
     * @param recordEnd   millisecond
     * @param bgBegin     millisecond
     * @param bgEnd       millisecond
     */
    public void mixAudios(String record, String bg,
                          long recordBegin, long recordEnd, long bgBegin, long bgEnd) {

        byte[][] allAudioBytes = new byte[2][];

        int readSize = 0;

        try {
            FileInputStream fisRecord = new FileInputStream(record);
            FileInputStream fisBg = new FileInputStream(bg);
            byte[] buffer_record = new byte[1024];
            byte[] buffer_bg = new byte[1024];
            //拼接 整段录音 都在背景音之前 或 整段背景音都在录音之前
            if (recordEnd <= bgBegin || bgEnd <= recordBegin) {
                if (recordEnd <= bgBegin) {
                    read(fisRecord, dbRecord, Long.MAX_VALUE);
                    read(fisBg, dbBg, Long.MAX_VALUE);
                } else {
                    read(fisBg, dbBg, Long.MAX_VALUE);
                    read(fisRecord, dbRecord, Long.MAX_VALUE);
                }
            } else {
                long record_bg_begin_delta = (recordBegin - bgBegin) * Constant.getBytesPerMillisecond();
                LogUtil.i("jimwind", "audio mixer record_bg_begin_delta " + record_bg_begin_delta);
                long record_bg_end_delta = (recordEnd - bgEnd) * Constant.getBytesPerMillisecond();
                LogUtil.i("jimwind", "audio mixer record_bg_end_delta " + record_bg_end_delta);
                //开始时间 record > bg
                if (record_bg_begin_delta > 0) {
                    //先读背景音, 不混音
                    read(fisBg, dbBg, record_bg_begin_delta);
                }
                //开始时间 record < bg
                else if (record_bg_begin_delta < 0) {
                    //先读录音, 不混音
                    read(fisRecord, dbRecord, Math.abs(record_bg_begin_delta));
                }

                //结束时间 record > bg
                if (record_bg_end_delta > 0) {
                    while (true) {
                        //优先读背景音，背景读完跳出循环，再读录音
                        //---------------- bg ----------------
                        readSize = fisBg.read(buffer_bg, 0, buffer_bg.length);
                        if (readSize == -1) {
                            break;
                        }
//                        allAudioBytes[1] = Arrays.copyOf(buffer_bg, buffer_bg.length);
                        allAudioBytes[1] = new byte[buffer_bg.length];
                        amplifyPCMData(buffer_bg, buffer_bg.length, allAudioBytes[1], 16, dbBg);

                        //---------------- record ----------------
                        readSize = fisRecord.read(buffer_record, 0, buffer_record.length);
                        if (readSize == -1) {
                            break;
                        }
//                        allAudioBytes[0] = Arrays.copyOf(buffer_record, buffer_record.length);
                        allAudioBytes[0] = new byte[buffer_record.length];
                        amplifyPCMData(buffer_record, buffer_record.length, allAudioBytes[0], 16, dbRecord);
                        //混音
                        byte[] mixBytes = mixRawAudioBytes(allAudioBytes);
                        if (mixBytes != null && mOnAudioMixListener != null) {
                            mOnAudioMixListener.onMixing(mixBytes);
                        }
                    }
                    read(fisRecord, dbRecord, Long.MAX_VALUE);
                }
                //结束时间 record < bg
                else {
                    while (true) {
                        //优先读录音，录音读完跳出循环，后读背景
                        //---------------- record ----------------
                        readSize = fisRecord.read(buffer_record, 0, buffer_record.length);
                        if (readSize == -1) {
                            break;
                        }
//                        allAudioBytes[0] = Arrays.copyOf(buffer_record, buffer_record.length);
                        allAudioBytes[0] = new byte[buffer_record.length];
                        amplifyPCMData(buffer_record, buffer_record.length, allAudioBytes[0], 16, dbRecord);

                        //---------------- bg ----------------
                        readSize = fisBg.read(buffer_bg, 0, buffer_bg.length);
                        if (readSize == -1) {
                            break;
                        }
//                        allAudioBytes[1] = Arrays.copyOf(buffer_bg, buffer_bg.length);
                        allAudioBytes[1] = new byte[buffer_bg.length];
                        amplifyPCMData(buffer_bg, buffer_bg.length, allAudioBytes[1], 16, dbBg);

                        //混音
                        byte[] mixBytes = mixRawAudioBytes(allAudioBytes);
                        if (mixBytes != null && mOnAudioMixListener != null) {
                            mOnAudioMixListener.onMixing(mixBytes);
                        }
                    }
                    read(fisBg, dbBg, Long.MAX_VALUE);
                }

            }
            fisBg.close();
            fisRecord.close();
            if (mOnAudioMixListener != null)
                mOnAudioMixListener.onMixComplete();
        } catch (IOException e) {
            e.printStackTrace();
            if (mOnAudioMixListener != null)
                mOnAudioMixListener.onMixError(1);
        } finally {

        }
    }

    public void mixAudios(File[] rawAudioFiles) {

        final int fileSize = rawAudioFiles.length;

        FileInputStream[] audioFileStreams = new FileInputStream[fileSize];
        File audioFile = null;

        FileInputStream inputStream;
        byte[][] allAudioBytes = new byte[fileSize][];
        boolean[] streamDoneArray = new boolean[fileSize];
        byte[] buffer = new byte[512];
        int offset;

        try {

            for (int fileIndex = 0; fileIndex < fileSize; ++fileIndex) {
                audioFile = rawAudioFiles[fileIndex];
                audioFileStreams[fileIndex] = new FileInputStream(audioFile);
            }

            while (true) {

                for (int streamIndex = 0; streamIndex < fileSize; ++streamIndex) {
                    //每个文件都读取512 byte 混音
                    inputStream = audioFileStreams[streamIndex];
                    if (!streamDoneArray[streamIndex] && (offset = inputStream.read(buffer)) != -1) {
                        //allAudioBytes[streamIndex] = Arrays.copyOf(buffer, buffer.length);
                        if (streamIndex == 0) {
                            allAudioBytes[streamIndex] = new byte[buffer.length];
                            amplifyPCMData(buffer, buffer.length, allAudioBytes[streamIndex], 16, dbBg);
                        } else if (streamIndex == 1) {
                            allAudioBytes[streamIndex] = new byte[buffer.length];
                            amplifyPCMData(buffer, buffer.length, allAudioBytes[streamIndex], 16, dbRecord);

                        }
//                        if(streamIndex == 0 && volumeBg != 1.0f){
//                            for(int i=0; i<allAudioBytes[streamIndex].length; i++){
//                                allAudioBytes[streamIndex][i] = (byte) ((float)allAudioBytes[streamIndex][i] * volumeBg);
//                            }
//                        } else if(streamIndex == 1 && volumeRecord != 1.0f){
//                            for(int i=0; i<allAudioBytes[streamIndex].length; i++){
//                                allAudioBytes[streamIndex][i] = (byte) ((float)allAudioBytes[streamIndex][i] * volumeRecord);
//                            }
//                        }

                    } else {
                        streamDoneArray[streamIndex] = true;
                        allAudioBytes[streamIndex] = new byte[512];
                    }
                }

                byte[] mixBytes = mixRawAudioBytes(allAudioBytes);

                if (mixBytes != null && mOnAudioMixListener != null) {
                    mOnAudioMixListener.onMixing(mixBytes);
                }

                boolean done = true;
                for (boolean streamEnd : streamDoneArray) {
                    if (!streamEnd) {
                        done = false;
                    }
                }

                if (done) {
                    if (mOnAudioMixListener != null)
                        mOnAudioMixListener.onMixComplete();
                    break;
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
            if (mOnAudioMixListener != null)
                mOnAudioMixListener.onMixError(1);
        } finally {
            try {
                for (FileInputStream in : audioFileStreams) {
                    if (in != null)
                        in.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    abstract byte[] mixRawAudioBytes(byte[][] data);

    public interface OnAudioMixListener {
        /**
         * invoke when mixing, if you want to stop the mixing process, you can throw an AudioMixException
         *
         * @param mixBytes
         * @throws AudioMixException
         */
        void onMixing(byte[] mixBytes) throws IOException;

        void onMixError(int errorCode);

        /**
         * invoke when mix success
         */
        void onMixComplete();
    }

    public static class AudioMixException extends IOException {
        private static final long serialVersionUID = -1344782236320621800L;

        public AudioMixException(String msg) {
            super(msg);
        }
    }

    /**
     * 平均值算法
     *
     * @author Darcy
     */
    private static class AverageAudioMixer extends MultiAudioMixer {

        @Override
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
}
