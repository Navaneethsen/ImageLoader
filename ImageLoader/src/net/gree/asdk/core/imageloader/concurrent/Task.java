package net.gree.asdk.core.imageloader.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import net.gree.asdk.core.imageloader.log.GLog;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Process;

public abstract class Task<Params,Progress,Result> {
  private static final String TAG = Task.class.getSimpleName();

  private static final int MESSAGE_POST_RESULT = 0x1;
  private static final int MESSAGE_POST_PROGRESS = 0x2;

  private static final InternalHandler sHandler = new InternalHandler();

  private volatile Status mStatus = Status.PENDING;

  private final WorkerCallable<Params,Result> mWorker;
  private final FutureTask<Result> mFuture;

  private final AtomicBoolean mCancelled = new AtomicBoolean();
  private final AtomicBoolean mTaskInvoked = new AtomicBoolean();


  public Task() {
    this(Process.THREAD_PRIORITY_BACKGROUND);
  }

  public Task(final int threadPriority) {
    mWorker = new WorkerCallable<Params,Result>() {
      public Result call() throws Exception {
        mTaskInvoked.set(true);

        Process.setThreadPriority(threadPriority);
        return postResult(doInBackground(mParams));
      }
    };

    mFuture = new FutureTask<Result>(mWorker) {
      protected void done() {
          try {
            postResultIfNotInvoked(get());
          } catch (InterruptedException e) {
            GLog.printStackTrace(TAG, e);
          } catch (ExecutionException e) {
            throw new RuntimeException("An error occured while executing doInBackground()", e.getCause());
          } catch (CancellationException e) {
            postResultIfNotInvoked(null);
          }
      }
    };
  }

  private void postResultIfNotInvoked(Result result) {
    if (!mTaskInvoked.get()) {
      postResult(result);
    }
  }

  @SuppressWarnings("unchecked")
  private Result postResult(Result result) {
    sHandler.obtainMessage(MESSAGE_POST_RESULT, new TaskResult<Result>(this, result))
        .sendToTarget();
    return result;
  }

  /**
   * Indicates the current status of the task. Each status will be set only once
   * during the lifetime of a task.
   */
  public enum Status {
    /** Indicates that the task has not been executed yet. */
    PENDING,
    /** Indicates that the task is running. */
    RUNNING,
    /** Indicates that {@link AsyncTask#onPostExecute} has finished. */
    FINISHED,
  }

  /**
   * Returns the current status of this task.
   * @return The current status.
   */
  public final Status getStatus() { return mStatus; }

  /**
   * Override this method to perform a computation on a background thread. The
   * specified parameters are the parameters passed to {@link #execute}
   * by the caller of this task.
   *
   * This method can call {@link #publishProgress} to publish updates
   * on the UI thread.
   *
   * @param params The parameters of the task.
   * @return A result, defined by the subclass of this task.
   */
  protected abstract Result doInBackground(Params... params);

  /**
   * Runs on the UI thread before {@link #doInBackground}.
   */
  protected void onPreExecute() { }

  /**
   * <p>Runs on the UI thread after {@link #doInBackground}. The
   * specified result is the value returned by {@link #doInBackground}.</p>
   *
   * <p>This method won't be invoked if the task was cancelled.</p>
   *
   * @param result The result of the operation computed by {@link #doInBackground}.
   */
  protected void onPostExecute(Result result) { }

  /**
   * Runs on the UI thread after {@link #publishProgress} is invoked.
   * The specified values are the values passed to {@link #publishProgress}.
   *
   * @param values The values indicating progress.
   */
  protected void onProgressUpdate(Progress... values) { }

  /**
   * <p>Applications should preferably override {@link #onCancelled(Object)}.
   * This method is invoked by the default implementation of
   * {@link #onCancelled(Object)}.</p>
   *
   * <p>Runs on the UI thread after {@link #cancel(boolean)} is invoked and
   * {@link #doInBackground(Object[])} has finished.</p>
   */
  protected void onCancelled() { }

  /**
   * <p>Runs on the UI thread after {@link #cancel(boolean)} is invoked and
   * {@link #doInBackground(Object[])} has finished.</p>
   *
   * <p>The default implementation simply invokes {@link #onCancelled()} and
   * ignores the result. If you write your own implementation, do not call
   * <code>super.onCancelled(result)</code>.</p>
   *
   * @param result The result, if any, computed in
   *               {@link #doInBackground(Object[])}, can be null
   */
  protected void onCancelled(Result result) {
    onCancelled();
  }

  /**
   * Returns <tt>true</tt> if this task was cancelled before it completed
   * normally. If you are calling {@link #cancel(boolean)} on the task,
   * the value returned by this method should be checked periodically from
   * {@link #doInBackground(Object[])} to end the task as soon as possible.
   *
   * @return <tt>true</tt> if task was cancelled before it completed
   */
  public final boolean isCancelled() {
    return mCancelled.get();
  }

  /**
   * <p>Attempts to cancel execution of this task.  This attempt will
   * fail if the task has already completed, already been cancelled,
   * or could not be cancelled for some other reason. If successful,
   * and this task has not started when <tt>cancel</tt> is called,
   * this task should never run. If the task has already started,
   * then the <tt>mayInterruptIfRunning</tt> parameter determines
   * whether the thread executing this task should be interrupted in
   * an attempt to stop the task.</p>
   *
   * <p>Calling this method will result in {@link #onCancelled(Object)} being
   * invoked on the UI thread after {@link #doInBackground(Object[])}
   * returns. Calling this method guarantees that {@link #onPostExecute(Object)}
   * is never invoked. After invoking this method, you should check the
   * value returned by {@link #isCancelled()} periodically from
   * {@link #doInBackground(Object[])} to finish the task as early as
   * possible.</p>
   *
   * @param mayInterruptIfRunning <tt>true</tt> if the thread executing this
   *        task should be interrupted; otherwise, in-progress tasks are allowed
   *        to complete.
   *
   * @return <tt>false</tt> if the task could not be cancelled,
   *         typically because it has already completed normally;
   *         <tt>true</tt> otherwise
   */
  public final boolean cancel(boolean mayInterruptIfRunning) {
    mCancelled.set(true);
    return mFuture.cancel(mayInterruptIfRunning);
  }

  /**
   * Waits if necessary for the computation to complete, and then
   * retrieves its result.
   *
   * @return The computed result.
   *
   * @throws CancellationException If the computation was cancelled.
   * @throws ExecutionException If the computation threw an exception.
   * @throws InterruptedException If the current thread was interrupted
   *         while waiting.
   */
  public final Result get() throws InterruptedException, ExecutionException {
    return mFuture.get();
  }

  /**
   * Waits if necessary for at most the given time for the computation
   * to complete, and then retrieves its result.
   *
   * @param timeout Time to wait before cancelling the operation.
   * @param unit The time unit for the timeout.
   *
   * @return The computed result.
   *
   * @throws CancellationException If the computation was cancelled.
   * @throws ExecutionException If the computation threw an exception.
   * @throws InterruptedException If the current thread was interrupted
   *         while waiting.
   * @throws TimeoutException If the wait timed out.
   */
  public final Result get(long timeout, TimeUnit unit) throws InterruptedException,
      ExecutionException, TimeoutException {
    return mFuture.get(timeout, unit);
  }

  /**
   * This method can be invoked from {@link #doInBackground} to
   * publish updates on the UI thread while the background computation is
   * still running. Each call to this method will trigger the execution of
   * {@link #onProgressUpdate} on the UI thread.
   *
   * {@link #onProgressUpdate} will note be called if the task has been
   * canceled.
   *
   * @param values The progress values to update the UI with.
   */
  protected final void publishProgress(Progress... values) {
    if (!isCancelled()) {
      sHandler.obtainMessage(MESSAGE_POST_PROGRESS, new TaskResult<Progress>(this, values))
          .sendToTarget();
    }
  }

  Task<Params, Progress, Result> execute(Executor exec, Params...params) {
    // Util.startLog(TAG, "execute");
    switch (mStatus) {
      case RUNNING:
        throw new IllegalStateException("Cannot execute task: the task is already running.");
      case FINISHED:
        throw new IllegalStateException(
            "Cannot execute task: the task has already been executed (a task can be executed only once)");
    }
    mStatus = Status.RUNNING;

    mWorker.mParams = params;
    onPreExecute();
    exec.execute(mFuture);
    return this;
  }

  private void finish(Result result) {
    if (isCancelled()) {
      onCancelled(result);
    }
    else {
      onPostExecute(result);
    }
    mStatus = Status.FINISHED;
  }

  @SuppressWarnings("rawtypes")
  private static class InternalHandler extends Handler {
    public InternalHandler() {
      super(Looper.getMainLooper());
    }

    public void handleMessage(Message msg) {
      TaskResult result = (TaskResult) msg.obj;
      switch (msg.what) {
        case MESSAGE_POST_RESULT:
          // There is only one result.
          result.mTask.finish(result.mData[0]);
          break;
        case MESSAGE_POST_PROGRESS:
          result.mTask.onProgressUpdate(result.mData);
          break;
      }
    }
  }

  private static abstract class WorkerCallable<Params,Result> implements Callable<Result> {
    Params[] mParams;
  }

  @SuppressWarnings("rawtypes")
  private static class TaskResult<Data> {
    final Task mTask;
    final Data[] mData;

    TaskResult(Task task, Data... data) {
      mTask = task;
      mData = data;
    }
  }
}
