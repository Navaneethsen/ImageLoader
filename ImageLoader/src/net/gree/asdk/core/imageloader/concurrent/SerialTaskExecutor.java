package net.gree.asdk.core.imageloader.concurrent;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;


public class SerialTaskExecutor implements ITaskExecutor {
	private static Executor sExecutor = null;
	
	@Override
	public <Params, Progress, Result> Task<Params, Progress, Result> execute(
			Task<Params, Progress, Result> task, Params... params) {
		return task.execute(getExecutor(), params);
	}

	@Override
	public void execute(Runnable task) {
		getExecutor().execute(task);
	}
	
	private static Executor getExecutor() {
	    if (sExecutor == null) {
	      sExecutor = Executors.newSingleThreadExecutor();
	    }
	    return sExecutor;
	  }
}
