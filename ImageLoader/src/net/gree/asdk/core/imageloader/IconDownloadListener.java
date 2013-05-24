package net.gree.asdk.core.imageloader;

import org.apache.http.HeaderIterator;

import android.graphics.Bitmap;

public interface IconDownloadListener {
	  void onSuccess(Bitmap image);
	  void onFailure(int responseCode, HeaderIterator headers, String response);
	}
