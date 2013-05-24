package net.gree.asdk.core.imageloader.concurrent;

public interface ITaskExecutor {
	public <Params,Progress,Result> Task<Params,Progress,Result> execute(
			Task<Params,Progress,Result> task, Params... params);

		  public void execute(Runnable task);
}
