package net.gree.asdk.core.imageloader;

import net.gree.asdk.core.imageloader.concurrent.ITaskExecutor;
import net.gree.asdk.core.imageloader.concurrent.ITaskExecutorParameters;
import net.gree.asdk.core.imageloader.concurrent.TaskExecutorFactory;
import net.gree.asdk.core.imageloader.download.ImageDownloader;

public final class LoaderSettings {
	private static final int DEFAULT_DISK_CACHE_SIZE = 50 * 1024 * 1024;// google recommends 1MB, per app.
	private static final int DEFAUTL_MEMORY_CACHE_SIZE = (int) (Runtime.getRuntime().maxMemory() / 4); 
	
	public String mUniqueName;
	public boolean mEnableDiskCache = false;
	public boolean mEnableMemCache = false;
	public long mDiskCacheSize = 0;
	public int mMemCacheSize = 0;
	
	public ExecutorParameters mExecutorParameters;
	public ITaskExecutorParameters mTaskExecutorParameters;
	
	public ITaskExecutor mTaskExecutor;
	
	public ImageDownloader mImageDownloader;

	private LoaderSettings(String uniqueName) {
		mUniqueName = uniqueName;
	}
	
	public static class ExecutorParameters {
		public final int mCorePoolSize;
		public final int mMaxPoolSize;
		public final int mKeepAliveTime;
		
		public ExecutorParameters(int corePoolSize, int maxPoolSize, int keepAliveTime) {
			mCorePoolSize = corePoolSize;
			mMaxPoolSize = maxPoolSize;
			mKeepAliveTime = keepAliveTime;
		}
	}
	
	public static class SettingsBuilder {
		private LoaderSettings mSettings;
		
		public SettingsBuilder(String uniqueName) {
			mSettings = new LoaderSettings(uniqueName);
			
			withMemoryCache(DEFAUTL_MEMORY_CACHE_SIZE);
			withDiskCache(DEFAULT_DISK_CACHE_SIZE);
		}
		
		public SettingsBuilder withMemoryCache(int cacheSize) {
			mSettings.mEnableMemCache = true;
			mSettings.mMemCacheSize = cacheSize;
			return this;
		}
		
		public SettingsBuilder withDiskCache(int cacheSize) {
			mSettings.mEnableDiskCache = true;
			mSettings.mDiskCacheSize = cacheSize;
			return this;
		}
		
		public SettingsBuilder addExecutorParameters(int corePoolSize, int maxPoolSize, int keepAliveTime) {
			mSettings.mExecutorParameters = new ExecutorParameters(corePoolSize, maxPoolSize, keepAliveTime);
			return this;
		}
		
		public SettingsBuilder withTaskExecutor(int type) {
			mSettings.mTaskExecutorParameters = new ITaskExecutorParameters() {

				@Override
				public int corePoolSize() {
					return mSettings.mExecutorParameters.mCorePoolSize;
				}

				@Override
				public int maxPoolSize() {
					return mSettings.mExecutorParameters.mMaxPoolSize;
				}

				@Override
				public int keepAlive() {
					return mSettings.mExecutorParameters.mKeepAliveTime;
				}
				
			};
			mSettings.mTaskExecutor = TaskExecutorFactory.getTaskExecutor(type, mSettings.mTaskExecutorParameters);
			return this;
		}
		
		public SettingsBuilder setImageDownloader(ImageDownloader imageDownloader) {
			mSettings.mImageDownloader = imageDownloader;
			return this;
		}
		
		public LoaderSettings build() {
			return mSettings;
		}
	}
}
