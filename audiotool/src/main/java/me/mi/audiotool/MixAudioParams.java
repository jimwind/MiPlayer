package me.mi.audiotool;

import me.mi.audiotool.utils.LogUtil;

import java.io.Serializable;

public class MixAudioParams implements Serializable {
    private String recordPCM;//录音文件 pcm    注意，是全路径
    private int totalRecordMillisecond;

    /**
     * 录音生成的pcm文件：全路径
     *
     * @return
     */
    public String getRecordPCM() {
        return recordPCM;
    }

    /**
     * @param recordPCM 全路径
     */
    public void setRecordPCM(String recordPCM) {
        LogUtil.i("jimwind", "[param] set record pcm");
        this.recordPCM = recordPCM;
    }

    public int getTotalRecordMillisecond() {
        return totalRecordMillisecond;
    }

    public void setTotalRecordMillisecond(int totalRecordMillisecond) {
        this.totalRecordMillisecond = totalRecordMillisecond;
    }
}
