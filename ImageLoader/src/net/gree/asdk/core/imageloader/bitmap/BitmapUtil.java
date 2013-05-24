/**
 * Copyright 2012 Novoda Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.gree.asdk.core.imageloader.bitmap;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ComposeShader;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.io.*;

/**
 * Utility class abstract the usage of the BitmapFactory. It is shielding the users of this class from bugs and OutOfMemory exceptions.
 */
public class BitmapUtil {

    private static final int BUFFER_SIZE = 64 * 1024;

    public Bitmap decodeFile(File f, int width, int height) {
        updateLastModifiedForCache(f);
        int suggestedSize = height;
        if (width > height) {
            suggestedSize = width;
        }
        Bitmap unscaledBitmap = decodeFile(f, suggestedSize);
        if (unscaledBitmap == null) {
            return null;
        }
        return unscaledBitmap;
    }

    /**
     * Use decodeFileAndScale(File, int, int, boolean) instead.
     *
     * @param f
     * @param width
     * @param height
     * @return
     */
    @Deprecated
    public Bitmap decodeFileAndScale(File f, int width, int height) {
        return decodeFileAndScale(f, width, height, false);
    }

    public Bitmap decodeFileAndScale(File f, int width, int height, boolean upsampling) {
        Bitmap unscaledBitmap = decodeFile(f, width, height);
        if (unscaledBitmap == null) {
            return null;
        }
        return scaleBitmap(unscaledBitmap, width, height, upsampling);
    }

    /**
     * use {@link decodeResourceBitmapAndScale} instead
     *
     * @param c
     * @param width
     * @param height
     * @param resourceId
     * @return
     */
    @Deprecated
    public Bitmap scaleResourceBitmap(Context c, int width, int height, int resourceId) {
        return decodeResourceBitmapAndScale(c, width, height, resourceId, false);
    }

    public Bitmap decodeResourceBitmap(Context c, int width, int height, int resourceId) {
        Bitmap unscaledBitmap = null;
        try {
            unscaledBitmap = BitmapFactory.decodeResource(c.getResources(), resourceId);
            return unscaledBitmap;
        } catch (final Throwable e) {
            // calling gc does not help as is called anyway
            // http://code.google.com/p/android/issues/detail?id=8488#c80
            // System.gc();
        }
        return null;
    }

    public Bitmap decodeResourceBitmapAndScale(Context c, int width, int height, int resourceId, boolean upsampling) {
        Bitmap unscaledBitmap = null;
        try {
            unscaledBitmap = BitmapFactory.decodeResource(c.getResources(), resourceId);
            return scaleBitmap(unscaledBitmap, width, height, upsampling);
        } catch (final Throwable e) {
            // calling gc does not help as is called anyway
            // http://code.google.com/p/android/issues/detail?id=8488#c80
            // System.gc();
        }
        return null;
    }

    /*public Bitmap decodeResourceBitmap(ImageWrapper w, int resId) {
        return decodeResourceBitmap(w.getContext(), w.getWidth(), w.getHeight(), resId);
    }

    *//**
     * use {@link decodeResourceBitmapAndScale} instead. This method ignores the upsampling settings.
     *
     * @param w
     * @param resId
     * @return
     *//*
    @Deprecated
    public Bitmap scaleResourceBitmap(ImageWrapper w, int resId) {
        return decodeResourceBitmapAndScale(w.getContext(), w.getWidth(), w.getHeight(), resId, false);
    }

    public Bitmap decodeResourceBitmapAndScale(ImageWrapper w, int resId, boolean upsampling) {
        return decodeResourceBitmapAndScale(w.getContext(), w.getWidth(), w.getHeight(), resId, upsampling);
    }*/

    /**
     * Calls {@link scaleBitmap(Bitmap, int, int, boolean)} with upsampling disabled.
     * <p/>
     * This method ignores the upsampling settings.
     *
     * @param b
     * @param width
     * @param height
     * @return
     */
    public Bitmap scaleBitmap(Bitmap b, int width, int height) {
        return scaleBitmap(b, width, height, false);
    }

    /**
     * Creates a new bitmap from the given one in the specified size respecting the size ratio of the origin image.
     *
     * @param b        original image
     * @param width    preferred width of the new image
     * @param height   preferred height of the new image
     * @param upsample if true smaller images than the preferred size are increased, if false the origin bitmap is returned
     * @return new bitmap if size has changed, otherwise original bitmap.
     */
    public Bitmap scaleBitmap(Bitmap b, int width, int height, boolean upsampling) {
        int imageHeight = b.getHeight();
        int imageWidth = b.getWidth();
        if (!upsampling && imageHeight <= height && imageWidth <= width) {
            return b;
        }
        int finalWidth = width;
        int finalHeight = height;
        if (imageHeight > imageWidth) {
            float factor = ((float) height) / ((float) imageHeight);
            finalHeight = new Float(imageHeight * factor).intValue();
            finalWidth = new Float(imageWidth * factor).intValue();
        } else {
            float factor = ((float) width) / ((float) imageWidth);
            finalHeight = new Float(imageHeight * factor).intValue();
            finalWidth = new Float(imageWidth * factor).intValue();
        }
        Bitmap scaled = null;
        try {
            scaled = Bitmap.createScaledBitmap(b, finalWidth, finalHeight, true);
        } catch (final Throwable e) {
            // calling gc does not help as is called anyway
            // http://code.google.com/p/android/issues/detail?id=8488#c80
            // System.gc();
        }
        // recycle b only if createScaledBitmap returned a new instance.
        if (scaled != b) {
            recycle(b);
        }
        return scaled;
    }
    
    public Bitmap scaleBitmapExactly(Bitmap b, int width, int height) {
    	Bitmap scaled = null;
        try {
            scaled = Bitmap.createScaledBitmap(b, width, height, true);
        } catch (final Throwable e) {
            // calling gc does not help as is called anyway
            // http://code.google.com/p/android/issues/detail?id=8488#c80
            // System.gc();
        }
        // recycle b only if createScaledBitmap returned a new instance.
        if (scaled != b) {
            recycle(b);
        }
        return scaled;
    }
    
    public Drawable getRoundedCornerDrawable(Bitmap bitmap, int cornerRadius, int margin) {
    	return new StreamDrawable(bitmap, cornerRadius, margin);
    }
    
    public Drawable getRoundedCornerDrawable(Bitmap bitmap, int width, int height, int cornerRadius, int margin) {
    	return new StreamDrawable(bitmap, width, height, cornerRadius, margin);
    }
    
    private static class StreamDrawable extends Drawable {
		private static final boolean USE_VIGNETTE = true;

		private final float mCornerRadius;
		private final RectF mRect = new RectF();
		private final BitmapShader mBitmapShader;
		private final Paint mPaint;
		private final int mMargin;
		private final int mWidth;
		private final int mHeight;
		
		private StreamDrawable(Bitmap bitmap, int width, int height, float cornerRadius, int margin) {
			mCornerRadius = cornerRadius;

			mBitmapShader = new BitmapShader(bitmap,
					Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);

			mPaint = new Paint();
			mPaint.setAntiAlias(true);
			mPaint.setShader(mBitmapShader);

			mMargin = margin;
			
			mWidth = width;
			mHeight = height;
		}
		
		private StreamDrawable(Bitmap bitmap, float cornerRadius, int margin) {
			this(bitmap, -1, -1, cornerRadius, margin);
		}

		@Override
		protected void onBoundsChange(Rect bounds) {
			super.onBoundsChange(bounds);
			int boundsWidth;
			int boundsHeight;
			if (mWidth > 0 && mHeight > 0) {
				boundsWidth = mWidth;
				boundsHeight = mHeight;
			} else {
				boundsWidth = bounds.width();
				boundsHeight = bounds.height();
			}
			mRect.set(mMargin, mMargin, boundsWidth - mMargin, boundsHeight - mMargin);

			if (USE_VIGNETTE) {
				RadialGradient vignette = new RadialGradient(
						mRect.centerX(), mRect.centerY() * 1.0f / 0.7f, mRect.centerX() * 1.3f,
						new int[] { 0, 0, 0x7f000000 }, new float[] { 0.0f, 0.7f, 1.0f },
						Shader.TileMode.CLAMP);
	
				Matrix oval = new Matrix();
				oval.setScale(1.0f, 0.7f);
				vignette.setLocalMatrix(oval);
	
				mPaint.setShader(
						new ComposeShader(mBitmapShader, vignette, PorterDuff.Mode.SRC_OVER));
			}
		}

		@Override
		public void draw(Canvas canvas) {
			canvas.drawRoundRect(mRect, mCornerRadius, mCornerRadius, mPaint);
		}

		@Override
		public int getOpacity() {
			return PixelFormat.TRANSLUCENT;
		}

		@Override
		public void setAlpha(int alpha) {
			mPaint.setAlpha(alpha);
		}

		@Override
		public void setColorFilter(ColorFilter cf) {
			mPaint.setColorFilter(cf);
		}		
	}

    /**
     * Convenience method to decode an input stream as a bitmap using BitmapFactory.decodeStream without any parameter options.
     * <p/>
     * If decoding fails the input stream is closed.
     *
     * @param is input stream of image data
     * @return bitmap created from the given input stream.
     */
    public Bitmap decodeInputStream(InputStream is) {
        Bitmap bitmap = null;
        try {
            bitmap = BitmapFactory.decodeStream(is, null, null);
        } catch (final Throwable e) {
            // calling gc does not help as is called anyway
            // http://code.google.com/p/android/issues/detail?id=8488#c80
            // System.gc();
        } finally {
            closeSilently(is);
        }
        return bitmap;
    }

    private void recycle(Bitmap scaled) {
        try {
            scaled.recycle();
        } catch (Exception e) {
            //
        }
    }

    private void updateLastModifiedForCache(File f) {
        f.setLastModified(System.currentTimeMillis());
    }

    private Bitmap decodeFile(File f, int suggestedSize) {
        Bitmap bitmap = null;
        FileInputStream fis = null;
        try {
            int scale = evaluateScale(f, suggestedSize);
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = scale;
            options.inTempStorage = new byte[BUFFER_SIZE];
            options.inPurgeable = true;
            fis = new FileInputStream(f);
            bitmap = BitmapFactory.decodeStream(fis, null, options);
        } catch (final Throwable e) {
            // calling gc does not help as is called anyway
            // http://code.google.com/p/android/issues/detail?id=8488#c80
            // System.gc();
        } finally {
            closeSilently(fis);
        }
        return bitmap;
    }

    private int evaluateScale(File f, int suggestedSize) {
        final BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        decodeFileToPopulateOptions(f, o);
        return calculateScale(suggestedSize, o.outWidth, o.outHeight);
    }

    private void decodeFileToPopulateOptions(File f, final BitmapFactory.Options o) {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(f);
            BitmapFactory.decodeStream(fis, null, o);
            closeSilently(fis);
        } catch (final Throwable e) {
            // calling gc does not help as is called anyway
            // http://code.google.com/p/android/issues/detail?id=8488#c80
            // System.gc();
        } finally {
            closeSilently(fis);
        }
    }

    private void closeSilently(Closeable c) {
        try {
            if (c != null) {
                c.close();
            }
        } catch (Exception e) {
        }
    }

    int calculateScale(final int requiredSize, int widthTmp, int heightTmp) {
        int scale = 1;
        while (true) {
            if ((widthTmp / 2) < requiredSize || (heightTmp / 2) < requiredSize) {
                break;
            }
            widthTmp /= 2;
            heightTmp /= 2;
            scale *= 2;
        }
        return scale;
    }

}