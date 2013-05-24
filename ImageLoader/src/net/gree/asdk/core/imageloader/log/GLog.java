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

package net.gree.asdk.core.imageloader.log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

/*
 * GLog provides a flexible logging system for both production and debugging. 
 * The actual level can be set using a corresponding enum, or as an integer. 
 * They will be interpreted according to ranges,
 * level<=0 Error, 1-25 == Warn, 26-50 == Info, 51-75 == Debug level>76 == verbose  
 */
/**
 * Gree internal Log class, in Gree sdk, please use this class instead of Android default Log class
 */
public class GLog {

  private GLog() {

  }

  public static final int ERROR = 0;
  public static final int WARN = 25;
  public static final int INFO = 50;
  public static final int DEBUG = 75;
  public static final int VERBOSE = 100;
  private static Handler mGLogHandler;
  private static GreeLooperThread mGLogThread;
  private static final int WRITE = 1;
  private static final int FLUSH = 2;
  static {
    mGLogThread = new GreeLooperThread() {
      @Override
      protected void handleGreeMessage(Message msg) {
        switch (msg.what) {
          case WRITE:
            String date = new SimpleDateFormat("yyyy/MM/dd:HH:mm:ss.SSS", Locale.US).format(new Date());
            try {
              GLogInfo info = (GLogInfo) msg.obj;
              if (info == null) {
                return;
              }
              String logLevel = noNull(info.mLevel);
              String tag = noNull(info.mTag);
              String message = noNull(info.mMessage);
              logStream.write(date + "|" + logLevel + "|" + tag + "|" + message + "\n");
              if (autoFlush) {
                logStream.flush();
              }
            } catch (IOException ex) {
              Log.e("GLog", ex.toString());
            }
            break;
          case FLUSH:
            try {
              if (logStream != null && fileOutput) {
                logStream.flush();
              }
            } catch (IOException ex) {
              e("GLog", ex.toString());
            }
            break;
          default:
            break;
        }
      }
    };
    mGLogThread.start();
    mGLogHandler = mGLogThread.getHandler();
  }


  /**
   * LogLevel enum
   */
  public static enum LogLevel {
    Verbose(VERBOSE), Debug(DEBUG), Info(DEBUG), Warn(WARN), Error(ERROR);

    private int mCode;

    private LogLevel(int code) {
      this.mCode = code;
    }

    private static LogLevel getLogLevel(int code) {
      if (code <= ERROR) {
        return Error;
      } else if (code <= WARN) {
        return Warn;
      } else if (code <= INFO) {
        return Info;
      } else if (code <= DEBUG) {
        return Debug;
      } else {
        return Verbose;
      }
    }

    /**
     * Get the Code in Integer
     * 
     * @return level code in int
     */
    public int getCode() {
      return mCode;
    }
  }

  /**
   * Set the level with integer
   * 
   * @param level
   */
  public static void setLevel(int level) {
    GLog.sLevel = LogLevel.getLogLevel(level);
  }


  /**
   * Get the static LogLevel object
   * 
   * @return level
   */
  public static LogLevel getLevel() {
    return sLevel;
  }

  /**
   * Log as verbose mode, depending on the current log status
   * 
   * @param tag
   * @param msg
   */
  public static void v(String tag, String msg) {
    if (GLog.sLevel.getCode() >= VERBOSE) {
      msg = noNull(msg);
      Log.v(tag, msg);
      logf("VERBOSE", tag, msg);
    }
  }

  /**
   * Log as debug mode, depending on the current log status
   * 
   * @param tag
   * @param msg
   */
  public static void d(String tag, String msg) {
    if (GLog.sLevel.getCode() >= DEBUG) {
      msg = noNull(msg);
      Log.d(tag, msg);
      logf("DEBUG", tag, msg);
    }
  }


  /**
   * Log as info mode, depending on the current log status
   * 
   * @param tag
   * @param msg
   */
  public static void i(String tag, String msg) {
    if (GLog.sLevel.getCode() >= INFO) {
      msg = noNull(msg);
      Log.i(tag, msg);
      logf("Info ", tag, msg);
    }
  }

  /**
   * Log as warning mode, depending on the current log status
   * 
   * @param tag
   * @param msg
   */
  public static void w(String tag, String msg) {
    if (GLog.sLevel.getCode() >= WARN) {
      msg = noNull(msg);
      Log.w(tag, msg);
      logf("Warn", tag, msg);
    }
  }

  /**
   * Log as error mode, depending on the current log status
   * 
   * @param tag
   * @param msg
   */
  public static void e(String tag, String msg) {
    if (GLog.sLevel.getCode() >= ERROR) {
      msg = noNull(msg);
      Log.e(tag, msg);
      logf("Error", tag, msg);
    }
  }

  /**
   * Log with file
   * 
   * @param level
   * @param tag
   * @param msg
   * @TODO review and add Unit Test
   */
  public static void logf(final String level, final String tag, final String msg) {
    if (logStream != null && fileOutput) {
      mGLogHandler = mGLogThread.getHandler();
      if (mGLogHandler == null) {
        return;
      }
      GLogInfo info = new GLogInfo();
      info.mMessage = msg;
      info.mTag = tag;
      info.mLevel = level;
      mGLogHandler.sendMessage(Message.obtain(mGLogHandler, WRITE, info));
    }
  }

  /**
   * Debug with File
   * 
   * @param path
   * @return result code
   * @TODO review and add Unit Test
   */
  public static boolean debugFile(String _path) {
    return debugFile(_path, false);
  }

  /**
   * Debug with File
   * 
   * @param path
   * @param truncate
   * @return result code
   * @TODO review and add Unit Test
   */
  public static boolean debugFile(String _path, boolean truncate) {
    fileOutput = false;
    GLog.path = _path;
    debugLog = new File(path);
    if (debugLog != null) {
      try {
        if (!debugLog.exists()) {
          debugLog.createNewFile();
        }
        logStream =
            new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(path, !truncate)));
        fileOutput = true;
      } catch (IOException ex) {
        e("GLog", ex.toString());
      }
    }
    return fileOutput;
  }

  /**
   * Close opened file
   * 
   * @TODO review and add Unit Test
   */
  public static void closeFile() {
    if (logStream != null && fileOutput) {
      try {
        logStream.close();
      } catch (IOException ex) {
        e("GLog", ex.toString());
      }
      fileOutput = false;
    }
  }

  /**
   * Flush
   * 
   * @TODO review and add Unit Test
   */
  public static void flush() {
    mGLogHandler = mGLogThread.getHandler();
    if (mGLogHandler == null) {
      return;
    }
    mGLogHandler.sendMessage(Message.obtain(mGLogHandler, FLUSH, null));
  }

  /**
   * Buffered IO is much faster, but crashing after logging without flushing is slower. Allow app to
   * control this.
   * 
   * @param flush Whether to flush after every log statement.
   * @TODO review and add Unit Test
   */
  public static void setAutoFlush(boolean flush) {
    autoFlush = flush;
  }

  private static String noNull(String msg) {
    if (msg == null) {
      return "null";
    }
    return msg;
  }

  /**
   * printStackTrace's wrapper
   * 
   * @param tag
   * @param e
   * @TODO review and add Unit Test
   */
  public static void printStackTrace(String tag, Exception e) {
    if (e == null) {
      return;
    }
    if (logStream == null || !fileOutput) {
      e.printStackTrace();
      return;
    }
    StringWriter sw = null;
    PrintWriter pw = null;

    sw = new StringWriter();
    pw = new PrintWriter(sw);
    e.printStackTrace(pw);
    String trace = sw.toString();
    w(tag, trace);
    try {
      if (sw != null) {
        sw.close();
      }
      if (pw != null) {
        pw.close();
      }
    } catch (IOException ignore) {
      if (ignore != null) {
        GLog.e("printStackTrace", ignore.getMessage());
      }
    }
  }

  static LogLevel sLevel = LogLevel.Error;
  static String path = null;
  static File debugLog = null;
  static OutputStreamWriter logStream = null;
  static boolean fileOutput = false;
  static boolean autoFlush = true;
}
