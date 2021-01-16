#include "lame_3.99.5/lame.h"
#include "lame_3.99.5/lame_global_flags.h"
#include <stdio.h>
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <jni.h>
#include <android/log.h>
#include <sys/stat.h>

#define LOG_TAG "LAME ENCODER"
#define LOGD(format, args...)  __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, format, ##args);
#define BUFFER_SIZE 8192
#define be_short(s) ((short) ((unsigned short) (s) << 8) | ((unsigned short) (s) >> 8))
#define LOGI(FORMAT, ...) __android_log_print(ANDROID_LOG_INFO,"FILE_PATCH",FORMAT,__VA_ARGS__);
#define INBUFSIZE  4096
#define MP3BUFSIZE (int) (1.25 * INBUFSIZE) + 7200

static lame_global_flags *lame = NULL;

JNIEXPORT void JNICALL Java_com_qk_audiotool_lame_LameUtil_init(
		JNIEnv *env, jclass cls, jint inSamplerate, jint inChannel, jint outSamplerate, jint outBitrate, jint quality) {
	if (lame != NULL) {
		lame_close(lame);
		lame = NULL;
	}
	lame = lame_init();
	lame_set_in_samplerate(lame, inSamplerate);
	if(inChannel == 1) {
        lame_set_mode(lame, MONO);
    } else if(inChannel == 2){
	    lame_set_mode(lame, STEREO);
	}
	lame_set_num_channels(lame, inChannel);//输入流的声道
	lame_set_out_samplerate(lame, outSamplerate);
	lame_set_brate(lame, outBitrate);
	lame_set_quality(lame, quality);
	lame_set_VBR(lame, vbr_default);
	lame_init_params(lame);
}

JNIEXPORT jint JNICALL Java_com_qk_audiotool_lame_LameUtil_encode(
		JNIEnv *env, jclass cls, jshortArray buffer_l, jshortArray buffer_r,
		jint samples, jbyteArray mp3buf) {
	jshort* j_buffer_l = (*env)->GetShortArrayElements(env, buffer_l, NULL);

	jshort* j_buffer_r = (*env)->GetShortArrayElements(env, buffer_r, NULL);

	const jsize mp3buf_size = (*env)->GetArrayLength(env, mp3buf);
	jbyte* j_mp3buf = (*env)->GetByteArrayElements(env, mp3buf, NULL);

	int result = lame_encode_buffer(lame, j_buffer_l, j_buffer_r,
			samples, j_mp3buf, mp3buf_size);

	(*env)->ReleaseShortArrayElements(env, buffer_l, j_buffer_l, 0);
	(*env)->ReleaseShortArrayElements(env, buffer_r, j_buffer_r, 0);
	(*env)->ReleaseByteArrayElements(env, mp3buf, j_mp3buf, 0);

	return result;
}

JNIEXPORT jint JNICALL Java_com_qk_audiotool_lame_LameUtil_flush(
		JNIEnv *env, jclass cls, jbyteArray mp3buf) {
	const jsize mp3buf_size = (*env)->GetArrayLength(env, mp3buf);
	jbyte* j_mp3buf = (*env)->GetByteArrayElements(env, mp3buf, NULL);

	int result = lame_encode_flush(lame, j_mp3buf, mp3buf_size);

	(*env)->ReleaseByteArrayElements(env, mp3buf, j_mp3buf, 0);

	return result;
}

JNIEXPORT void JNICALL Java_com_qk_audiotool_lame_LameUtil_close
(JNIEnv *env, jclass cls) {
	lame_close(lame);
	lame = NULL;
}

int read_samples(FILE *input_file, short *input) {
	int nb_read;
	nb_read = fread(input, 1, sizeof(short), input_file) / sizeof(short);
 
	int i = 0;
	while (i < nb_read) {
		input[i] = be_short(input[i]);
		i++;
	}
 
	return nb_read;
}

JNIEXPORT jint Java_com_qk_audiotool_lame_LameUtil_encodeFile
(JNIEnv *env, jclass cls, jstring in_source_path, jstring in_target_path) {
	int status = 0;

	short* pcm_buffer;
	short* pcm_buffer_left;
	short* pcm_buffer_right;
	int read;
	unsigned char* mp3_buffer;
	int write;

	const char *source_path, *target_path;
	source_path = (*env)->GetStringUTFChars(env, in_source_path, NULL);
	target_path = (*env)->GetStringUTFChars(env, in_target_path, NULL);

	FILE *infp, *outfp;
	infp = fopen(source_path, "rb");
	outfp = fopen(target_path, "wb");
	const int bufferSize = 1024 * 32;
	const int mp3_buffserSize = 1024 * 16 * 1.25 + 7200;
	struct stat statbuff;
	jlong filesize = 0;
	if(stat(source_path, &statbuff) < 0){
	} else {
		filesize = statbuff.st_size;
	}
	jlong readsize = 0;
	filesize /= sizeof(short);
	LOGI("jimwind LameUtil_encodeFile num_channels %d ", lame->num_channels);
	LOGI("jimwind LameUtil_encodeFile source_path %s ", source_path);
	LOGI("jimwind LameUtil_encodeFile target_path %s ", target_path);
	//回调函数：
	jmethodID callback = (*env)->GetMethodID(env, cls, "progress", "(J)V");
	jmethodID initLameUtil = (*env)->GetMethodID(env, cls, "<init>", "()V");
	jobject jobjLameUtil = (*env)->NewObject(env, cls, initLameUtil);

	int channels = lame->num_channels;
	if(channels == 2){
        pcm_buffer 		 = (short *)malloc((size_t)(bufferSize * sizeof(short)));
		mp3_buffer		 = (unsigned char *)malloc((size_t)mp3_buffserSize * sizeof(unsigned char));

        pcm_buffer_left  = (short *)malloc((size_t)(bufferSize/2 * sizeof(short)));
        pcm_buffer_right = (short *)malloc((size_t)(bufferSize/2 * sizeof(short)));
		do {
			read = fread(pcm_buffer, sizeof(short), bufferSize, infp);
			LOGI("jimwind LameUtil_encodeFile read is %d --------------------------", read);
			if(read != 0){
				for(int i=0; i<read; i++){
					if(i%2 == 0){
						pcm_buffer_left[i/2]  = pcm_buffer[i];
					} else {
						pcm_buffer_right[i/2] = pcm_buffer[i];
					}
				}
				write = lame_encode_buffer(lame, pcm_buffer_left, pcm_buffer_right, read / 2, mp3_buffer, mp3_buffserSize);
			} else if(read == 0){
				write = lame_encode_flush(lame, mp3_buffer, mp3_buffserSize);
			}
			readsize += read;
			(*env)->CallVoidMethod(env, jobjLameUtil, callback, readsize * 100 / filesize);
            LOGI("jimwind LameUtil_encodeFile read is %d, write is %d, progress %d", read, write, (int)readsize * 100 / filesize);
			fwrite(mp3_buffer, 1, write, outfp);
		} while (read != 0);
	} else {
		pcm_buffer 		 = (short *)malloc((size_t)(INBUFSIZE * sizeof(short)) );
		mp3_buffer		 = (unsigned char *)malloc((size_t)MP3BUFSIZE * sizeof(unsigned char));
		do {
			read = fread(pcm_buffer, sizeof(short), INBUFSIZE, infp);
			LOGI("jimwind LameUtil_encodeFile channel is 1, read is %d -------------", read);
			if(read != 0){
				write = lame_encode_buffer(lame, pcm_buffer, pcm_buffer, read, mp3_buffer, MP3BUFSIZE);
			} else if(read == 0){
				write = lame_encode_flush(lame, mp3_buffer, MP3BUFSIZE);
			}
			readsize += read;
			(*env)->CallVoidMethod(env, jobjLameUtil, callback, readsize * 100 / filesize);
            LOGI("jimwind LameUtil_encodeFile channel is 1, read is %d, write is %d, progress %d", read, write, (int)readsize * 100 / filesize);
			fwrite(mp3_buffer, sizeof(unsigned char), write, outfp);
		} while (read != 0);
	}
    free(mp3_buffer);
    free(pcm_buffer);
    if(channels == 2) {
        free(pcm_buffer_left);
        free(pcm_buffer_right);
    }
    fclose(outfp);
    fclose(infp);
    (*env)->ReleaseStringUTFChars(env, in_source_path, source_path);
    (*env)->ReleaseStringUTFChars(env, in_target_path, target_path);
    lame_mp3_tags_fid(lame, outfp);
	lame_close(lame);
	lame = NULL;
	LOGI("jimwind LameUtil_encodeFile finished, status is %d", status);
	return status;
}

//JNIEXPORT jint Java_com_qk_audiotool_lame_LameUtil_encodeFile
//        (JNIEnv *env, jobject jobj, jstring in_source_path, jstring in_target_path) {
//    int status = 0;
//
//    short* input_buffer;
//    int input_samples;
//    unsigned char* mp3_buffer;
//    int mp3_bytes;
//
//    const char *source_path, *target_path;
//    source_path = (*env)->GetStringUTFChars(env, in_source_path, NULL);
//    target_path = (*env)->GetStringUTFChars(env, in_target_path, NULL);
//
//    FILE *infp, *outfp;
//    infp = fopen(source_path, "rb");
//    outfp = fopen(target_path, "wb");
//
//    mp3_buffer = (char *)malloc((size_t)MP3BUFSIZE);
//    input_buffer = (short *)malloc((size_t)INBUFSIZE * 2);
//    LOGI("jimwind LameUtil_encodeFile source_path %s ", source_path);
//    LOGI("jimwind LameUtil_encodeFile target_path %s ", target_path);
//
//    do{
//        input_samples = fread(input_buffer, sizeof(short int) * lame->num_channels, (size_t)INBUFSIZE, infp);
//        LOGI("jimwind LameUtil_encodeFile %d ", input_samples);
//        if(input_samples != 0) {
//            //https://blog.csdn.net/xjb2006/article/details/81261699
//            //解决单声道录音转成mp3时间时长减半，速度加快 mi.gao@2019/10/16
//            if (lame->num_channels == 2) {
//                mp3_bytes = lame_encode_buffer_interleaved(lame, input_buffer,
//                                                           input_samples, mp3_buffer, MP3BUFSIZE);
//            } else {
//                mp3_bytes = lame_encode_buffer(lame, input_buffer, NULL,
//                                               input_samples, mp3_buffer, MP3BUFSIZE);
//            }
//        } else {
//            mp3_bytes = lame_encode_flush(lame, mp3_buffer, sizeof(mp3_buffer));
//        }
//        LOGI("jimwind LameUtil_encodeFile mp3_bytes %d", mp3_bytes);
//        if(mp3_bytes < 0){
//            status = -1;
//            goto free_buffers;
//        } else if(mp3_bytes > 0){
//            fwrite(mp3_buffer, 1, mp3_bytes, outfp);
//        }
//    } while(input_samples > 0);
//
//    free_buffers:
//        free(mp3_buffer);
//        free(input_buffer);
//        fclose(outfp);
//        fclose(infp);
//
//    lame_close(lame);
//    lame = NULL;
//
//    return status;
//}
