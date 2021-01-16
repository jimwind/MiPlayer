package com.qk.audiotool;


import android.database.Cursor;
import android.os.Bundle;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;

/**
 * Created by Vander on 2019-10-24 14:28
 * <p>
 * 短音频工具：取所有本地音频
 */
public class LocalAudioManager {

    private String[] mMediaColumns = {
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.IS_MUSIC,
            MediaStore.Audio.Media.DATA
    };

    private Fragment mOwner;

    private OnQueryAudioListener mOnQueryAudioListener;

    public LocalAudioManager(Fragment owner) {
        mOwner = owner;
    }

    public void setOnQueryAudioListener(OnQueryAudioListener onQueryAudioListener) {
        mOnQueryAudioListener = onQueryAudioListener;
    }

    /**
     * 查询本地音频实现
     * <p>
     * // 过滤规则 创建CursorLoader时可以设置selection
     * // 注意点 魅族部分手机 isMusic 恒等为 0
     */
    public void onQuery() {
        final LoaderManager manager = LoaderManager.getInstance(mOwner);
        manager.initLoader(0, null, new LoaderManager.LoaderCallbacks<Cursor>() {
            @NonNull
            @Override
            public Loader<Cursor> onCreateLoader(int id, @Nullable Bundle args) {
                //只取mp3文件
                String mtMP3 = MimeTypeMap.getSingleton().getMimeTypeFromExtension("mp3");
                String mtWAV = MimeTypeMap.getSingleton().getMimeTypeFromExtension("wav");
                return new CursorLoader(
                        mOwner.getActivity(),
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        mMediaColumns,
//                        MediaStore.Files.FileColumns.MIME_TYPE + "=? or " + MediaStore.Files.FileColumns.MIME_TYPE + "=?",
                        MediaStore.Files.FileColumns.MIME_TYPE + "=? ",
                        new String[]{mtMP3/*, mtWAV*/},
                        MediaStore.Audio.Media.DATE_ADDED + " DESC"
                );
            }

            @Override
            public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor data) {
                if (data == null || data.getCount() <= 0) {
//                    if (mOnQueryAudioListener != null) {
//                        mOnQueryAudioListener.onComplete(list);
//                    }
                    manager.destroyLoader(0);
                    return;
                }
                while (data.moveToNext()) {
                    int isMusic = data.getInt(data.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_MUSIC));
                    if (isMusic == 0) {
                        continue;
                    }
                    String id = data.getString(data.getColumnIndexOrThrow(MediaStore.Audio.Media._ID));

                    String albumId = data.getString(data.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID));

                    String title = data.getString(data.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE));

                    String artist = data.getString(data.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST));

                    long duration = data.getLong(data.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION));

                    long size = data.getLong(data.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE));

                    String mineType = data.getString(data.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE));

                    String path = data.getString(data.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));
                }
//                if (mOnQueryAudioListener != null) {
//                    mOnQueryAudioListener.onComplete(list);
//                }
                manager.destroyLoader(0);
            }

            @Override
            public void onLoaderReset(@NonNull Loader<Cursor> loader) {
                //Empty
            }
        });
    }


    /**
     * 查询本地音频的监听
     */
    public interface OnQueryAudioListener {
//        void onComplete(List<AudioInfo> list);
    }

}