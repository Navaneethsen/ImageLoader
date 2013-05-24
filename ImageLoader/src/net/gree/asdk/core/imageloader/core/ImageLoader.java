package net.gree.asdk.core.imageloader.core;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Hashtable;

import org.apache.http.HeaderIterator;

import net.gree.asdk.core.imageloader.IconDownloadListener;
import net.gree.asdk.core.imageloader.bitmap.BitmapUtil;
import net.gree.asdk.core.imageloader.cache.ImageCache;
import net.gree.asdk.core.imageloader.concurrent.ITaskExecutor;
import net.gree.asdk.core.imageloader.concurrent.Task;
import net.gree.asdk.core.imageloader.download.ImageDownloader;
import net.gree.asdk.core.imageloader.log.GLog;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

public class ImageLoader {
	private static final String TAG = ImageLoader.class.getSimpleName();
	
	private Context mContext;
	private RequestQueue mRequestQueue = new RequestQueue();
	private ImageLoadingThread mImageLoadingWorkerThread;
	private ImageCache mImageCache;
	private final Hashtable<String, Bitmap> mLoadingImages = new Hashtable<String, Bitmap>(2);
	private final BitmapUtil mBitmapUtil = new BitmapUtil();
	private ITaskExecutor mTaskExecutor;
	private ImageDownloader mImageDownloader;
	
	public ImageLoader(Context context, ITaskExecutor taskExecutor) {
		mContext = context;
		mTaskExecutor = taskExecutor;
		mImageLoadingWorkerThread = new ImageLoadingThread(mRequestQueue);
		mImageLoadingWorkerThread.start();	
	}
	
	public void setImageCache(ImageCache imageCache) {
		mImageCache = imageCache;
	}
	
	public void setImageDownloader(ImageDownloader imageDownloader) {
		mImageDownloader = imageDownloader;
	}
	
	public void loadImage(String url, ImageView imageView, int resId,
			int reqWidth, int reqHeight, int reqCornerRadius, int reqMargin) {
		GLog.d(TAG, "request       : " + url);
		if (!isValidUrl(url)) {
			GLog.e(TAG, "invalid       : " + url);
			return;
		}
		loadImage(url, imageView, getStubImage(resId, reqWidth, reqHeight),
				reqWidth, reqHeight, reqCornerRadius, reqMargin);
	}
	
	public void loadImage(String url, ImageView imageView, int resId,
			int reqWidth, int reqHeight, int reqMargin) {
		GLog.d(TAG, "request       : " + url);
		if (!isValidUrl(url)) {
			GLog.e(TAG, "invalid       : " + url);
			return;
		}
		loadImage(url, imageView, getStubImage(resId, reqWidth, reqHeight),
				reqWidth, reqHeight, -1, reqMargin);
	}
	
	public void loadImage(String url, ImageView imageView, int resId,
			int reqWidth, int reqHeight) {
		GLog.d(TAG, "request       : " + url);
		if (!isValidUrl(url)) {
			GLog.e(TAG, "invalid       : " + url);
			return;
		}
		loadImage(url, imageView, getStubImage(resId, reqWidth, reqHeight),
				reqWidth, reqHeight, -1, 0);
	}
	
	private BitmapWorkerTask mBitmapTask;

	  /**
	   * Loads the image from the cache or fetches the image.
	   * 
	   * @param url Url of the image
	   * @param listener
	   */
	  public void loadImage(String url, int reqWidth, int reqHeight, IconDownloadListener listener) {
		GLog.d(TAG, "request       : " + url);
		if (!isValidUrl(url)) {
			GLog.e(TAG, "invalid       : " + url);
			return;
		}
	    Bitmap bitmap = null;
	    if (mImageCache != null) {
	      // tries to get the bitmap from memory first
	      bitmap = mImageCache.getFromMemCache(url);
	    }

	    if (bitmap != null) {
	      if (listener != null) {
	        listener.onSuccess(bitmap);
	      }
	    } else {
	      // declare a new bitmap worker task
	      mBitmapTask = new BitmapWorkerTask(url, reqWidth, reqHeight, listener);

	      // download and put the bitmap on the cache
	      Request req = new Request(url, mBitmapTask, mTaskExecutor);
	      mRequestQueue.putRequest(req);
	    }
	  }
	  
	  /**
	   * Queries the cache for the Bitmap, returns it if it is found
	   * 
	   * @param key the key that represents the Bitmap on the cache
	   * @return Bitmap from the cache if discovered, null if not found
	   */
	  public Bitmap getBitmap(String key) {
	    // check to see if the ImageChace is null
	    if (mImageCache == null) {
	      GLog.e(TAG, "ImageChace is null. Must instantiate first.");
	      return null;
	    }
	    // looks for the Bitmap in on the disk
	    Bitmap b = mImageCache.getFromDisk(key);
	    if (b != null) {
	      return b;
	    }

	    // looks for the Bitmap on the cache
	    b = mImageCache.getFromMemCache(key);
	    // returns the image or a null value
	    return b;
	  }

	private void loadImage(String url, ImageView imageView,
			Bitmap loadingBitmap, int reqWidth, int reqHeight, int reqCornerRadius, int reqMargin) {
		Bitmap bitmap = null;
		if (mImageCache != null) {
			bitmap = mImageCache.getFromMemCache(getCacheKey(url, reqCornerRadius));
		}

		if (cancelPotentialWork(url, imageView) && bitmap == null) {
			final ImageViewBitmapWorkerTask task = new ImageViewBitmapWorkerTask(
					imageView, reqWidth, reqHeight, reqCornerRadius, reqMargin);
			Request req = new Request(url, task, mTaskExecutor);
			final AsyncDrawable asyncDrawable = new AsyncDrawable(
					mContext.getResources(), loadingBitmap, req);
			if (imageView != null) {
				imageView.setImageDrawable(asyncDrawable);
			}

			mRequestQueue.putRequest(req);
		} else if (bitmap != null && imageView != null) {
			if (reqCornerRadius > 0) {
				imageView.setImageDrawable(mBitmapUtil.getRoundedCornerDrawable(bitmap, reqCornerRadius, reqMargin));
			} else {
				imageView.setImageBitmap(bitmap);
			}
		}
	}
	
	private String getCacheKey(String url, int type) {
		String key = url;
		if (type > 0) {
			key += "_" + type;
		}
		return key;
	}
	
	private Bitmap getStubImage(int resourceId, int requestedWidth, int requestedHeight) {
		if (resourceId < 0) {
			return null;
		}
		String resKey = String.valueOf(resourceId) + String.valueOf(requestedWidth) + String.valueOf(requestedHeight);
		Bitmap bitmap = null;
		
	    if (!mLoadingImages.containsKey(resKey)) {
	    	try {
				bitmap = mBitmapUtil.decodeResourceBitmapAndScale(mContext, requestedWidth, requestedHeight, resourceId, false);
				if (bitmap == null) {
					return null;
				}
				mLoadingImages.put(resKey, bitmap);
			} catch (NotFoundException e) {
	            GLog.printStackTrace(TAG, e);
	        }
		 } else {
			 bitmap = mLoadingImages.get(resKey);
		 }
		return bitmap;
	}
	
	private boolean isValidUrl(String url) {
		if (TextUtils.isEmpty(url)) {
			return false;
		}
		if (!url.startsWith("http")) {
			return false;
		}
		return true;
	}
	
	public void clearBitmap(View view) {
	    if (view instanceof ViewGroup) {
	      ViewGroup group = (ViewGroup) view;
	      int childNum = group.getChildCount();
	      for (int i = 0; i < childNum; i++) {
	        View child = group.getChildAt(i);
	        if (child instanceof ViewGroup) {
	          clearBitmap(child);
	        } else if (child instanceof ImageView) {
	          ImageView imageView = (ImageView) child;
	          Drawable drawable = imageView.getDrawable();
	          if (drawable instanceof BitmapDrawable) {
	            BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
	            Bitmap bitmap = bitmapDrawable.getBitmap();
	            if (bitmap != null && !bitmap.isRecycled()) {
	              bitmap.recycle();
	            }
	          }
	          imageView.setImageDrawable(null);
	        }
	      }
	    }
	  }
	
	public void clearAll() {
	    if (mImageCache != null) {
	      mImageCache.close();
	    }
	    mImageLoadingWorkerThread.requestFinish();
	  }
	
	private static Request getBitmapRequest(ImageView imageView) {
	    if (imageView != null) {
	      final Drawable drawable = imageView.getDrawable();
	      if (drawable instanceof AsyncDrawable) {
	        final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
	        return asyncDrawable.getRequest();
	      }
	    }
	    return null;
	  }
	
	private boolean cancelPotentialWork(Object data, ImageView imageView) {
	    final Request request = getBitmapRequest(imageView);
	    if (request != null) {
	      final ImageViewBitmapWorkerTask bitmapWorkerTask =
	          (ImageViewBitmapWorkerTask) request.getTask();

	      if (bitmapWorkerTask != null) {
	        final Object bitmapData = bitmapWorkerTask.data;
	        if (bitmapData == null || !bitmapData.equals(data)) {
	          bitmapWorkerTask.cancel(true);
	          mRequestQueue.removeRequest(request);
	          GLog.d(TAG, "cancelled " + String.valueOf(bitmapData));
	        } else {
	          return false;
	        }
	      }
	    }
	    return true;
	  }
	
	private static class AsyncDrawable extends BitmapDrawable {
	    private Request mRequest;

	    public AsyncDrawable(Resources res, Bitmap bitmap, Request request) {
	      super(res, bitmap);
	      mRequest = request;
	    }

	    public Request getRequest() {
	      return mRequest;
	    }
	  }
	
	private class ImageViewBitmapWorkerTask extends Task<String, Void, Bitmap> {
	    private Object data;
	    private int mWidth;
	    private int mHeight;
	    private int mCornerRadius;
	    private int mMargin;
	    private final WeakReference<ImageView> mImageViewReference;
	    
	    public ImageViewBitmapWorkerTask(ImageView imageView, int reqWidth, int reqHeight) {
	    	this(imageView, reqWidth, reqHeight, -1, 0);
	    }
	    
		public ImageViewBitmapWorkerTask(ImageView imageView, int reqWidth,
				int reqHeight, int cornerRadius, int margin) {
			mImageViewReference = new WeakReference<ImageView>(imageView);
			mWidth = reqWidth;
			mHeight = reqHeight;
			mCornerRadius = cornerRadius;
			mMargin = margin;
		}

	    @Override
	    protected Bitmap doInBackground(String... params) {
	      data = params[0];
	      final String dataString = String.valueOf(data);
	      final String cacheKey = getCacheKey(dataString, mCornerRadius);
	      Bitmap bitmap = null;

	      if (mImageCache != null && !isCancelled() && getAttachedImageView() != null) {
	        bitmap = mImageCache.getFromDisk(cacheKey);

	        if (bitmap == null) {
	        	try {
					bitmap = mBitmapUtil.decodeInputStream(mImageDownloader.getStream(new URI(dataString)));
					GLog.d(TAG, "Image URI: " + dataString);
					GLog.d(TAG, bitmap == null ?  "bitmap is null" : "bitmap not null");
					if (mCornerRadius > 0) {
						bitmap = mBitmapUtil.scaleBitmapExactly(bitmap, mWidth, mHeight);
					} else {
						bitmap = mBitmapUtil.scaleBitmap(bitmap, mWidth, mHeight, true);
					}
				} catch (IOException e) {
					GLog.e(TAG, e.toString());
					// e.printStackTrace();
				} catch (URISyntaxException e) {
					GLog.e(TAG, e.toString());
					// e.printStackTrace();
				}
	        }
	      }


	      if (bitmap != null && mImageCache != null) {
	        mImageCache.put(cacheKey, bitmap);
	      }

	      return bitmap;
	    }

	    @Override
	    protected void onPostExecute(Bitmap bitmap) {
	      final ImageView imageView = getAttachedImageView();
	      if (isCancelled()) {
	        bitmap = null;
	        return;
	      }
	      mRequestQueue.notifyCompleteRequest();

	      if (bitmap != null && imageView != null) {
	    	  if (mCornerRadius > 0) {
	    		  imageView.setImageDrawable(mBitmapUtil.getRoundedCornerDrawable(bitmap, mCornerRadius, mMargin));
	    	  } else {
	    		  imageView.setImageBitmap(bitmap);
	    	  }	  
	      }
	    }

	    private ImageView getAttachedImageView() {
	      final ImageView imageView = mImageViewReference.get();
	      final Request request = getBitmapRequest(imageView);
	      if (request != null) {
	        final ImageViewBitmapWorkerTask bitmapWorkerTask =
	            (ImageViewBitmapWorkerTask) request.getTask();

	        if (this == bitmapWorkerTask && imageView != null) {
	          return imageView;
	        }
	      }
	      return null;
	    }
	  }

	  private class BitmapWorkerTask extends Task<String,Void,Bitmap> {
	    private int mWidth;
	    private int mHeight;
	    private String mUrl;
	    private WeakReference<IconDownloadListener> mWeakListener;

	    public BitmapWorkerTask(String url, int reqWidth, int reqHeight, IconDownloadListener listener) {
	      mUrl = url;
	      mWidth = reqWidth;
	      mHeight = reqHeight;
	      mWeakListener = new WeakReference<IconDownloadListener>(listener);
	    }

	    @Override
	    protected Bitmap doInBackground(String... params) {
	      Bitmap bitmap = null;

	      if (mImageCache != null && !isCancelled()) {
	        bitmap = mImageCache.getFromDisk(mUrl);
	      }

	      if (bitmap == null) {
	    	  try {
				bitmap = mBitmapUtil.decodeInputStream(mImageDownloader.getStream(new URI(mUrl)));
				bitmap = mBitmapUtil.scaleBitmap(bitmap, mWidth, mHeight, true);
			} catch (IOException e) {
				GLog.e(TAG, e.toString());
				// e.printStackTrace();
			} catch (URISyntaxException e) {
				GLog.e(TAG, e.toString());
				// e.printStackTrace();
			}
	      }

	      if (bitmap != null && mImageCache != null) {
	        mImageCache.put(mUrl, bitmap);
	      }
	      return bitmap;
	    }

	    @Override
	    protected void onPostExecute(Bitmap bitmap) {
	      if (isCancelled()) {
	        return;
	      }
	      mRequestQueue.notifyCompleteRequest();
	      IconDownloadListener listener = getAttachedListener();
	      // we need to issue a failure to the listener, so we call one here if there are no bitmaps to return
	      if(bitmap == null && listener != null){
	    	  
	    	  listener.onFailure(HttpURLConnection.HTTP_INTERNAL_ERROR, null, "Failure on retreival of the Bitmap");
	      }
	      else if (listener != null) {
	    	  listener.onSuccess(bitmap);
	      }
	    }
	    
	    private IconDownloadListener getAttachedListener() {
	    	if (mWeakListener != null) {
	    		return mWeakListener.get();
	    	} else {
	    		return null;
	    	}
	    }
	  }
}
