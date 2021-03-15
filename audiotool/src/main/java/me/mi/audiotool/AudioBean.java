package me.mi.audiotool;


import android.util.Log;

import java.io.Serializable;
import java.util.ArrayList;

public class AudioBean implements Serializable {
    private static final long serialVersionUID = 4594031209196842863L;
    private String name;
    private String path;
    private boolean record;
    private ArrayList<Short> frames;
    private float seconds;
    private int sampleRate;
    private int channels;

//    private int startMillisecond;
//    private int endMillisecond;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ArrayList<Short> getFrames() {
        return frames;
    }

    public void setFrames(short[] frames) {
        if (frames == null) {
            return;
        }
        this.frames = new ArrayList<>();
        for (int i = 0; i < frames.length; i++) {
            this.frames.add(frames[i]);
        }
    }

    public void setFrames(ArrayList<Short> frames) {
        this.frames = frames;
    }

    public float getSeconds() {
        return seconds;
    }

    public void setSeconds(float seconds) {
        Log.i("jimwind", "seconds " + seconds);
        this.seconds = seconds;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
    }

    public int getChannels() {
        return channels;
    }

    public void setChannels(int channels) {
        this.channels = channels;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public boolean isRecord() {
        return record;
    }

    public void setRecord(boolean record) {
        this.record = record;
    }

//    public int getStartMillisecond() {
//        return startMillisecond;
//    }
//
//    public void setStartMillisecond(int startMillisecond) {
//        this.startMillisecond = startMillisecond;
//    }
//
//    public int getEndMillisecond() {
//        return endMillisecond;
//    }
//
//    public void setEndMillisecond(int endMillisecond) {
//        this.endMillisecond = endMillisecond;
//    }

    public void print() {
        Log.i("jimwind", "AudioBean name:" + name + " seconds:" + seconds + " sampleRate:" + sampleRate + " channels:" + channels);
    }
}