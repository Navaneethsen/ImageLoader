/*
 * Copyright 2012 GREE, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.gree.asdk.core.imageloader.concurrent;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ParallelTaskExecutor implements ITaskExecutor {
  private static final int DEFAULT_CORE_POOL_SIZE = 5;
  private static final int DEFAULT_MAX_POOL_SIZE = 128;
  private static final int DEFAULT_KEEP_ALIVE = 1000;

  private static Executor sExecutor = null;
  private static ITaskExecutorParameters sParameters;
  
  public ParallelTaskExecutor(ITaskExecutorParameters parameters) {
	  sParameters = parameters;
  }

  public <Params,Progress,Result> Task<Params,Progress,Result> execute(
      Task<Params,Progress,Result> task, Params... params) {
    return task.execute(getExecutor(), params);
  }

  public void execute(Runnable task) {
    getExecutor().execute(task);
  }

  private static Executor getExecutor() {
    if (sExecutor == null) {
      int coreSize = get(sParameters.corePoolSize(), DEFAULT_CORE_POOL_SIZE);
      int maxSize = get(sParameters.maxPoolSize(), DEFAULT_MAX_POOL_SIZE);
      int keepAlive = get(sParameters.keepAlive(), DEFAULT_KEEP_ALIVE);
      sExecutor =
          new ThreadPoolExecutor(coreSize, maxSize, keepAlive, TimeUnit.MILLISECONDS,
              new LinkedBlockingQueue<Runnable>(), new ThreadFactory() {
                private final AtomicInteger mCount = new AtomicInteger(1);

                public Thread newThread(Runnable r) {
                  return new Thread(r, Task.class.getSimpleName() + " #"
                      + mCount.getAndIncrement());
                }
              });
    }
    return sExecutor;
  }
  
  private static int get(int setValue, int defaultValue) {
	    return setValue > 0 ? setValue : defaultValue;
	  }
}
