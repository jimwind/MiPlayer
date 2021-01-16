# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)

set(distribution_DIR ${PROJECT_SOURCE_DIR}/../../../libs)
include_directories(src/main/cpp/touch)
include_directories(src/main/cpp/lame_3.99.5)
include_directories(src/main/cpp/inc)
include_directories(src/main/cpp/wrtc)
include_directories(src/main/cpp/ffmpeg)


set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Wall -pedantic")

include_directories(${PROJECT_SOURCE_DIR}/include)

#add_definitions(-DFIXED_POINT -DUSE_KISS_FFT -DHAVE_CONFIG_H)
# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
        log-lib

        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log )


add_library(soundtouch
        SHARED
        soundtouch-jni.cpp
        touch/AAFilter.cpp
        touch/FIFOSampleBuffer.cpp
        touch/FIRFilter.cpp
        touch/cpu_detect_x86.cpp
        touch/sse_optimized.cpp
        touch/WavFile.cpp
        touch/RateTransposer.cpp
        touch/SoundTouch.cpp
        touch/InterpolateCubic.cpp
        touch/InterpolateLinear.cpp
        touch/InterpolateShannon.cpp
        touch/TDStretch.cpp
        touch/BPMDetect.cpp
        touch/PeakFinder.cpp
        )
target_link_libraries(soundtouch
        ${log-lib})

add_library(mp3lame
		SHARED
		lame_util.c
		lame_3.99.5/bitstream.c
		lame_3.99.5/fft.c
		lame_3.99.5/id3tag.c
		lame_3.99.5/mpglib_interface.c
		lame_3.99.5/presets.c
		lame_3.99.5/quantize.c
		lame_3.99.5/reservoir.c
		lame_3.99.5/tables.c
		lame_3.99.5/util.c
		lame_3.99.5/VbrTag.c
		lame_3.99.5/encoder.c
		lame_3.99.5/gain_analysis.c
		lame_3.99.5/lame.c
		lame_3.99.5/newmdct.c
		lame_3.99.5/psymodel.c
		lame_3.99.5/quantize_pvt.c
		lame_3.99.5/set_get.c
		lame_3.99.5/takehiro.c
		lame_3.99.5/vbrquantize.c
		lame_3.99.5/version.c

		)
target_link_libraries(mp3lame
        ${log-lib})

#--------------------fmod--------------------
#fmod 相关 start
add_library( fmod
		SHARED
		IMPORTED )
set_target_properties( fmod
		PROPERTIES IMPORTED_LOCATION
		${distribution_DIR}/${ANDROID_ABI}/libfmod.so)


add_library( fmodL
		SHARED
		IMPORTED )
set_target_properties( fmodL
		PROPERTIES IMPORTED_LOCATION
		${distribution_DIR}/${ANDROID_ABI}/libfmodL.so )

add_library( fmod_util
		SHARED
		fmod.cpp)

target_link_libraries( fmod_util
		fmod
		fmodL
		${log-lib} )
#fmod 相关 end
#--------------------fmod--------------------
add_library(libavutil
		SHARED
		IMPORTED
		)
set_target_properties(libavutil
		PROPERTIES IMPORTED_LOCATION
		${distribution_DIR}/${ANDROID_ABI}/libavutil.so )

add_library(libswresample
		SHARED
		IMPORTED
		)
set_target_properties(libswresample
		PROPERTIES IMPORTED_LOCATION
		${distribution_DIR}/${ANDROID_ABI}/libswresample.so
		)

add_library(libswscale
		SHARED
		IMPORTED
		)
set_target_properties(libswscale
		PROPERTIES IMPORTED_LOCATION
		${distribution_DIR}/${ANDROID_ABI}/libswscale.so
		)

add_library(libavcodec
		SHARED
		IMPORTED
		)
set_target_properties(libavcodec
		PROPERTIES IMPORTED_LOCATION
		${distribution_DIR}/${ANDROID_ABI}/libavcodec.so
		)

add_library(libavformat
		SHARED
		IMPORTED
		)
set_target_properties(libavformat
		PROPERTIES IMPORTED_LOCATION
		${distribution_DIR}/${ANDROID_ABI}/libavformat.so
		)

add_library(libavfilter
		SHARED
		IMPORTED
		)
set_target_properties(libavfilter
		PROPERTIES IMPORTED_LOCATION
		${distribution_DIR}/${ANDROID_ABI}/libavfilter.so
		)
add_library(libpostproc
		SHARED
		IMPORTED
		)
set_target_properties(libpostproc
		PROPERTIES IMPORTED_LOCATION
		${distribution_DIR}/${ANDROID_ABI}/libpostproc.so
		)
add_library(ffmpeg-cmd
		SHARED
		ffmpeg-cmd.cpp
		ffmpeg/cmdutils.c
		ffmpeg/ffmpeg_filter.c
		ffmpeg/ffmpeg_opt.c
		ffmpeg/ffmpeg_hw.c
		ffmpeg/ffmpeg.c
		)

target_link_libraries(ffmpeg-cmd
		libavcodec
		libswscale
		libswresample
		libpostproc
		libavutil
		libavformat
		libavfilter

		# Links the target library to the log library
		# included in the NDK.
		${log-lib})

add_library( # Sets the name of the library.
		WRtcAudio

		# Sets the library as a shared library.
		SHARED

		# Provides a relative path to your source file(s).
		# Associated headers in the same location as their source
		# file are automatically included.
		web_rtc.cpp

		wrtc/noise_suppression.c
		wrtc/fft4g.c
		wrtc/ns_core.c

		wrtc/analog_agc.c
		wrtc/digital_agc.c
		wrtc/division_operations.c
		wrtc/copy_set_operations.c
		wrtc/dot_product_with_scale.c
		wrtc/downsample_fast.c
		wrtc/resample_by_2.c
		wrtc/spl_sqrt.c
		wrtc/splitting_filter.c

		)
target_link_libraries( # Specifies the target library.
		WRtcAudio

		# Links the target library to the log library
		# included in the NDK.
		${log-lib})
# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

#add_library( # Sets the name of the library.
#             native-lib
#
#             # Sets the library as a shared library.
#             SHARED
#
#             # Provides a relative path to your source file(s).
#             native-lib.cpp )



# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

#target_link_libraries( # Specifies the target library.
#                       native-lib
#
#                       # Links the target library to the log library
#                       # included in the NDK.
#                       ${log-lib} )