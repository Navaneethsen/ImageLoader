package net.gree.asdk.core.imageloader;

import net.gree.asdk.core.imageloader.cache.ImageCache;
import net.gree.asdk.core.imageloader.core.ImageLoader;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.widget.ImageView;

public class ImageLoaderManager {
	private final LoaderSettings mLoaderSettings;
	
	private ImageLoader mImageLoader;
	
	public ImageLoaderManager(Context context, LoaderSettings settings) {
		if (context != null) {
            verifyPermissions(context);
        }
		mLoaderSettings = settings;
		
		mImageLoader = new ImageLoader(context, mLoaderSettings.mTaskExecutor);
		ImageCache imageCache = new ImageCache(context, mLoaderSettings);
		mImageLoader.setImageCache(imageCache);
		mImageLoader.setImageDownloader(mLoaderSettings.mImageDownloader);
	}
	
	private void verifyPermissions(Context context) {
        verifyPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        verifyPermission(context, Manifest.permission.INTERNET);
    }

    private void verifyPermission(Context c, String permission) {
        int p = c.getPackageManager().checkPermission(permission, c.getPackageName());
        if (p == PackageManager.PERMISSION_DENIED) {
            throw new RuntimeException("ImageLoader : please add the permission " + permission + " to the manifest");
        }
    }
    
    public void loadImage(String url, ImageView imageView, int resId,
			int reqWidth, int reqHeight)  {
    	mImageLoader.loadImage(url, imageView, resId, reqWidth, reqHeight);
    }
    
    public void loadImage(String url, ImageView imageView, int resId,
			int reqWidth, int reqHeight, int reqMargin)  {
    	mImageLoader.loadImage(url, imageView, resId, reqWidth, reqHeight, reqMargin);
    }
    
    public void loadImage(String url, ImageView imageView, int resId,
			int reqWidth, int reqHeight, int reqCornerRadius, int reqMargin)  {
    	mImageLoader.loadImage(url, imageView, resId, reqWidth, reqHeight, reqCornerRadius, reqMargin);
    }
    
    public void loadImage(String url, int reqWidth, int reqHeight, IconDownloadListener listener) {
    	mImageLoader.loadImage(url, reqWidth, reqHeight, listener);
    }
    
    public void clear() {
    	mImageLoader.clearAll();
    }
}
