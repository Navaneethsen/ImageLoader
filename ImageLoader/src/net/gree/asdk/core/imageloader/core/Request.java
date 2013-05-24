package net.gree.asdk.core.imageloader.core;

import android.annotation.TargetApi;
import android.os.Build;
import net.gree.asdk.core.imageloader.concurrent.ITaskExecutor;
import net.gree.asdk.core.imageloader.concurrent.Task;

public class Request {
	private Task<String, ?, ?> mTask;
	private String mUrl;
	private ITaskExecutor mTaskExecutor;

	public Request(String url, Task<String, ?, ?> task,
			ITaskExecutor taskExecutor) {
		mUrl = url;
		mTask = task;
		mTaskExecutor = taskExecutor;
	}

	public String getUrl() {
		return mUrl;
	}

	public Task<String, ?, ?> getTask() {
		return mTask;
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public void execute() throws InterruptedException {
		mTaskExecutor.execute(mTask, mUrl);
	}
}
