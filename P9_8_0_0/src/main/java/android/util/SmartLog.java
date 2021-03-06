package android.util;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Calendar;
import java.util.Iterator;
import java.util.LinkedList;

public final class SmartLog {
    private static final int MAX_CACHE_LINES = 20;
    private static final int MAX_LOG_LINES = 1000;
    private static final int MAX_MESSAGE_ID = 100000;
    private static final String TAG = "SmartLog";
    private static final Singleton<SmartLog> gDefault = new Singleton<SmartLog>() {
        protected SmartLog create() {
            return new SmartLog();
        }
    };
    private LinkedList<String> mCacheLog;
    private Handler mHandler;
    private LinkedList<String> mLog;
    private int mMessageId;

    private class LogHandler extends Handler {
        LogHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            SmartLog.this.moveCacheToLog();
            SmartLog.this.addToLog("timeout(>1s):" + String.valueOf(msg.obj));
        }
    }

    public static class ReadOnlyLocalLog {
        private final SmartLog smartLog;

        ReadOnlyLocalLog(SmartLog log) {
            this.smartLog = log;
        }

        public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            this.smartLog.dump(fd, pw, args);
        }
    }

    private SmartLog() {
        this.mMessageId = -1;
        this.mLog = new LinkedList();
        this.mCacheLog = new LinkedList();
        HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        this.mHandler = new LogHandler(handlerThread.getLooper());
    }

    public static SmartLog getInstance() {
        return (SmartLog) gDefault.get();
    }

    public int startRecord(String log, int waitMillis) {
        int messageId = getMessageId();
        String msgLog = getLog(messageId, log);
        Message msg = new Message();
        msg.what = messageId;
        msg.obj = msgLog;
        addToCache("cache:" + msgLog);
        this.mHandler.sendMessageDelayed(msg, (long) waitMillis);
        return messageId;
    }

    public void endRecord(int messageId, String log) {
        String msgLog = getLog(messageId, log);
        if (this.mHandler.hasMessages(messageId)) {
            this.mHandler.removeMessages(messageId);
            addToCache("cache:" + msgLog);
            return;
        }
        addToLog("timeout:" + msgLog);
    }

    public synchronized void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        Iterator<String> itr = this.mLog.listIterator(0);
        while (itr.hasNext()) {
            pw.println((String) itr.next());
        }
    }

    public synchronized void reverseDump(FileDescriptor fd, PrintWriter pw, String[] args) {
        for (int i = this.mLog.size() - 1; i >= 0; i--) {
            pw.println((String) this.mLog.get(i));
        }
    }

    public ReadOnlyLocalLog readOnlyLocalLog() {
        return new ReadOnlyLocalLog(this);
    }

    private synchronized void addToLog(String msg) {
        this.mLog.add(msg);
        while (this.mLog.size() > MAX_LOG_LINES) {
            Log.e(TAG, (String) this.mLog.remove());
        }
    }

    private synchronized void addToCache(String msg) {
        this.mCacheLog.add(msg);
        while (this.mCacheLog.size() > 20) {
            this.mCacheLog.remove();
        }
    }

    private synchronized void moveCacheToLog() {
        while (this.mCacheLog.size() > 0) {
            addToLog((String) this.mCacheLog.remove());
        }
    }

    private synchronized String getLog(int msgId, String msg) {
        StringBuilder sb;
        long now = System.currentTimeMillis();
        sb = new StringBuilder();
        Calendar.getInstance().setTimeInMillis(now);
        sb.append(String.format("%tm-%td %tH:%tM:%tS.%tL", new Object[]{c, c, c, c, c, c}));
        return msgId + ":" + sb.toString() + msg;
    }

    private synchronized int getMessageId() {
        int i;
        i = this.mMessageId + 1;
        this.mMessageId = i;
        return i / MAX_MESSAGE_ID;
    }
}
