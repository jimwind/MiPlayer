package com.qk.audiotool;

import com.qk.audiotool.jssrc_resample.SSRC;
import com.qk.audiotool.utils.FileUtil;
import com.qk.audiotool.utils.LogUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class AudioDecodeTask extends Thread {

    public AudioDecodeTask(String dir, String mp3, String pcm) {
        mDir = dir;
        mp3File = mDir + mp3;
        pcmFile = mDir + pcm;
        pcmTempFile = mDir + "temp_" + pcm;
        pcmTempFile2 = mDir + "temp2_" + pcm;

    }

    @Override
    public void run() {
        super.run();
        File mp3 = new File(mp3File);
        if (!mp3.exists()) {
            LogUtil.e("jimwind", "no mp3 file need decode " + mp3File);
            return;
        }
        File decodeFile = new File(pcmFile);
        LogUtil.i("jimwind", "decode record " + pcmFile);
        if (decodeFile.exists()) {
            decodeFile.delete();
            LogUtil.w("jimwind", "文件已经存在，删除！重新解码..." + pcmFile);
        }


        LogUtil.i("jimwind", "createDefualtDecoder start " + mp3File);
        AudioDecoder audioDec = AudioDecoder.createDefualtDecoder(mp3File);

        try {
            audioDec.setOnAudioDecoderListener(new AudioDecoder.OnAudioDecoderListener() {
                @Override
                public void onDecode(byte[] decodedBytes, double progress) {
//                      LogUtil.e("jimwind", "decode progress:" + progress);
                }
            });
            LogUtil.i("jimwind", "audioDec.decodeToFile start ");
            AudioDecoder.RawAudioInfo rawAudioInfo = audioDec.decodeToFile(pcmTempFile);
            LogUtil.i("jimwind", "音频采样率:" + rawAudioInfo.sampleRate);
            LogUtil.i("jimwind", "音频通道数:" + rawAudioInfo.channel);
            LogUtil.i("jimwind", "音频长度：" + rawAudioInfo.duration);
            LogUtil.i("jimwind", "mp3解码成pcm成功");
            //双声道解成单声道数据
            if (rawAudioInfo.channel == 2) {
                FileInputStream fin = new FileInputStream(pcmTempFile);
                FileOutputStream fos = new FileOutputStream(pcmTempFile2);
                byte[] buffer = new byte[4096];
                while (fin.read(buffer, 0, buffer.length) > 0) {
                    fos.write(AudioUtil.splitStereoPcm(buffer));
                }
                fin.close();
                fos.close();
                FileUtil.delFile(pcmTempFile);
                new File(pcmTempFile2).renameTo(new File(pcmTempFile));
                LogUtil.i("jimwind", "pcm取单通道成功");
            }
            if (rawAudioInfo.sampleRate != Constant.DEFAULT_SAMPLING_RATE) {
                FileInputStream fileInputStream = new FileInputStream(pcmTempFile);
                FileOutputStream fileOutputStream = new FileOutputStream(pcmFile);
                new SSRC(fileInputStream, fileOutputStream,
                        (int) rawAudioInfo.sampleRate,
                        Constant.DEFAULT_SAMPLING_RATE,
                        2,
                        2,
                        Constant.CHANNEL,
                        Integer.MAX_VALUE, 0, 0, true);
                LogUtil.i("jimwind", "不是" + Constant.DEFAULT_SAMPLING_RATE + "的mp3解码成pcm成功");

                FileUtil.delFile(pcmTempFile);
            } else {
                new File(pcmTempFile).renameTo(new File(pcmFile));
            }
            if (mListener != null) {
                mListener.onDecodeFinished(true);
            }
        } catch (IOException e) {
            LogUtil.e("jimwind", "decode exception " + e.toString());
            if (mListener != null) {
                mListener.onDecodeFinished(false);
            }
            e.printStackTrace();
        }

    }

    private String mDir;
    private String mp3File;
    private String pcmFile;
    private String pcmTempFile;
    private String pcmTempFile2;

    //    @Override
//    protected Boolean doInBackground(String... params) {
//        mDir = params[0];
//        mp3File     = mDir + params[1];
//        pcmFile     = mDir + params[2];
//        pcmTempFile = mDir +"temp_"+params[2];
//        pcmTempFile2= mDir +"temp2_"+params[2];
//        File decodeFile = new File(pcmFile);
//        LogUtil.i("jimwind", "decode record " + pcmFile);
//        if (decodeFile.exists()) {
//            decodeFile.delete();
//            LogUtil.w("jimwind", "文件已经存在，删除！重新解码..."+pcmFile);
////            publishProgress(1.0);
//        }
//
//        {
//            LogUtil.i("jimwind", "createDefualtDecoder start "+mp3File);
//            AudioDecoder audioDec = AudioDecoder.createDefualtDecoder(mp3File);
//
//            try {
//                audioDec.setOnAudioDecoderListener(new AudioDecoder.OnAudioDecoderListener() {
//                    @Override
//                    public void onDecode(byte[] decodedBytes, double progress) {
////                      LogUtil.e("jimwind", "decode progress:" + progress);
//                        publishProgress(progress);
//                    }
//                });
//                LogUtil.i("jimwind", "audioDec.decodeToFile start ");
//                AudioDecoder.RawAudioInfo rawAudioInfo = audioDec.decodeToFile(pcmTempFile);
//                LogUtil.i("jimwind", "音频采样率:"+rawAudioInfo.sampleRate);
//                LogUtil.i("jimwind", "音频通道数:"+rawAudioInfo.channel);
//                LogUtil.i("jimwind", "音频长度："+rawAudioInfo.duration);
//                LogUtil.i("jimwind", "mp3解码成pcm成功");
//                //双声道解成单声道数据
//                if(rawAudioInfo.channel == 2){
//                    FileInputStream fin = new FileInputStream(pcmTempFile);
//                    FileOutputStream fos = new FileOutputStream(pcmTempFile2);
//                    byte[] buffer = new byte[4096];
//                    while (fin.read(buffer, 0, buffer.length) > 0){
//                        fos.write(AudioUtil.splitStereoPcm(buffer));
//                    }
//                    fin.close();
//                    fos.close();
//                    FileUtil.delFile(pcmTempFile);
//                    new File(pcmTempFile2).renameTo(new File(pcmTempFile));
//                    LogUtil.i("jimwind", "pcm取单通道成功");
//                }
//                if (rawAudioInfo.sampleRate != Constant.DEFAULT_SAMPLING_RATE) {
//                    FileInputStream fileInputStream = new FileInputStream(pcmTempFile);
//                    FileOutputStream fileOutputStream = new FileOutputStream(pcmFile);
//                    new SSRC(fileInputStream, fileOutputStream,
//                            (int) rawAudioInfo.sampleRate,
//                            Constant.DEFAULT_SAMPLING_RATE,
//                            2,
//                            2,
//                            Constant.CHANNEL,
//                            Integer.MAX_VALUE, 0, 0, true);
//                    LogUtil.i("jimwind", "不是"+Constant.DEFAULT_SAMPLING_RATE+"的mp3解码成pcm成功");
//
//                    FileUtil.delFile(pcmTempFile);
//                } else {
//                    new File(pcmTempFile).renameTo(new File(pcmFile));
//                }
//                if(mListener != null){
//                    mListener.onDecodeFinished(true);
//                }
//                return true;
//            } catch (IOException e) {
//                LogUtil.e("jimwind", "decode exception "+e.toString());
//                e.printStackTrace();
//                return false;
//            }
//        }
//    }
//
//    @Override
//    protected void onProgressUpdate(Double... values) {
//        super.onProgressUpdate(values);
//    }
//
//    @Override
//    protected void onPostExecute(Boolean result) {
//        super.onPostExecute(result);
//        LogUtil.i("jimwind", "AudioDecodeTask "+AudioDecodeTask.this);
//        LogUtil.i("jimwind", "AudioDecodeTask onPostExecute "+result);
//
//    }
    public interface Listener {
        void onDecodeFinished(boolean result);
    }

    private Listener mListener;

    public void setListener(Listener listener) {
        mListener = listener;
    }
}

