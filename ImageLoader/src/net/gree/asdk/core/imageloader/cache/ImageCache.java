/*
 * Copyright (C) 2012 The Android Open Source Project
 * Copyright 2012 GREE, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * NOTE: This file has been modified by GREE, Inc.
 * Modifications are licensed under the License.
 */
package net.gree.asdk.core.imageloader.cache;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.CountDownLatch;

import net.gree.asdk.core.imageloader.LoaderSettings;
import net.gree.asdk.core.imageloader.log.GLog;
import net.gree.asdk.core.imageloader.utils.Util;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Environment;
import android.os.Looper;
import android.support.v4.util.LruCache;
import android.text.TextUtils;

public class ImageCache {
	private static final String TAG = ImageCache.class.getSimpleName();
	private static final int IO_BUFFER_SIZE = 8 * 1024;
	private static final int APP_VERSION = 1;
	private static final int VALUE_COUNT = 1;
	private static final CompressFormat COMPRESS_FORMAT = CompressFormat.PNG;
	private static final int COMPRESS_QUALITY = 100;

	private DiskLruCache mDiskLruCache = null;
	private CountDownLatch mDiskSignal = null;
	private LruCache<String, Bitmap> mMemCache = null;
	private final LoaderSettings mSettings;

	public ImageCache(final Context context, final LoaderSettings settings) {
		mSettings = settings;
		if (settings.mEnableMemCache) {
			initMemCache(context);
		}
		if (settings.mEnableDiskCache) {
			mDiskSignal = new CountDownLatch(1);
			if (Looper.getMainLooper().getThread()
					.equals(Thread.currentThread())) {
				new Thread(new Runnable() {
					@Override
					public void run() {
						try {
							initDiskCache(context);
						} catch (IOException e) {
							GLog.printStackTrace(TAG, e);
						} finally {
							mDiskSignal.countDown();
						}
					}
				}).start();
			} else {
				try {
					initDiskCache(context);
				} catch (IOException e) {
					GLog.printStackTrace(TAG, e);
				} finally {
					mDiskSignal.countDown();
				}
			}
		}
	}

	private void initMemCache(Context context) {
		mMemCache = new LruCache<String, Bitmap>(mSettings.mMemCacheSize) {
			@Override
			protected int sizeOf(String key, Bitmap bitmap) {
				return getBitmapSize(bitmap);
			}

			@Override
			protected void entryRemoved(boolean evicted, String key,
					Bitmap oldBitmap, Bitmap newBitmap) {
				if (oldBitmap != null && !oldBitmap.isRecycled()) {
					oldBitmap.recycle();
					oldBitmap = null;
				}
			}
		};
	}

	private void initDiskCache(Context context)
			throws IOException {
		File diskCacheDir = getDiskCacheDir(context, mSettings.mUniqueName);
		mDiskLruCache = DiskLruCache.open(diskCacheDir, APP_VERSION,
				VALUE_COUNT, mSettings.mDiskCacheSize);
	}

	public Bitmap getFromMemCache(String key) {
		if (mMemCache == null || TextUtils.isEmpty(key)) {
			return null;
		}
		Bitmap bitmap = mMemCache.get(key);
		if (bitmap != null) {
			GLog.d(TAG, "get from mem  : " + key);
		}
		return bitmap;
	}

	public Bitmap getFromDisk(String key) {
		if (mDiskLruCache == null && mSettings.mEnableDiskCache == true) {
			try {
				mDiskSignal.await();
			} catch (InterruptedException e) {
				GLog.printStackTrace(TAG, e);
			}
		}
		if (mDiskLruCache == null || TextUtils.isEmpty(key)) {
			return null;
		}
		Bitmap bitmap = null;
		DiskLruCache.Snapshot snapshot = null;
		try {
			snapshot = mDiskLruCache.get(hashKeyForDisk(key));
			if (snapshot == null) {
				return null;
			}
			final InputStream in = snapshot.getInputStream(0);
			if (in != null) {
				final BufferedInputStream buffIn = new BufferedInputStream(in);
				bitmap = BitmapFactory.decodeStream(buffIn);
			}
		} catch (IOException e) {
			GLog.printStackTrace(TAG, e);
		} finally {
			if (snapshot != null) {
				snapshot.close();
			}
		}
		if (bitmap != null) {
			GLog.d(TAG, "get from disk : " + key);
		}
		return bitmap;
	}

	public void put(String key, Bitmap bitmap) {
		if (TextUtils.isEmpty(key) || bitmap == null) {
			return;
		}
		putMem(key, bitmap);
		putDisk(key, bitmap);
	}

	public void close() {
		if (mMemCache != null) {
			mMemCache.evictAll();
		}
		if (mDiskLruCache != null) {
			try {
				mDiskLruCache.close();
			} catch (IOException e) {
				GLog.printStackTrace(TAG, e);
			}
		}
	}

	private void putMem(String key, Bitmap bitmap) {
		if (mMemCache != null && mMemCache.get(key) == null) {
			mMemCache.put(key, bitmap);
		}
	}

	private void putDisk(String key, Bitmap bitmap) {
		if (mDiskLruCache == null && mSettings.mEnableDiskCache == true) {
			try {
				mDiskSignal.await();
			} catch (InterruptedException e) {
				GLog.printStackTrace(TAG, e);
			}
		}
		if (mDiskLruCache == null || containsKeyInDisk(key)) {
			return;
		}
		DiskLruCache.Editor editor = null;
		try {
			editor = mDiskLruCache.edit(hashKeyForDisk(key));
			if (editor == null) {
				return;
			}
			if (writeBitmapToFile(bitmap, editor)) {
				mDiskLruCache.flush();
				editor.commit();
			} else {
				editor.abort();
			}
		} catch (IOException e) {
			GLog.printStackTrace(TAG, e);
			if (editor != null) {
				try {
					editor.abort();
				} catch (IOException e1) {
					GLog.printStackTrace(TAG, e);
				}
			}
		}
	}

	/**
	 * Checks to see if the item exists in the Cache already
	 * 
	 * @param url
	 *            Url representation of the key being stored
	 * @return true if it exists, false otherwise
	 */
	public boolean isInCache(String url) {
		if (mMemCache == null) {
			GLog.w(TAG,
					"Memory cache is null on check. Shouldn't be if coming from ImageFetcher.");
			return false;
		}
		// checks to see if it is in the memory cache
		if (mMemCache.get(url) != null) {
			return true;
		}
		// checks to see if it is in the disk cache
		if (containsKeyInDisk(url)) {
			return true;
		}
		// if it is in neither, return false
		return false;
	}

	public boolean containsKeyInDisk(String key) {
		boolean contained = false;
		DiskLruCache.Snapshot snapshot = null;
		try {
			snapshot = mDiskLruCache.get(hashKeyForDisk(key));
			contained = snapshot != null;
		} catch (IOException e) {
			GLog.printStackTrace(TAG, e);
		} finally {
			if (snapshot != null) {
				snapshot.close();
			}
		}
		return contained;
	}

	private boolean writeBitmapToFile(Bitmap bitmap, DiskLruCache.Editor editor)
			throws IOException {
		OutputStream out = null;
		try {
			out = new BufferedOutputStream(editor.newOutputStream(0),
					IO_BUFFER_SIZE);
			return bitmap.compress(COMPRESS_FORMAT, COMPRESS_QUALITY, out);
		} finally {
			if (out != null) {
				out.close();
			}
		}
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
	private int getBitmapSize(Bitmap bitmap) {
		if (Util.hasHoneycombMR1()) {
			return bitmap.getByteCount();
		}
		return bitmap.getRowBytes() * bitmap.getHeight();
	}

	public static File getDiskCacheDir(Context context, String uniqueName) {
		String cachePath = getExternalCacheDir(context).getPath();
		if (TextUtils.isEmpty(cachePath)) {
			cachePath = context.getCacheDir().getPath();
		}
		return new File(cachePath + File.separator + uniqueName);
	}

	public static String hashKeyForDisk(String key) {
		String cacheKey;
		try {
			final MessageDigest mDigest = MessageDigest.getInstance("SHA-1");
			mDigest.update(key.getBytes());
			cacheKey = bytesToHexString(mDigest.digest());
		} catch (NoSuchAlgorithmException e) {
			cacheKey = String.valueOf(key.hashCode());
		}

		return cacheKey;
	}

	private static String bytesToHexString(byte[] bytes) {
		// http://stackoverflow.com/questions/332079
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < bytes.length; i++) {
			String hex = Integer.toHexString(0xFF & bytes[i]);
			if (hex.length() == 1) {
				sb.append('0');
			}
			sb.append(hex);
		}
		return sb.toString();
	}

	@TargetApi(Build.VERSION_CODES.FROYO)
	protected static File getExternalCacheDir(Context context) {
		if (Util.hasFroyo()) {
			File cacheDir = context.getExternalCacheDir();
			if (cacheDir != null) {
				return cacheDir;
			}
		}
		final String cacheDir = "/Android/data/" + context.getPackageName()
				+ "/cache/";
		return new File(Environment.getExternalStorageDirectory().getPath()
				+ cacheDir);
	}
}
