#include <jni.h>
#include <string>
#include <unistd.h>
#include "android/log.h"

#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "ffmpeg-cmd", __VA_ARGS__)
#define LOGI(FORMAT, ...) __android_log_print(ANDROID_LOG_INFO,"ffmpeg-cmd",FORMAT,##__VA_ARGS__);
#define LOGE(FORMAT, ...) __android_log_print(ANDROID_LOG_ERROR,"ffmpeg-cmd",FORMAT,##__VA_ARGS__);
extern "C" {
#include "ffmpeg/ffmpeg.h"
#include "ffmpeg/libavcodec/jni.h"
#include "ffmpeg/libavformat/avformat.h"
#include "ffmpeg/libavutil/opt.h"
}

int rec_audio(jbyte *pArray, jsize size);

void stop_audio();

extern "C" JNIEXPORT jint JNICALL
Java_com_qk_audiotool_ffmpeg_FFmpegCmd_run(JNIEnv *env, jclass type, jint cmdLen,
                                           jobjectArray cmd) {
    //set java vm
    JavaVM *jvm = NULL;
    env->GetJavaVM(&jvm);
    av_jni_set_java_vm(jvm, NULL);

    char *argCmd[cmdLen];
    jstring buf[cmdLen];

    for (int i = 0; i < cmdLen; ++i) {
        buf[i] = static_cast<jstring>(env->GetObjectArrayElement(cmd, i));
        char *string = const_cast<char *>(env->GetStringUTFChars(buf[i], JNI_FALSE));
        argCmd[i] = string;
        LOGD("argCmd=%s", argCmd[i]);
    }

    int retCode = ffmpeg_exec(cmdLen, argCmd);
    LOGD("ffmpeg-cmd: retCode=%d", retCode);

    return retCode;

}
extern "C"
JNIEXPORT jint JNICALL Java_com_qk_audiotool_ffmpeg_FFmpegCmd_getProgress(JNIEnv *env, jclass type) {
    return get_progress();
}

#define MAX_AUDIO_FRME_SIZE 48000 * 4
extern "C" JNIEXPORT void JNICALL Java_com_qk_audiotool_ffmpeg_AudioPlayer_sound
        (JNIEnv *env, jobject jthiz, jstring input_jstr, jstring output_jstr) {
    const char *input_cstr = env->GetStringUTFChars(input_jstr, NULL);
    const char *output_cstr = env->GetStringUTFChars(output_jstr, NULL);
    LOGI("%s", "sound");

    av_register_all(); // 注册组件
    avformat_network_init(); // 支持网络流
    AVFormatContext *pFormatCtx = avformat_alloc_context();
    //打开音频文件
    if (avformat_open_input(&pFormatCtx, input_cstr, NULL, NULL) != 0) {
        LOGI("%s, %s", "无法打开音频文件 ", input_cstr);
        return;
    }
    //获取输入文件信息
    if (avformat_find_stream_info(pFormatCtx, NULL) < 0) {
        LOGI("%s", "无法获取输入文件信息");
        return;
    }
    //获取音频流索引位置
    int i = 0, audio_stream_idx = -1;
    for (; i < pFormatCtx->nb_streams; i++) {
        if (pFormatCtx->streams[i]->codec->codec_type == AVMEDIA_TYPE_AUDIO) {
            audio_stream_idx = i;
            break;
        }
    }

    //获取解码器
    AVCodecContext *codecCtx = pFormatCtx->streams[audio_stream_idx]->codec;
    AVCodec *codec = avcodec_find_decoder(codecCtx->codec_id);
    if (codec == NULL) {
        LOGI("%s", "无法获取解码器");
        return;
    }
    //打开解码器
    if (avcodec_open2(codecCtx, codec, NULL) < 0) {
        LOGI("%s", "无法打开解码器");
        return;
    }
    //压缩数据
    AVPacket *packet = (AVPacket *) av_malloc(sizeof(AVPacket));
    //解压缩数据
    AVFrame *frame = av_frame_alloc();
    //frame->16bit 44100 PCM 统一音频采样格式与采样率
    SwrContext *swrCtx = swr_alloc();

    //重采样设置参数-------------start
    //输入的采样格式
    enum AVSampleFormat in_sample_fmt = codecCtx->sample_fmt;
    //输出采样格式16bit PCM
    enum AVSampleFormat out_sample_fmt = AV_SAMPLE_FMT_S16;
    //输入采样率
    int in_sample_rate = codecCtx->sample_rate;
    //输出采样率
    int out_sample_rate = 44100;//in_sample_rate;
    //获取输入的声道布局
    //根据声道个数获取默认的声道布局（2个声道，默认立体声stereo）
    //av_get_default_channel_layout(codecCtx->channels);
    uint64_t in_ch_layout = codecCtx->channel_layout;
    //输出的声道布局（立体声）
    uint64_t out_ch_layout = AV_CH_LAYOUT_STEREO;

    swr_alloc_set_opts(swrCtx,
                       out_ch_layout, out_sample_fmt, out_sample_rate,
                       in_ch_layout, in_sample_fmt, in_sample_rate,
                       0, NULL);
    swr_init(swrCtx);

    //输出的声道个数
    int out_channel_nb = av_get_channel_layout_nb_channels(out_ch_layout);

    //重采样设置参数-------------end

    //JNI begin------------------
    //JasonPlayer
    jclass player_class = env->GetObjectClass(jthiz);

    jmethodID callback = env->GetMethodID(player_class, "onPlaying", "([B)V");
    //AudioTrack对象
    jmethodID create_audio_track_mid = env->GetMethodID(player_class, "createAudioTrack",
                                                        "(II)Landroid/media/AudioTrack;");
    jobject audio_track = env->CallObjectMethod(jthiz, create_audio_track_mid, out_sample_rate,
                                                out_channel_nb);

    //调用AudioTrack.play方法
    jclass audio_track_class = env->GetObjectClass(audio_track);
    jmethodID audio_track_play_mid = env->GetMethodID(audio_track_class, "play", "()V");
    env->CallVoidMethod(audio_track, audio_track_play_mid);

    //AudioTrack.write
    jmethodID audio_track_write_mid = env->GetMethodID(audio_track_class, "write", "([BII)I");

    //JNI end------------------
    FILE *fp_pcm = fopen(output_cstr, "wb");

    //16bit 44100 PCM 数据
    uint8_t *out_buffer = (uint8_t *) av_malloc(MAX_AUDIO_FRME_SIZE);
    LOGI("in_sample_rate %d, out_channel_nb %d", in_sample_rate, out_channel_nb);
    int got_frame = 0, index = 0, ret;
    //不断读取压缩数据
    while (av_read_frame(pFormatCtx, packet) >= 0) {
        //解码音频类型的Packet
        if (packet->stream_index == audio_stream_idx) {
            //解码
            ret = avcodec_decode_audio4(codecCtx, frame, &got_frame, packet);

            if (ret < 0) {
                LOGI("%s", "解码完成");
            }
            //解码一帧成功
            if (got_frame > 0) {
                LOGI("解码：%d", index++);
                // frame中的数据重采样到out_buffer
                swr_convert(swrCtx, &out_buffer, MAX_AUDIO_FRME_SIZE,
                            (const uint8_t **) frame->data, frame->nb_samples);
                //获取sample的size
                int out_buffer_size = av_samples_get_buffer_size(NULL, out_channel_nb,
                                                                 frame->nb_samples, out_sample_fmt,
                                                                 1);
                fwrite(out_buffer, 1, out_buffer_size, fp_pcm);

                //out_buffer缓冲区数据，转成byte数组
                jbyteArray audio_sample_array = env->NewByteArray(out_buffer_size);
                jbyte *sample_bytep = env->GetByteArrayElements(audio_sample_array, NULL);
                //out_buffer的数据复制到sampe_bytep
                memcpy(sample_bytep, out_buffer, out_buffer_size);
                //同步
                env->ReleaseByteArrayElements(audio_sample_array, sample_bytep, 0);

//                if (audio_sample_array) {
//                    jsize len = env->GetArrayLength(audio_sample_array);
//                    jbyte *jbarray = (jbyte *) malloc(len * sizeof(jbyte));
//                    env->GetByteArrayRegion(audio_sample_array, 0, len, jbarray);
//                    rec_audio(jbarray, env->GetArrayLength(audio_sample_array));
//                    free(jbarray);
//                }

                //AudioTrack.write PCM数据
                env->CallIntMethod(audio_track, audio_track_write_mid,
                                   audio_sample_array, 0, out_buffer_size);

                env->CallVoidMethod(jthiz, callback, audio_sample_array);
                //释放局部引用
                env->DeleteLocalRef(audio_sample_array);
                usleep(1000 * 16);
            }
        }

        av_free_packet(packet);
//        stop_audio();
    }

    av_frame_free(&frame);
    av_free(out_buffer);

    swr_free(&swrCtx);
    avcodec_close(codecCtx);
    avformat_close_input(&pFormatCtx);

    env->ReleaseStringUTFChars(input_jstr, input_cstr);
    env->ReleaseStringUTFChars(output_jstr, output_cstr);

}

int getChannelLayout(int channel){
    if(channel == 2){
        return AV_CH_LAYOUT_STEREO;
    } else {
        return AV_CH_LAYOUT_MONO;
    }
}
/**
 * 重采样
 * @param env
 * @param clazz
 * @param sourcePath 源PCM文件
 * @param targetPath 目标PCM文件
 * @param sourceSampleRate 源采样率
 * @param targetSampleRate 目标采样率
 * @param sourceChannels 源声道数
 * @param targetChannels 目标声道数
 * @return
 */
extern "C" JNIEXPORT jint JNICALL Java_com_qk_audiotool_ffmpeg_AudioPlayer_Resample(JNIEnv *env, jobject jthiz,
                        jstring sourcePath, jstring targetPath,
                        jint sourceSampleRate, jint targetSampleRate,
                        jint sourceChannels, jint targetChannels) {
    int result = -1;
    FILE *source;
    FILE *target;
    SwrContext *context;
    int sourceChannelLayout;
    int targetChannelLayout;
    AVSampleFormat sampleFormat;
    int sourceLineSize;
    int sourceBufferSize;
    int sourceSamples;
    uint8_t **sourceData;
    int targetLineSize;
    int targetBufferSize;
    int targetSamples;
    int targetMaxSamples;
    uint8_t **targetData;
    int read;
    const char *_sourcePath = env->GetStringUTFChars(sourcePath, 0);
    const char *_targetPath = env->GetStringUTFChars(targetPath, 0);
    // 打开文件
    source = fopen(_sourcePath, "rb");
    if (!source) {
        result = -1;
        goto R2;
    }
    target = fopen(_targetPath, "wb");
    if (!target) {
        fclose(source);
        goto R2;
    }
    // 重采样上下文
    context = swr_alloc();
    if (!context) {
        goto R1;
    }
    // 声道类型
    sourceChannelLayout = getChannelLayout(sourceChannels);
    targetChannelLayout = getChannelLayout(targetChannels);
    // 16BIT交叉存放PCM数据格式
    sampleFormat = AV_SAMPLE_FMT_S16;
    // 配置
    av_opt_set_int(context, "in_channel_layout", sourceChannelLayout, 0);
    av_opt_set_int(context, "in_sample_rate", sourceSampleRate, 0);
    av_opt_set_sample_fmt(context, "in_sample_fmt", sampleFormat, 0);
    av_opt_set_int(context, "out_channel_layout", targetChannelLayout, 0);
    av_opt_set_int(context, "out_sample_rate", targetSampleRate, 0);
    av_opt_set_sample_fmt(context, "out_sample_fmt", sampleFormat, 0);
    // 初始化
    if (swr_init(context) < 0) {
        result = -1;
        goto R1;
    }
    // 输入
    // 输入样品数 一帧1024样品数
    sourceSamples = 1024;
    // 输入大小 计算一帧样品数据量大小 = 声道数 * 样品数 * 每个样品所占字节
    sourceBufferSize = av_samples_get_buffer_size(&sourceLineSize, sourceChannels, sourceSamples, sampleFormat, 1);
    // 分配输入空间
    result = av_samples_alloc_array_and_samples(&sourceData, &sourceLineSize, sourceChannels,
                                                sourceSamples, sampleFormat, 0);
    if (result < 0) {
        result = -1;
        goto R1;
    }
    // 输出
    // 计算（最大）输出样品数
    targetMaxSamples = targetSamples = (int) av_rescale_rnd(sourceSamples, targetSampleRate, sourceSampleRate, AV_ROUND_UP);
    // 分配输出空间
    result = av_samples_alloc_array_and_samples(&targetData, &targetLineSize, targetChannels,
                                                targetSamples, sampleFormat, 0);
    if (result < 0) {
        result = -1;
        goto R1;
    }
    // 循环读取文件
    // 每次读取一帧数据量大小
    read = fread(sourceData[0], 1, sourceBufferSize, source);
    while (read > 0) {
        // 计算输出样品数
        targetSamples = (int) av_rescale_rnd(swr_get_delay(context, sourceSampleRate) + sourceSamples, targetSampleRate, sourceSampleRate, AV_ROUND_UP);
        if (targetSamples > targetMaxSamples) {
            av_freep(&targetData[0]);
            result = av_samples_alloc(targetData, &targetLineSize, targetChannels, targetSamples, sampleFormat, 1);
            if (result < 0) {
                break;
            }
            targetMaxSamples = targetSamples;
        }
        // 重采样
        result = swr_convert(context, targetData, targetSamples,
                             (const uint8_t **) sourceData, sourceSamples);
        if (result < 0) {
            break;
        }
        // 计算输出大小 result为一帧重采样数
        targetBufferSize = av_samples_get_buffer_size(&targetLineSize, targetChannels, result, sampleFormat, 1);
        if (targetBufferSize < 0) {
            break;
        }
        // 写入文件
        fwrite(targetData[0], 1, targetBufferSize, target);
        // 每次读取一帧数据量大小
        read = fread(sourceData[0], 1, sourceBufferSize, source);
    }
    R1:
    // 关闭文件
    fclose(source);
    fclose(target);
    R2:
    // 释放
    swr_free(&context);
    env->ReleaseStringUTFChars(sourcePath, _sourcePath);
    env->ReleaseStringUTFChars(targetPath, _targetPath);
    return result;
}


//重采样上下文
SwrContext *swr_ctx = NULL;
//输入音频参数
int64_t src_ch_layout = AV_CH_LAYOUT_STEREO;
int src_rate = 44100;
uint8_t **src_data = NULL;
int src_nb_channels = 0;
int src_linesize = 0;
int src_nb_samples = 0;
enum AVSampleFormat src_sample_fmt = AV_SAMPLE_FMT_S16;
//输出音频参数
int64_t dst_ch_layout = AV_CH_LAYOUT_STEREO;
int dst_rate = 48000;
uint8_t **dst_data = NULL;
int dst_nb_channels = 0;
int dst_linesize = 0;
int dst_nb_samples = 0, dst_max_nb_samples = 0;
enum AVSampleFormat dst_sample_fmt = AV_SAMPLE_FMT_S16;
int init_swr_env_success = 0;
int open_file_success = 0;
FILE *pFile;


/**
 * 创建重采样上下文
 * @param pContext 需要初始化的重采样上下文双指针
 * @return success：1   failed：0
 */
int8_t init_swr(SwrContext **pContext, jsize size) {
    int ret = 0;
    //创建SwrContext，设置参数。
    *pContext = swr_alloc_set_opts(NULL,                //cts
                                   dst_ch_layout,          //输出的channel布局
                                   dst_sample_fmt,         //输出的采样大小
                                   dst_rate,               //输出的采样率
                                   src_ch_layout,          //输入channel布局
                                   src_sample_fmt,         //输入的采样格式
                                   src_rate,               //输入的采样率
                                   0, NULL);

    //初始化重采样上下文
    if (!*pContext || swr_init(*pContext) < 0) {
        goto __ERROR;
    }

    //依据输入布局计算通道数
    src_nb_channels = av_get_channel_layout_nb_channels(static_cast<uint64_t>(src_ch_layout));
    //输入数据，单通道采样点数量， av_get_bytes_per_sample  位深->byte
    src_nb_samples = size / src_nb_channels / av_get_bytes_per_sample(src_sample_fmt);
    //依据输入数据的通道数、单通道采样点数量、采样大小，申请src_data缓冲数据地址和src_linesize数据长度
    ret = av_samples_alloc_array_and_samples(&src_data, &src_linesize, src_nb_channels,
                                             src_nb_samples, src_sample_fmt, 0);

    if (ret < 0) {
        goto __ERROR;
    }
    //计算输出数据单通道采样点数量
    //计算转换的样本数：避免缓冲，确保输出缓冲区至少包含所有转换的输入样本
    dst_max_nb_samples = dst_nb_samples = static_cast<int>(av_rescale_rnd(src_nb_samples, dst_rate,
                                                                          src_rate,
                                                                          AV_ROUND_UP));
    dst_nb_channels = av_get_channel_layout_nb_channels(static_cast<uint64_t>(dst_ch_layout));
    ret = av_samples_alloc_array_and_samples(&dst_data, &dst_linesize, dst_nb_channels,
                                             dst_nb_samples,
                                             dst_sample_fmt, 0);
    if (ret < 0) {
        goto __ERROR;
    }
    return 1;
    __ERROR:
    if (*pContext) {
        swr_free(pContext);
    }
    LOGI("重采样模块初始化失败");
    return 0;
}

/**
 * 对上层传过来的每一帧音频数据做处理
 * 这个函数会被Java层的while调用到
 * @param pArray 原始音频帧数据
 * @param size   数据大小
 * @return
 */
int rec_audio(jbyte *pArray, jsize size) {
    int result = 1;
    int ret = 0;
    int dst_bufsize = 0;
    if (!init_swr_env_success && init_swr(&swr_ctx, size)) {
        init_swr_env_success = 1;
    }
    if (!open_file_success) {
        pFile = fopen("/data/data/com.qk.qingka/files/out.swr.pcm", "wb+");
        open_file_success = 1;
    }

    dst_nb_samples = static_cast<int>(av_rescale_rnd(
            swr_get_delay(swr_ctx, src_rate) + src_nb_samples, dst_rate, src_rate, AV_ROUND_UP));
    if (dst_nb_samples > dst_max_nb_samples) {
        av_free(&dst_data[0]);
        ret = av_samples_alloc(dst_data, &dst_linesize, dst_nb_channels, dst_nb_samples,
                               dst_sample_fmt, 0);
        if (ret < 0) goto __end;
        dst_max_nb_samples = dst_nb_samples;
    }

    LOGI("src_nb_samples:%d, dst_nb_samples:%d", src_nb_samples, dst_nb_samples);
    memcpy((void *) src_data[0], (void *) pArray, static_cast<size_t>(size));
    ret = swr_convert(swr_ctx, dst_data, dst_nb_samples, (const uint8_t **) src_data,
                      src_nb_samples);
    if (ret < 0)goto __end;
    dst_bufsize = av_samples_get_buffer_size(&dst_linesize, dst_nb_channels, dst_nb_samples,
                                             dst_sample_fmt, 1);
    if (dst_bufsize < 0)goto __end;
    LOGI("in:%d out:%d ,dst_bufsize:%d", src_nb_samples, ret, dst_bufsize);
    fwrite(dst_data[0], 1, static_cast<size_t>(dst_bufsize), pFile);
    return result;

    __end:
    if (ret < 0) {
        char errors[1024] = {0,};
        av_strerror(ret, errors, 1024);
        LOGI("重采样出错 [%d]%s", ret, errors);
    }
    result = -1;
    return result;
}

void stop_audio() {
    //重置标志位
    init_swr_env_success = 0;
    open_file_success = 0;
    //释放资源
    if (pFile) {
        fflush(pFile);
        fclose(pFile);
    }
    //释放输入输出缓冲区
    if (src_data) {
        av_freep(&src_data[0]);
    }
    av_freep(&src_data);
    if (dst_data) {
        av_freep(&dst_data[0]);
    }
    av_freep(&dst_data);
    //释放重采样上下文
    if (swr_ctx)
        swr_free(&swr_ctx);
    LOGI("finish !!");
}

