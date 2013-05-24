package net.gree.asdk.core.imageloader.concurrent;

public class TaskExecutorFactory {
	public static final int TYPE_SERIAL = 1;
	public static final int TYPE_PARALLEL = 2;

	public static ITaskExecutor getTaskExecutor(int type, ITaskExecutorParameters parameters) {
		ITaskExecutor executor = null;
		switch (type) {
		case TYPE_SERIAL:
			executor = new SerialTaskExecutor();
			break;
		case TYPE_PARALLEL:
			executor = new ParallelTaskExecutor(parameters);
			break;
		}
		return executor;
	}
}
