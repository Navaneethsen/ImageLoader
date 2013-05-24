package net.gree.asdk.core.imageloader.concurrent;

public interface ITaskExecutorParameters {
	public int corePoolSize();
	public int maxPoolSize();
	public int keepAlive();
}
