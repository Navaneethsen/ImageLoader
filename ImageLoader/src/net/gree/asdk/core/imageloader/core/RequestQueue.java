/*
 * Copyright (C) 2012 The Android Open Source Project
 * Copyright 2012 GREE, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * NOTE: This file has been modified by GREE, Inc.
 * Modifications are licensed under the License.
 */
package net.gree.asdk.core.imageloader.core;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import net.gree.asdk.core.imageloader.log.GLog;

public class RequestQueue {
  private static final String TAG = RequestQueue.class.getSimpleName();
  private static final int MAXIMUM_TASK_COUNT = 5;

  private final BlockingQueue<Request> mQueue = new LinkedBlockingQueue<Request>();
  private volatile int mRequestCount = 0;
  
  public Request getRequest() {
    Request req = null;
    final int runCount;
    synchronized (this) {
      runCount = mRequestCount -  mQueue.size();
    }
    if (runCount > MAXIMUM_TASK_COUNT) {
      synchronized (this) {
        try {
          GLog.d(TAG, "get wait");
          this.wait();
        } catch (InterruptedException e) {
          GLog.printStackTrace(TAG, e);
        }
      }
    } else {
      try {
        req = mQueue.take();
      } catch (InterruptedException e) {
        GLog.printStackTrace(TAG, e);
        return null;
      }
      GLog.d(TAG, "get request.getUrl : " + req.getUrl());
    }
    return req;
  }
  
  public synchronized void putRequest(Request request) {
    try {
      GLog.d(TAG, "put request.getUrl : " + request.getUrl());
      mQueue.put(request);
      mRequestCount += 1;
    } catch (InterruptedException e) {
      GLog.printStackTrace(TAG, e);
    }
  }
  
  public synchronized void removeRequest(Request request) {
    if (request == null) {
      return;
    }
    mQueue.remove(request);
    mRequestCount -= 1;
    if (mRequestCount < 0) {
      mRequestCount = 0;
    }
    notifyAll();
  }
  public synchronized void notifyCompleteRequest() {
    mRequestCount -= 1;
    if (mRequestCount < 0) {
      mRequestCount = 0;
    }
    notifyAll();
  }
  
  public void removeAll() {
    for (Request item : mQueue) {
      mQueue.remove(item);
    }
  }
  public int getLength() {
    return mRequestCount;
  }
}
