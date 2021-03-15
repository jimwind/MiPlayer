package me.mi.audiotool.soundtouch;


import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

/**
 * 处理音调 -12 ~ 12 低八度至高八度 mi.gao 2019/10/21
 */
public class AudioPitchProcess {
    private Context context;
    private String mInFile;
    private String mOutFile;

    public AudioPitchProcess(Context context, String inFile, String outFile) {
        this.context = context;
        mInFile = inFile;
        mOutFile = outFile;
    }

    /// process a file with SoundTouch. Do the processing using a background processing
    /// task to avoid hanging of the UI
    public void process(float pitch) {
        try {
            ProcessTask task = new ProcessTask();
            ProcessTask.Parameters params = task.new Parameters();
            // parse processing parameters
            params.inFileName = mInFile;
            params.outFileName = mOutFile;
            params.tempo = 0.01f * Float.parseFloat("100");
            params.pitch = pitch;

            // update UI about status
//            appendToConsole("Process audio file :" + params.inFileName +" => " + params.outFileName);
//            appendToConsole("Tempo = " + params.tempo);
//            appendToConsole("Pitch adjust = " + params.pitch);

//            Toast.makeText(context, "Starting to process file " + params.inFileName + "...", Toast.LENGTH_SHORT).show();

            // start SoundTouch processing in a background thread
            task.execute(params);
//			task.doSoundTouchProcessing(params);	// this would run processing in main thread

        } catch (Exception exp) {
            Log.e("jimwind", "sound touch process pitch " + exp.toString());
            exp.printStackTrace();
        }

    }

    /// Helper class that will execute the SoundTouch processing. As the processing may take
    /// some time, run it in background thread to avoid hanging of the UI.
    private class ProcessTask extends AsyncTask<ProcessTask.Parameters, Integer, Long> {
        /// Helper class to store the SoundTouch file processing parameters
        public final class Parameters {
            String inFileName;
            String outFileName;
            float tempo;
            float pitch;
        }


        /// Function that does the SoundTouch processing
        public final long doSoundTouchProcessing(Parameters params) {
            SoundTouch st = new SoundTouch();
            st.setTempo(params.tempo);
            st.setPitchSemiTones(params.pitch);
            Log.i("jimwind", "SoundTouch process file " + params.inFileName);
            long startTime = System.currentTimeMillis();
            int res = st.processFile(params.inFileName, params.outFileName);
            long endTime = System.currentTimeMillis();
            float duration = (endTime - startTime) * 0.001f;

            Log.i("jimwind", "SoundTouch process file done, duration = " + duration);
//            appendToConsole("Processing done, duration " + duration + " sec.");
            if (res != 0) {
                String err = SoundTouch.getErrorString();
                Log.e("jimwind", "pitch process failure " + err);
//                appendToConsole("Failure: " + err);
                return -1L;
            }

            // Play file if so is desirable
//            if (checkBoxPlay.isChecked())
//            {
//                playWavFile(params.outFileName);
//            }
            if (listener != null) {
                listener.done();
            }
            return 0L;
        }


        /// Overloaded function that get called by the system to perform the background processing
        @Override
        protected Long doInBackground(Parameters... aparams) {
            return doSoundTouchProcessing(aparams[0]);
        }

    }

    public interface Listener {
        void done();
    }

    private Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }
}
