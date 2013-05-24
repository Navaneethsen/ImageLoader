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

import net.gree.asdk.core.imageloader.log.GLog;

public class ImageLoadingThread extends Thread {
  private static final String TAG = ImageLoadingThread.class.getSimpleName();
  private volatile boolean mFinishRequested = false;
  private RequestQueue mRequestQueue;
  public ImageLoadingThread(RequestQueue reqQueue) {
    mRequestQueue = reqQueue;
  }
  
  @Override
  public void run() {
    try {
      while (!mFinishRequested) {
        Request req = mRequestQueue.getRequest();
        if (req != null) {
          req.execute();
        }
      }
    } catch (InterruptedException e) {
    } finally {
      GLog.d(TAG, "Image fetcher thread is finished.");
      mRequestQueue.removeAll();
    }
  }
  
  public void requestFinish() {
    mFinishRequested = true;
    interrupt();
  }
}
