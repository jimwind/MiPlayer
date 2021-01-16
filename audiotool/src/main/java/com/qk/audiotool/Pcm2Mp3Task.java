package com.qk.audiotool;

import android.os.AsyncTask;

import com.qk.audiotool.lame.LameUtil;
import com.qk.audiotool.utils.LogUtil;

//import me.mi.mp3.util.LameUtil;


public class Pcm2Mp3Task extends AsyncTask<String, Integer, Boolean> {
    private String rawFile = "";
    private String mp3File = "";

    @Override
    protected Boolean doInBackground(String... params) {
        rawFile = params[0];
        mp3File = params[1];
        LogUtil.i("jimwind", "mp3 file is " + mp3File);
//        FLameUtils lameUtils = new FLameUtils(1, Constant.DEFAULT_SAMPLING_RATE, 96);
//        return  lameUtils.raw2mp3(rawFile, mp3File);
        LameUtil.init(Constant.DEFAULT_SAMPLING_RATE,
                1,
                Constant.DEFAULT_SAMPLING_RATE,
                128, 7);
        LameUtil.encodeFile(rawFile, mp3File);
        return true;
    }

    @Override
    protected void onPostExecute(Boolean result) {
    }
}
