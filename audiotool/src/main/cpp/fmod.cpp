#include <jni.h>
#include "inc/fmod.h"
#include "inc/fmod.hpp"
#include <string.h>
#include <unistd.h>
#include <android/log.h>
#include <sys/time.h>

#define LOGI(FORMAT, ...) __android_log_print(ANDROID_LOG_INFO,"FILE_PATCH",FORMAT,__VA_ARGS__);
#define LOGE(FORMAT, ...) __android_log_print(ANDROID_LOG_ERROR,"FILE_PATCH",FORMAT,__VA_ARGS__);

/**
 *
 * mi.gao
 *
 */

bool playing = false;
bool stop = false;
const int MAX = 512;//最多128个音频同时播放
//加载音频文件
void _effect(jboolean j_export, JNIEnv *env, jobject jobj, jobjectArray j_file_paths,
             jfloatArray j_pitches, jfloatArray j_volumes,
             jfloatArray j_positions, jfloatArray j_delays, jfloatArray j_milliseconds,
             jstring j_output_file_path, jint j_play_millisecond_offset);
//播放多个音频文件 com.qk.audiotool.fmod
extern "C" JNIEXPORT void JNICALL Java_com_qk_audiotool_fmod_FmodUtils_effects
        (JNIEnv *env, jobject jobj, jobjectArray j_file_paths,
         jfloatArray j_pitches, jfloatArray j_volumes,
         jfloatArray j_positions, jfloatArray j_delays, jfloatArray j_milliseconds,
         jint j_play_millisecond_offset) {
    LOGI("jimwind fmod effects %s", "");
    _effect(false, env, jobj, j_file_paths, j_pitches, j_volumes, j_positions, j_delays,
            j_milliseconds, NULL, j_play_millisecond_offset);
}

//保存处理后的文件（多音频）
extern "C" JNIEXPORT void JNICALL Java_com_qk_audiotool_fmod_FmodUtils_saveEffects
        (JNIEnv *env, jobject jobj, jobjectArray j_file_paths, jfloatArray j_pitches,
         jfloatArray j_volumes,
         jfloatArray j_positions, jfloatArray j_delays, jfloatArray j_milliseconds,
         jstring j_output_file_path) {
    LOGI("jimwind fmod save effect file %s", "");

    _effect(true, env, jobj, j_file_paths, j_pitches, j_volumes, j_positions, j_delays,
            j_milliseconds, j_output_file_path, 0);
}
//停止播放
extern "C" JNIEXPORT void JNICALL Java_com_qk_audiotool_fmod_FmodUtils_stop
        (JNIEnv *env, jobject jobj) {
    LOGI("jimwind fmod stop %s", "");
    stop = true;
}
//判断是否正在播放
extern "C" JNIEXPORT bool JNICALL Java_com_qk_audiotool_fmod_FmodUtils_isPlaying
        (JNIEnv *env, jobject jobj) {
    return playing;
}

/** |0s       |10s      |20s      |30s
 *  ------------------------------      时间轴
 *     ---[-----------]-----            表示音频文件
 *     ---position = 3s 3000ms
 *  ------delay = 6s 6000ms
 *  ------------------millisecond = 18000ms
 *
 * @param j_export 是否导出文件
 * @param env
 * @param jobj
 * @param j_file_paths 混音全路径数组
 * @param j_pitches 声调
 * @param j_volumes 音量
 * @param j_positions 音频裁切位置
 * @param j_delays 音频混音开始位置
 * @param j_milliseconds 音频混音长度
 * @param j_output_file_path 导出的目标文件全路径
 * @param j_play_millisecond_offset 用于快进快退
 */
void _effect(jboolean j_export, JNIEnv *env, jobject jobj, jobjectArray j_file_paths,
             jfloatArray j_pitches, jfloatArray j_volumes,
             jfloatArray j_positions, jfloatArray j_delays, jfloatArray j_milliseconds,
             jstring j_output_file_path,
             jint j_play_millisecond_offset) {
    if (playing) {
        //建议，在调用播放之前，先调用isPlaying，判断是否正在播放，等stop完成后再调用当前方法
        LOGI("jimwind fmod effects playing %s", "");
        stop = true;
        return;
    }
    FMOD::System *system;
    FMOD::Sound *sounds[MAX];
    FMOD::Channel *channels[MAX];
    FMOD::DSP *dsps[MAX];
    FMOD::ChannelGroup *channelGroup;
    jint f_length = (*env).GetArrayLength(j_file_paths);
    {
        jint pitch_length = (*env).GetArrayLength(j_pitches);
        jint volume_length = (*env).GetArrayLength(j_volumes);
        jint position_length = (*env).GetArrayLength(j_positions);
        jint delay_length = (*env).GetArrayLength(j_delays);
        jint ms_length = (*env).GetArrayLength(j_milliseconds);
        if (f_length != pitch_length || f_length != volume_length
            || f_length != position_length || f_length != delay_length
            || f_length != ms_length) {
            LOGE("jimwind fmod params count is not same %s", "");
            return;
        }
    }
    int fileCount = f_length;
    if (fileCount > MAX) {
        LOGE("jimwind fmod support %d file play, yours is %d ", MAX, fileCount);
        return;
    } else {
        LOGE("jimwind fmod support %d file play, yours is %d ", MAX, fileCount);
    }
    int playMilliseconds = 0;
    timeval start, now;
    jclass cls = env->GetObjectClass(jobj);
    jmethodID callback = env->GetMethodID(cls, "progress", "(IIZ)V");

    jstring paths[MAX];
    const char *file_paths[MAX];
    jfloat *pitches = (*env).GetFloatArrayElements(j_pitches, NULL);
    jfloat *volumes = (*env).GetFloatArrayElements(j_volumes, NULL);
    jfloat *positions = (*env).GetFloatArrayElements(j_positions, NULL);
    jfloat *delays = (*env).GetFloatArrayElements(j_delays, NULL);
    jfloat *milliSeconds = (*env).GetFloatArrayElements(j_milliseconds, NULL);
    jfloat totalMilliseconds = 0;
    for (int i = 0; i < fileCount; i++) {
        paths[i] = static_cast<jstring>((*env).GetObjectArrayElement(j_file_paths, i));
        file_paths[i] = env->GetStringUTFChars(paths[i], NULL);
    }
    const char *file_o_path;
    if (j_export) {
        file_o_path = env->GetStringUTFChars(j_output_file_path, NULL);
    }
    unsigned int dsp_bufferlength;
    int sample = 44100; //
    int buffer_length = sample / 10;
    try {
        System_Create(&system);

        FMOD_SPEAKERMODE speakType = FMOD_SPEAKERMODE_DEFAULT;
        system->setSoftwareFormat(sample, speakType, 0);
        system->getSoftwareFormat(&sample, &speakType, 0);
        LOGI("jimwind fmod sample:%d", sample);

        if (j_export) {
            int dsp_numbuffers;
            system->getDSPBufferSize(&dsp_bufferlength, &dsp_numbuffers);
            LOGI("jimwind fmod - dsp_bufferlength %u, dsp_numbuffers %d, setDSPBufferSize %d",
                 dsp_bufferlength, dsp_numbuffers, buffer_length);
            //要在system->init之前设置
            system->setDSPBufferSize(buffer_length, dsp_numbuffers);//每次生成 buffer_length 数据

            system->getDSPBufferSize(&dsp_bufferlength, &dsp_numbuffers);
            LOGI("jimwind fmod = dsp_bufferlength %u, dsp_numbuffers %d",
                 dsp_bufferlength, dsp_numbuffers);

            system->setOutput(FMOD_OUTPUTTYPE_WAVWRITER_NRT);
            system->init(MAX, FMOD_INIT_STREAM_FROM_UPDATE, (void *) file_o_path);
            system->createChannelGroup("qingka_export", &channelGroup);
        } else {
            system->init(MAX, FMOD_INIT_NORMAL, NULL);
            system->createChannelGroup("qingka", &channelGroup);
        }

        unsigned long long dspclock;
        unsigned long long parentclock;
        unsigned int startdelay;
        unsigned int enddelay;
        channelGroup->getDSPClock(&dspclock, &parentclock);
        for (int i = 0; i < fileCount; i++) {
            system->createStream(file_paths[i], FMOD_DEFAULT, NULL, &sounds[i]);
            system->playSound(sounds[i], channelGroup, false, &channels[i]);

            float volume = volumes[i];
            volume = (volume > 10.0f) ? 10.0f : volume;
            volume = (volume < 1.0f) ? 1.0f : volume;
            channels[i]->setVolume(volume);
            LOGI("jimwind fmod dspclock %llu", dspclock);
            startdelay = sample * delays[i] / 1000 + dspclock;
            enddelay = sample * milliSeconds[i] / 1000 + dspclock;
            channels[i]->setDelay(startdelay, enddelay, 0);
            if ((unsigned int) positions[i] != 0) {
                channels[i]->setPosition(positions[i], FMOD_TIMEUNIT_MS);
            }

            if (pitches[i] != 1.0f) {
                system->createDSPByType(FMOD_DSP_TYPE_PITCHSHIFT, &dsps[i]);
                dsps[i]->setParameterFloat(FMOD_DSP_PITCHSHIFT_PITCH, pitches[i]);
                channels[i]->addDSP(0, dsps[i]);
            }
            if (milliSeconds[i] > totalMilliseconds) {
                totalMilliseconds = milliSeconds[i];
            }
            LOGI("jimwind fmod set volume    %f", volume);
            LOGI("jimwind fmod set pitch     %f", pitches[i]);
            LOGI("jimwind fmod set delay     %f", delays[i] / 1000 * sample);
            LOGI("jimwind fmod set position  %f", positions[i]);
            LOGI("jimwind fmod milliSeconds  %f", milliSeconds[i]);
            LOGI("jimwind fmod file          %s", file_paths[i]);
        }

    } catch (...) {
        goto end;
    }

    //取开始时间，后面用时间差计算使用了多少时间
    gettimeofday(&start, NULL);
    if (j_export) {
        stop = false;
        // 每隔多少导出一次
        int sleep_time = 10000;//microsecond;  = 10ms
        // 当前导出的总毫秒数
        int export_ms = 0;
        // 每次处理的音频毫秒数 用于进度
        float plus_ms = (1000.0f * (float)dsp_bufferlength / (float)sample); //0.1s -> 每0.01s处理0.1s的数据，即总时长的1/10
        while (true) {
            system->update();
            usleep(sleep_time);//+
            export_ms += plus_ms;//ms
            env->CallVoidMethod(jobj, callback, export_ms, (int) totalMilliseconds, true);
            if (export_ms >= totalMilliseconds) {
                gettimeofday(&now, NULL);
                int used_ms = ((now.tv_sec - start.tv_sec) * 1000 + (now.tv_usec - start.tv_usec) / 1000);
                LOGI("jimwind fmod export file 总共花费时间 %d ms", used_ms);
                goto end;
            }
            if (stop) {
                goto end;
            }
        }

    } else {
        system->update();
        playing = true;
        stop = false;
        while (playing) {
            usleep(50 * 1000);
            for (int i = 0; i < fileCount; i++) {
                if (playMilliseconds + j_play_millisecond_offset >= milliSeconds[i]) {
                    channels[i]->stop();
                }
            }
            gettimeofday(&now, NULL);
            playMilliseconds = ((now.tv_sec - start.tv_sec) * 1000 +
                                (now.tv_usec - start.tv_usec) / 1000);
            channelGroup->isPlaying(&playing);
            env->CallVoidMethod(jobj, callback, playMilliseconds + j_play_millisecond_offset,
                                (int) totalMilliseconds, true);
            if (playMilliseconds + j_play_millisecond_offset >= totalMilliseconds) {
                break;
            }
            if (stop) {
                break;
            }
        }
    }
    goto end;
    end:
    for (int i = 0; i < fileCount; i++) {
        env->ReleaseStringUTFChars(paths[i], file_paths[i]);
        sounds[i]->release();
    }
    if (j_export) {
        env->ReleaseStringUTFChars(j_output_file_path, file_o_path);
        env->CallVoidMethod(jobj, callback, playMilliseconds + j_play_millisecond_offset,
                            (int) totalMilliseconds, false);
    }
    channelGroup->release();
    system->close();
    system->release();
    stop = false;
    playing = false;
}

jfloatArray asFloatArray(JNIEnv *env, float *buf, int size) {
    jfloatArray array = NULL;
    array = env->NewFloatArray(size);
    if (buf != NULL) {
        env->SetFloatArrayRegion(array, 0, size, buf);
    } else {
        LOGE("jimwind fmod play asFloatArray buf is NULL %s", "");
    }
    return array;
}
//播放单个音频文件 在线播放
extern "C" JNIEXPORT void JNICALL Java_com_qk_audiotool_fmod_FmodUtils_play
        (JNIEnv *env, jobject jobj, jstring j_url, jfloat j_position) {
    if (playing) {
        stop = true;
        LOGE("jimwind fmod is playing %s", "");
        return;
    }
    //路径转换
    const char *url = env->GetStringUTFChars(j_url, NULL);
    FMOD::System *system;
    FMOD::Sound *sound;
    FMOD::Channel *channel = nullptr;
    FMOD::DSP *dsp;
    FMOD_OPENSTATE openstate = FMOD_OPENSTATE_READY;
//    const int SIZE = 1024 ;
//    char str[SIZE] = {};
//    unsigned  int dataLength = 0;
    unsigned int pos = 0;
    jclass cls = env->GetObjectClass(jobj);
    jmethodID callback = env->GetMethodID(cls, "fft", "([F[FIIZ)V");
    try {
        //初始化
        System_Create(&system);
        system->init(1, FMOD_INIT_NORMAL, NULL);
        system->setStreamBufferSize(64 * 1024, FMOD_TIMEUNIT_RAWBYTES);
        FMOD_CREATESOUNDEXINFO exinfo;
        memset(&exinfo, 0, sizeof(FMOD_CREATESOUNDEXINFO));
        exinfo.cbsize = sizeof(FMOD_CREATESOUNDEXINFO);
        exinfo.suggestedsoundtype = FMOD_SOUND_TYPE_MPEG;
        exinfo.filebuffersize = 1024 *
                                32; /* Increase the default file chunk size to handle seeking inside large playlist files that may be over 2kb. */
//        url = "/storage/emulated/0/Music/承认吧 你已经不需要爱情了.mp3";
        LOGI("jimwind fmod play url %s", url);
        system->createSound(url, FMOD_CREATESTREAM | FMOD_NONBLOCKING, &exinfo, &sound);
//        sound->setLoopCount(50);
        system->createDSPByType(FMOD_DSP_TYPE::FMOD_DSP_TYPE_FFT, &dsp);
        dsp->setParameterInt(FMOD_DSP_FFT_WINDOWTYPE, FMOD_DSP_FFT_WINDOW_HAMMING);
        dsp->setParameterInt(FMOD_DSP_FFT_WINDOWSIZE, 512);
//        dsp->setParameterInt(FMOD_DSP_FFT_WINDOWTYPE, FMOD_DSP_FFT_WINDOW_RECT);
//        dsp->setParameterInt(FMOD_DSP_FFT_WINDOWSIZE, 1024);
    } catch (...) {
        LOGE("%s", "jimwind effect error");
        goto end;
    }

    stop = false;
    playing = true;
    while (playing) {
        unsigned int percent = 0;
        bool starving = false;
        usleep(33 * 1000);
        if (stop) {
            goto end;
        }
        FMOD_RESULT result = sound->getOpenState(&openstate, &percent, &starving, 0);
//        LOGI("jimwind fmod getOpenState result %d, openstate %d", result, openstate);
        if (channel) {
//            LOGI("jimwind fmod play 1 %s", "");
            channel->setMute(starving);
//            LOGI("jimwind fmod play 2 %s", "");
            channel->getPosition(&pos, FMOD_TIMEUNIT_MS);
        }
//        LOGI("jimwind fmod play 3 %s", "");
        if (openstate == FMOD_OPENSTATE_CONNECTING) {
            playing = true;
            LOGI("jimwind fmod play FMOD_OPENSTATE_CONNECTING %s", url);
        } else if (openstate == FMOD_OPENSTATE_BUFFERING) {
            playing = true;
            LOGI("jimwind fmod play FMOD_OPENSTATE_BUFFERING %s", url);
        } else if (openstate == FMOD_OPENSTATE_READY) {
            system->playSound(sound, 0, false, &channel);
            if (j_position > 0) {
                channel->setPosition(j_position, FMOD_TIMEUNIT_MS);
            }
            channel->addDSP(0, dsp);
        } else if (openstate == FMOD_OPENSTATE_PLAYING) {
            channel->isPlaying(&playing);
            FMOD_DSP_PARAMETER_FFT *fft = nullptr;
            dsp->getParameterData(FMOD_DSP_FFT_SPECTRUMDATA, (void **) &fft, 0, 0, 0);
//            LOGI("jimwind fmod chanels:%d length:%d ", fft->numchannels, fft->length);
//            LOGI("jimwind fmod percent:%d pos:%d ", percent, pos);
            //https://blog.csdn.net/tobearc/article/details/88371883


            if (playing) {
                jfloatArray left = asFloatArray(env, fft->spectrum[0], fft->length);
                jfloatArray right = asFloatArray(env, fft->spectrum[1], fft->length);
                if (left != NULL && right != NULL) {
                    if (fft->numchannels == 1) {
                        env->CallVoidMethod(jobj, callback, left, left, fft->length, pos, true);
                    } else {
                        env->CallVoidMethod(jobj, callback, left, right, fft->length, pos, true);
                    }
//                    env->CallVoidMethod(jobj, callback, left, right, fft->length, pos, true);
                }
                env->DeleteLocalRef(left);
                env->DeleteLocalRef(right);
            } else {
                env->CallVoidMethod(jobj, callback, NULL, NULL, 0, 0, false);
            }
        } else if (openstate == FMOD_OPENSTATE_ERROR) {
            LOGE("jimwind fmod play FMOD_OPENSTATE_ERROR openstate result %d", result);
            break;
        } else {
            LOGI("jimwind fmod play openstate %d", openstate);
        }
        if (openstate != FMOD_OPENSTATE_PLAYING) {
            LOGI("%s openstate: %d, playing:%d, pos:%d", "jimwind fmod", openstate, playing, pos);
        }
        if (stop) {
            break;
        }
    }

    goto end;

    end:
    env->ReleaseStringUTFChars(j_url, url);
    sound->release();
    system->close();
    channel->stop();
    system->release();
    stop = false;
    playing = false;
    channel = nullptr;
    LOGI("jimwind fmod release finished %s", "");
}

