package com.qk.audiotool;

import android.os.AsyncTask;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 将背景音(bg)和录音(record)混音
 */
public class MixAudioTask extends AsyncTask<Void, Double, Boolean> {
    private String bg;
    private String record;
    private double dbBg = -1.0;
    private double dbRecord = -1.0;
    private long bgBegin, bgEnd, recordBegin, recordEnd;//millisecond

    /**
     * @param bg     PCM 文件
     * @param record PCM 文件
     */
    public MixAudioTask(String bg, String record) {
        this.bg = bg;
        this.record = record;
    }

    public void setDB(double dbBg, double dbRecord) {
        this.dbBg = dbBg;
        this.dbRecord = dbRecord;
    }

    /**
     * @param recordBegin millisecond
     * @param recordEnd   millisecond
     * @param bgBegin     millisecond
     * @param bgEnd       millisecond
     */
    public void setPosition(long recordBegin, long recordEnd, long bgBegin, long bgEnd) {
        this.recordBegin = recordBegin;
        this.recordEnd = recordEnd;
        this.bgBegin = bgBegin;
        this.bgEnd = bgEnd;
    }

    @Override
    protected Boolean doInBackground(Void... params) {

        if (true) {
            File[] rawAudioFiles = new File[2];

            rawAudioFiles[0] = new File(bg);
            rawAudioFiles[1] = new File(record);
            final String mixFilePath = Constant.getFullPath(Constant.mixPCM);
            File file = new File(mixFilePath);
            if (file.exists()) {
                file.delete();
            }
            try {
                MultiAudioMixer audioMixer = MultiAudioMixer.createAudioMixer();

                audioMixer.setOnAudioMixListener(new MultiAudioMixer.OnAudioMixListener() {

                    FileOutputStream fosRawMixAudio = new FileOutputStream(mixFilePath);

                    @Override
                    public void onMixing(byte[] mixBytes) throws IOException {
                        fosRawMixAudio.write(mixBytes);
                    }

                    @Override
                    public void onMixError(int errorCode) {
                        try {
                            if (fosRawMixAudio != null)
                                fosRawMixAudio.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onMixComplete() {
                        try {
                            if (fosRawMixAudio != null)
                                fosRawMixAudio.close();

                            if (mListener != null) {
                                mListener.onMixFinished();
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                });
                audioMixer.setDb(dbBg, dbRecord);
                audioMixer.mixAudios(record, bg, recordBegin, recordEnd, bgBegin, bgEnd);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

        }

        return true;
    }

    @Override
    protected void onPostExecute(Boolean result) {
        super.onPostExecute(result);

    }

    private Listener mListener;

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public interface Listener {
        void onMixFinished();
    }
}
