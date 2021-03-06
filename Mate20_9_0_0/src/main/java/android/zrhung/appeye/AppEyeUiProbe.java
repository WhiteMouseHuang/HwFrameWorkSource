package android.zrhung.appeye;

import android.app.Activity;
import android.app.ActivityThread;
import android.os.FreezeScreenScene;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.MessageQueue;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.util.ZRHung;
import android.util.ZRHung.HungConfig;
import android.view.ViewRootImpl;
import android.zrhung.IAppEyeUiProbe;
import android.zrhung.ZrHungData;
import android.zrhung.ZrHungImpl;
import com.huawei.hsm.permission.StubController;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;

public final class AppEyeUiProbe extends ZrHungImpl implements IAppEyeUiProbe {
    private static final String MAIN_THREAD_MESSAGE_CALLBACK = "android.app.LoadedApk$ReceiverDispatcher$Args";
    private static final int MSG_DELAY_WATERLINE_DELAY = 2;
    private static final int MSG_DELAY_WATERLINE_FINE = 0;
    private static final int MSG_DELAY_WATERLINE_WARNING = 1;
    private static long MSG_DELAY_WATERMARK_DELAY = 3000;
    private static long MSG_DELAY_WATERMARK_WARNING = 1000;
    private static final String SYSTEMUI_PKG_NAME = "com.android.systemui";
    private static final String TAG = "ZrHung.AppEyeUiProbe";
    private static AppEyeUiProbe mSingleton;
    private AppEyeUIPChecker mChecker = new AppEyeUIPChecker();
    private Thread mCheckerThread = null;
    private boolean mEnableSystemUIChecking = false;
    private boolean mIsSystemUi = false;
    private String mPackageName;

    private final class AppEyeUIPChecker implements Runnable {
        private static final int FREEZE_HAPPENED = 2;
        private static final int INPUT_TIMEOUT_HAPPENED = 3;
        private static final int LENGTH_OF_ARRAY = 10;
        private static final int NO_EVENT_HAPPENED = 0;
        private static final int UPPER_BOUND_OF_ARRAY = 9;
        private static final int WARINIG_HAPPENED = 1;
        private long BLOCK_FREEZE_TIMEOUT = (this.BLOCK_WARNING_TIMEOUT * 2);
        private long BLOCK_INPUT_TIMEOUT = 4000;
        private long BLOCK_WARNING_TIMEOUT = 3000;
        private long DEFAULT_CHECK_INTERVAL = 1000;
        private long MSG_DELAY_FREEZE = (this.MSG_DELAY_WARNING * 2);
        private long MSG_DELAY_WARNING = 3000;
        private long VSYNC_CHECK_OFFSET = 1000;
        public int checkStateForDispatchTime = 0;
        public int checkStateForMessageDelay = 0;
        private boolean configReady = false;
        public Message curMessage = null;
        public Runnable curMessageCallback = null;
        public Handler curMessageTarget = null;
        public long delayStartTime = 0;
        public long dispatchTime = 0;
        public boolean dispatching = false;
        public boolean enabled = false;
        private long firstFdEventTime = 0;
        public long firstInputTime = 0;
        private boolean functionEnabled = true;
        public WeakReference<Activity> mCurrActivity = null;
        public StringBuilder mDelayedMsgCache = new StringBuilder();
        public String[] mDelayedMsgList = new String[10];
        private MessageQueue mMainQueue;
        public boolean outOfArrayBound = false;
        private int pid = Process.myPid();
        public int waterLine = 0;
        public int writeIndexOfArray = 0;

        public void run() {
            Log.d(AppEyeUiProbe.TAG, "Runnable thread started.");
            while (true) {
                getHungConfig();
                if (this.functionEnabled) {
                    synchronized (this) {
                        if (!(this.enabled || AppEyeUiProbe.this.mIsSystemUi)) {
                            this.checkStateForDispatchTime = 0;
                            this.checkStateForMessageDelay = 0;
                            if (!isCurrentActivityResume()) {
                                try {
                                    Log.d(AppEyeUiProbe.TAG, "not watching, wait.");
                                    wait();
                                    Log.d(AppEyeUiProbe.TAG, "restart watching");
                                } catch (InterruptedException e) {
                                }
                            }
                        }
                    }
                    long timeout = checkDispatchTimeout();
                    if (!AppEyeUiProbe.this.mIsSystemUi && this.checkStateForDispatchTime < 2) {
                        checkMessageDelay();
                    }
                    try {
                        Thread.sleep(timeout);
                    } catch (InterruptedException e2) {
                    }
                } else {
                    Log.d(AppEyeUiProbe.TAG, "apppuip fuction is not enabled, quit thread");
                    return;
                }
            }
            while (true) {
            }
        }

        private long checkDispatchTimeout() {
            if (this.dispatching) {
                long curTime = SystemClock.uptimeMillis();
                long elapsed = curTime - this.dispatchTime;
                if (elapsed >= this.BLOCK_FREEZE_TIMEOUT) {
                    if (this.checkStateForDispatchTime == 0 && isLastVsyncTimeout(this.BLOCK_WARNING_TIMEOUT)) {
                        this.checkStateForDispatchTime = 1;
                        sendAppEyeEvents((short) 257, elapsed);
                    }
                    if (this.checkStateForDispatchTime == 1) {
                        sendAppEyeEvents((short) 258, elapsed);
                        this.checkStateForDispatchTime = 2;
                        if (curTime - this.firstFdEventTime > this.BLOCK_INPUT_TIMEOUT && this.firstFdEventTime != 0 && isLastVsyncTimeout(this.BLOCK_FREEZE_TIMEOUT)) {
                            sendAppEyeEvents((short) 260, elapsed);
                            this.checkStateForDispatchTime = 3;
                        } else if (this.firstFdEventTime == 0 && peekEvent()) {
                            this.firstFdEventTime = SystemClock.uptimeMillis();
                        }
                    } else if (this.checkStateForDispatchTime == 2) {
                        if (curTime - this.firstFdEventTime > this.BLOCK_INPUT_TIMEOUT && this.firstFdEventTime != 0 && isLastVsyncTimeout(this.BLOCK_FREEZE_TIMEOUT)) {
                            sendAppEyeEvents((short) 260, elapsed);
                            this.checkStateForDispatchTime = 3;
                        } else if (this.firstFdEventTime == 0 && peekEvent()) {
                            this.firstFdEventTime = SystemClock.uptimeMillis();
                        }
                    }
                    return this.DEFAULT_CHECK_INTERVAL;
                } else if (elapsed >= this.BLOCK_WARNING_TIMEOUT) {
                    if (this.checkStateForDispatchTime == 0 && isLastVsyncTimeout(this.BLOCK_WARNING_TIMEOUT)) {
                        sendAppEyeEvents((short) 257, elapsed);
                        this.checkStateForDispatchTime = 1;
                        if (peekEvent()) {
                            this.firstFdEventTime = SystemClock.uptimeMillis();
                        }
                    }
                    if (this.firstFdEventTime == 0 && peekEvent()) {
                        this.firstFdEventTime = SystemClock.uptimeMillis();
                    }
                    return this.DEFAULT_CHECK_INTERVAL;
                } else if (this.BLOCK_INPUT_TIMEOUT < elapsed && elapsed < this.BLOCK_WARNING_TIMEOUT) {
                    return this.BLOCK_WARNING_TIMEOUT - elapsed;
                }
            }
            if (!(AppEyeUiProbe.this.mIsSystemUi || this.checkStateForDispatchTime != 1 || this.firstFdEventTime == 0)) {
                sendAppEyeEvents((short) 259, 0);
            }
            if (!AppEyeUiProbe.this.mIsSystemUi && (this.checkStateForDispatchTime == 2 || this.checkStateForDispatchTime == 3)) {
                Log.d(AppEyeUiProbe.TAG, "sendAppEye recover Events.");
                sendAppEyeEvents((short) 280, 0);
            }
            this.checkStateForDispatchTime = 0;
            this.firstFdEventTime = 0;
            return this.DEFAULT_CHECK_INTERVAL;
        }

        private void checkMessageDelay() {
            long curTime = SystemClock.uptimeMillis();
            if (this.waterLine == 2) {
                if (curTime - this.delayStartTime > this.MSG_DELAY_FREEZE) {
                    if (this.checkStateForMessageDelay == 1) {
                        sendAppEyeEvents((short) 261, 0);
                        if (this.firstInputTime == 0 || curTime - this.firstInputTime <= this.BLOCK_INPUT_TIMEOUT) {
                            this.checkStateForMessageDelay = 2;
                        } else {
                            sendAppEyeEvents((short) 263, 0);
                            this.checkStateForMessageDelay = 3;
                        }
                    } else if (this.checkStateForMessageDelay == 2 && this.firstInputTime != 0 && curTime - this.firstInputTime > this.BLOCK_INPUT_TIMEOUT) {
                        sendAppEyeEvents((short) 263, 0);
                        this.checkStateForMessageDelay = 3;
                    }
                    return;
                } else if (curTime - this.delayStartTime > this.MSG_DELAY_WARNING) {
                    Log.d(AppEyeUiProbe.TAG, "message delay warning");
                    this.mDelayedMsgCache.delete(0, this.mDelayedMsgCache.length());
                    this.mDelayedMsgCache.append(dumpDelayedMessageList());
                    this.checkStateForMessageDelay = 1;
                    return;
                }
            }
            if (this.checkStateForMessageDelay == 1 && this.waterLine == 1) {
                sendAppEyeEvents((short) 262, 0);
            }
            this.checkStateForMessageDelay = 0;
        }

        public void addToDelayedMsgList(String msg, long msgDelayed, long msgProcessed) {
            StringBuilder sb = new StringBuilder("Message: ");
            sb.append(msg);
            sb.append(",delayed ");
            sb.append(String.valueOf(msgDelayed));
            sb.append("ms,processed ");
            sb.append(String.valueOf(msgProcessed));
            sb.append("ms.");
            this.mDelayedMsgList[this.writeIndexOfArray] = sb.toString();
            if (this.writeIndexOfArray == 9) {
                this.writeIndexOfArray = 0;
                this.outOfArrayBound = true;
                return;
            }
            this.writeIndexOfArray++;
        }

        public String dumpDelayedMessageList() {
            StringBuilder sb = new StringBuilder();
            int i = 0;
            int i2;
            if (!this.outOfArrayBound) {
                while (true) {
                    i2 = i;
                    if (i2 >= this.writeIndexOfArray) {
                        break;
                    }
                    sb.append(this.mDelayedMsgList[i2]);
                    sb.append("\n");
                    i = i2 + 1;
                }
            } else {
                for (i2 = this.writeIndexOfArray; i2 < this.mDelayedMsgList.length; i2++) {
                    sb.append(this.mDelayedMsgList[i2]);
                    sb.append("\n");
                }
                while (true) {
                    i2 = i;
                    if (i2 >= this.writeIndexOfArray) {
                        break;
                    }
                    sb.append(this.mDelayedMsgList[i2]);
                    sb.append("\n");
                    i = i2 + 1;
                }
            }
            return sb.toString();
        }

        private void getHungConfig() {
            if (!this.configReady) {
                HungConfig cfg = ZRHung.getHungConfig(AppEyeUiProbe.this.mWpId);
                if (cfg == null) {
                    Log.w(AppEyeUiProbe.TAG, "Failed to get config from zrhung");
                    this.configReady = true;
                } else if (cfg.status != 0) {
                    if (cfg.status == -1 || cfg.status == -2) {
                        Log.w(AppEyeUiProbe.TAG, "config is not support or there is no config");
                        this.configReady = true;
                        this.functionEnabled = false;
                    }
                } else if (cfg.value == null) {
                    Log.w(AppEyeUiProbe.TAG, "Failed to get config from zrhung");
                    this.configReady = true;
                } else {
                    String[] configs = cfg.value.split(",");
                    this.functionEnabled = "1".equals(configs[0]);
                    for (int i = 1; i < configs.length; i++) {
                        long time = 0;
                        try {
                            time = Long.parseLong(configs[i]);
                            if (time != 0) {
                                switch (i) {
                                    case 1:
                                        this.BLOCK_WARNING_TIMEOUT = time;
                                        break;
                                    case 2:
                                        this.BLOCK_FREEZE_TIMEOUT = time;
                                        break;
                                    case 3:
                                        this.BLOCK_INPUT_TIMEOUT = time;
                                        break;
                                    case 4:
                                        AppEyeUiProbe.MSG_DELAY_WATERMARK_WARNING = time;
                                        break;
                                    case 5:
                                        AppEyeUiProbe.MSG_DELAY_WATERMARK_DELAY = time;
                                        break;
                                    case 6:
                                        this.DEFAULT_CHECK_INTERVAL = time;
                                        break;
                                    case 7:
                                        AppEyeUiProbe.this.mEnableSystemUIChecking = time == 1;
                                        break;
                                    default:
                                        break;
                                }
                            }
                        } catch (NumberFormatException e) {
                            Log.w(AppEyeUiProbe.TAG, "the config string cannot be parsed as long");
                        }
                    }
                    this.MSG_DELAY_WARNING = this.BLOCK_WARNING_TIMEOUT;
                    this.MSG_DELAY_FREEZE = this.BLOCK_FREEZE_TIMEOUT;
                    this.configReady = true;
                }
            }
        }

        private boolean peekEvent() {
            Activity tempActivity = null;
            if (this.mCurrActivity != null) {
                tempActivity = (Activity) this.mCurrActivity.get();
            }
            if (tempActivity != null) {
                ViewRootImpl viewRootImpl = tempActivity.getWindow().getDecorView().getViewRootImpl();
                if (viewRootImpl != null && viewRootImpl.peekEvent()) {
                    return true;
                }
            }
            return false;
        }

        private boolean isLastVsyncTimeout(long timeout) {
            MessageQueue mainQueue = getMainMessageQueue();
            if (mainQueue != null && SystemClock.uptimeMillis() - mainQueue.mLastVsyncEnd > timeout - this.VSYNC_CHECK_OFFSET) {
                return true;
            }
            Log.w(AppEyeUiProbe.TAG, "UIP freezing Happened but we still have vsync signal, exclude this case");
            this.functionEnabled = false;
            return false;
        }

        private MessageQueue getMainMessageQueue() {
            if (this.mMainQueue == null) {
                Looper mainLooper = Looper.getMainLooper();
                if (mainLooper != null) {
                    this.mMainQueue = mainLooper.getQueue();
                }
            }
            return this.mMainQueue;
        }

        private String getPackageName() {
            return ActivityThread.currentPackageName();
        }

        private String getActivityName() {
            return ActivityThread.currentActivityName();
        }

        private String getProcessName() {
            return ActivityThread.currentProcessName();
        }

        private int getUid() {
            int uid = 0;
            try {
                if (ActivityThread.currentApplication() != null) {
                    return ActivityThread.currentApplication().getApplicationContext().getPackageManager().getApplicationInfo(getPackageName(), 0).uid;
                }
                return uid;
            } catch (Exception e) {
                Log.w(AppEyeUiProbe.TAG, "could not get uid");
                return uid;
            }
        }

        private boolean isCurrentActivityResume() {
            Activity tempActivity = null;
            boolean isResume = false;
            if (this.mCurrActivity != null) {
                tempActivity = (Activity) this.mCurrActivity.get();
            }
            if (tempActivity != null) {
                try {
                    Field mResumed = Activity.class.getDeclaredField("mResumed");
                    mResumed.setAccessible(true);
                    isResume = mResumed.getBoolean(tempActivity);
                } catch (NoSuchFieldException e) {
                    Log.e(AppEyeUiProbe.TAG, "isCurrentActivityResume NoSuchFieldException", e);
                } catch (IllegalAccessException e2) {
                    Log.e(AppEyeUiProbe.TAG, "isCurrentActivityResume IllegalAccessException", e2);
                } catch (Exception e3) {
                    Log.e(AppEyeUiProbe.TAG, "isCurrentActivityResume exception", e3);
                }
            }
            String str = AppEyeUiProbe.TAG;
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("Current Activity:");
            stringBuilder.append(isResume);
            Log.d(str, stringBuilder.toString());
            return isResume;
        }

        private void sendAppEyeEvents(short wpID, long elapsedTime) {
            try {
                ZrHungData data = new ZrHungData();
                data.putInt(StubController.TABLE_COLUM_UID, getUid());
                data.putInt(FreezeScreenScene.PID_PARAM, this.pid);
                data.putString("packageName", getPackageName());
                data.putString(FreezeScreenScene.PROCESS_NAME, getProcessName());
                StringBuilder sb = new StringBuilder();
                String cmd = null;
                String messageInfo = AppEyeUiProbe.dumpMessage(AppEyeUiProbe.this.mChecker.curMessage, AppEyeUiProbe.this.mChecker.curMessageTarget, AppEyeUiProbe.this.mChecker.curMessageCallback);
                if (wpID != (short) 280) {
                    switch (wpID) {
                        case (short) 257:
                            if (AppEyeUiProbe.this.mIsSystemUi) {
                                wpID = (short) 284;
                            }
                            sb.append("APPEYE_UIP_WARNING\n");
                            sb.append("activityName = ");
                            sb.append(getActivityName());
                            sb.append("\n");
                            sb.append(messageInfo);
                            sb.append(", has taken ");
                            sb.append(String.valueOf(elapsedTime));
                            sb.append("ms");
                            StringBuilder stringBuilder = new StringBuilder();
                            stringBuilder.append("p=");
                            stringBuilder.append(this.pid);
                            cmd = stringBuilder.toString();
                            break;
                        case (short) 258:
                            if (AppEyeUiProbe.this.mIsSystemUi) {
                                wpID = (short) 285;
                            }
                            sb.append("APPEYE_UIP_FREEZE\n");
                            sb.append(messageInfo);
                            sb.append(", has taken ");
                            sb.append(String.valueOf(elapsedTime));
                            sb.append("ms");
                            cmd = "B";
                            break;
                        case (short) 259:
                            break;
                        case (short) 260:
                            if (AppEyeUiProbe.this.mIsSystemUi) {
                                wpID = (short) 286;
                            }
                            sb.append("APPEYE_UIP_INPUT\n");
                            sb.append(messageInfo);
                            sb.append(", has taken ");
                            sb.append(String.valueOf(elapsedTime));
                            sb.append("ms");
                            break;
                        case (short) 261:
                            sb.append("APPEYE_MTO_FREEZE\n");
                            sb.append("delayed messages are:\n");
                            sb.append(dumpDelayedMessageList());
                            sb.append("delayed messages 3s age are:\n");
                            sb.append(this.mDelayedMsgCache.toString());
                            cmd = "B";
                            break;
                        case (short) 262:
                            sb.append("APPEYE_MTO_SLOW\n");
                            sb.append("activityName = ");
                            sb.append(getActivityName());
                            sb.append("\n");
                            sb.append("delayed messages are:\n");
                            sb.append(dumpDelayedMessageList());
                            sb.append("delayed messages 3s age are:\n");
                            sb.append(this.mDelayedMsgCache.toString());
                            break;
                        case (short) 263:
                            sb.append("APPEYE_MTO_INPUT\n");
                            sb.append("delayed messages are:\n");
                            sb.append(dumpDelayedMessageList());
                            sb.append("delayed messages 3s age are:\n");
                            sb.append(this.mDelayedMsgCache.toString());
                            break;
                        default:
                            break;
                    }
                }
                sb.append("APPEYE_UIP_RECOVER\n");
                sb.append("delayed messages are:\n");
                sb.append(dumpDelayedMessageList());
                sb.append("delayed 6s messages are:\n");
                sb.append(this.mDelayedMsgCache.toString());
                if (!Log.HWINFO) {
                    cmd = "";
                }
                AppEyeUiProbe.this.sendAppEyeEvent(wpID, data, cmd, sb.toString());
            } catch (Exception ex) {
                String str = AppEyeUiProbe.TAG;
                StringBuilder stringBuilder2 = new StringBuilder();
                stringBuilder2.append("sendAppEyeEvent exception: ");
                stringBuilder2.append(ex);
                Log.d(str, stringBuilder2.toString());
            }
        }
    }

    public static synchronized AppEyeUiProbe get() {
        AppEyeUiProbe appEyeUiProbe;
        synchronized (AppEyeUiProbe.class) {
            if (mSingleton == null) {
                mSingleton = new AppEyeUiProbe("appeye_uiprobe");
            }
            appEyeUiProbe = mSingleton;
        }
        return appEyeUiProbe;
    }

    private AppEyeUiProbe(String wpName) {
        super(wpName);
        Log.d(TAG, "AppEyeUIP created.");
    }

    public boolean start(ZrHungData args) {
        synchronized (this) {
            if (this.mCheckerThread == null) {
                this.mCheckerThread = new Thread(this.mChecker);
                this.mCheckerThread.start();
            }
        }
        synchronized (this.mChecker) {
            this.mChecker.enabled = true;
            Log.d(TAG, "notify runnable to start.");
            this.mChecker.notifyAll();
        }
        return true;
    }

    public boolean stop(ZrHungData args) {
        Log.d(TAG, "stop checker.");
        this.mChecker.enabled = false;
        return true;
    }

    /* JADX WARNING: Removed duplicated region for block: B:22:0x004c  */
    /* JADX WARNING: Removed duplicated region for block: B:26:0x006e  */
    /* JADX WARNING: Removed duplicated region for block: B:25:0x006a  */
    /* JADX WARNING: Removed duplicated region for block: B:24:0x005e  */
    /* JADX WARNING: Removed duplicated region for block: B:23:0x0054  */
    /* JADX WARNING: Removed duplicated region for block: B:22:0x004c  */
    /* JADX WARNING: Removed duplicated region for block: B:26:0x006e  */
    /* JADX WARNING: Removed duplicated region for block: B:25:0x006a  */
    /* JADX WARNING: Removed duplicated region for block: B:24:0x005e  */
    /* JADX WARNING: Removed duplicated region for block: B:23:0x0054  */
    /* JADX WARNING: Removed duplicated region for block: B:22:0x004c  */
    /* JADX WARNING: Removed duplicated region for block: B:26:0x006e  */
    /* JADX WARNING: Removed duplicated region for block: B:25:0x006a  */
    /* JADX WARNING: Removed duplicated region for block: B:24:0x005e  */
    /* JADX WARNING: Removed duplicated region for block: B:23:0x0054  */
    /* JADX WARNING: Removed duplicated region for block: B:22:0x004c  */
    /* JADX WARNING: Removed duplicated region for block: B:26:0x006e  */
    /* JADX WARNING: Removed duplicated region for block: B:25:0x006a  */
    /* JADX WARNING: Removed duplicated region for block: B:24:0x005e  */
    /* JADX WARNING: Removed duplicated region for block: B:23:0x0054  */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public boolean check(ZrHungData args) {
        boolean z;
        String method = args.getString("method");
        int hashCode = method.hashCode();
        if (hashCode != 231233763) {
            if (hashCode != 1025827149) {
                if (hashCode != 1727640041) {
                    if (hashCode == 1923720383 && method.equals("beginDispatching")) {
                        z = false;
                        switch (z) {
                            case false:
                                beginDispatching((Message) args.get("message"), (Handler) args.get("target"), (Runnable) args.get("callback"));
                                break;
                            case true:
                                endDispatching();
                                break;
                            case true:
                                setCurrActivity((Activity) args.get(FreezeScreenScene.ACTIVITY_PARAM));
                                break;
                            case true:
                                setFirstInputTime(args.getLong("inputTime"));
                                break;
                            default:
                                Log.d(TAG, "unexpected method which should be handled.");
                                break;
                        }
                        return true;
                    }
                } else if (method.equals("setFirstInputTime")) {
                    z = true;
                    switch (z) {
                        case false:
                            break;
                        case true:
                            break;
                        case true:
                            break;
                        case true:
                            break;
                        default:
                            break;
                    }
                    return true;
                }
            } else if (method.equals("endDispatching")) {
                z = true;
                switch (z) {
                    case false:
                        break;
                    case true:
                        break;
                    case true:
                        break;
                    case true:
                        break;
                    default:
                        break;
                }
                return true;
            }
        } else if (method.equals("setCurrActivity")) {
            z = true;
            switch (z) {
                case false:
                    break;
                case true:
                    break;
                case true:
                    break;
                case true:
                    break;
                default:
                    break;
            }
            return true;
        }
        z = true;
        switch (z) {
            case false:
                break;
            case true:
                break;
            case true:
                break;
            case true:
                break;
            default:
                break;
        }
        return true;
    }

    private void setFirstInputTime(long inputTime) {
        if (this.mChecker.waterLine == 2 && this.mChecker.firstInputTime == 0) {
            this.mChecker.firstInputTime = inputTime;
        } else if (this.mChecker.waterLine < 1 && this.mChecker.firstInputTime != 0) {
            this.mChecker.firstInputTime = 0;
        }
        initBgWorkingPackageList();
    }

    private void setCurrActivity(Activity activity) {
        this.mChecker.mCurrActivity = new WeakReference(activity);
    }

    public void beginDispatching(Message msg, Handler target, Runnable callback) {
        this.mChecker.dispatchTime = SystemClock.uptimeMillis();
        this.mChecker.dispatching = true;
        this.mChecker.curMessage = msg;
        this.mChecker.curMessageTarget = target;
        this.mChecker.curMessageCallback = callback;
    }

    public void endDispatching() {
        long msgDelayed = this.mChecker.dispatchTime - this.mChecker.curMessage.expectedDispatchTime;
        if (msgDelayed >= MSG_DELAY_WATERMARK_DELAY) {
            addDelayedMsgList(msgDelayed);
            if (this.mChecker.waterLine != 2) {
                this.mChecker.delayStartTime = SystemClock.uptimeMillis();
                this.mChecker.waterLine = 2;
            }
        } else if (msgDelayed >= MSG_DELAY_WATERMARK_WARNING) {
            addDelayedMsgList(msgDelayed);
            if (this.mChecker.waterLine != 1) {
                this.mChecker.waterLine = 1;
            }
        } else if (this.mChecker.waterLine != 0) {
            this.mChecker.waterLine = 0;
            this.mChecker.writeIndexOfArray = 0;
            this.mChecker.outOfArrayBound = false;
        }
        this.mChecker.dispatching = false;
        this.mChecker.curMessage = null;
        this.mChecker.curMessageTarget = null;
        this.mChecker.curMessageCallback = null;
    }

    private void addDelayedMsgList(long msgDelayed) {
        if (Log.HWINFO) {
            long msgProcessed = SystemClock.uptimeMillis() - this.mChecker.dispatchTime;
            this.mChecker.addToDelayedMsgList(dumpMessage(this.mChecker.curMessage, this.mChecker.curMessageTarget, this.mChecker.curMessageCallback), msgDelayed, msgProcessed);
        }
    }

    private static String dumpMessage(Message message, Handler target, Runnable callback) {
        if (message == null) {
            return "message is null";
        }
        StringBuilder b = new StringBuilder();
        b.append("{");
        if (target != null) {
            if (callback != null) {
                try {
                    String callbackClass = callback.getClass().getName();
                    b.append(" callback=");
                    b.append(callbackClass);
                    if (MAIN_THREAD_MESSAGE_CALLBACK.equals(callbackClass)) {
                        b.append(callback.toString());
                    }
                } catch (Exception e) {
                    Log.i(TAG, "Could not get Class Name", e);
                }
            } else {
                b.append(" what=");
                b.append(message.what);
            }
            if (message.arg1 != 0) {
                b.append(" arg1=");
                b.append(message.arg1);
            }
            if (message.arg2 != 0) {
                b.append(" arg2=");
                b.append(message.arg2);
            }
            try {
                if (message.obj != null) {
                    b.append(" obj=");
                    b.append(message.obj);
                }
            } catch (Exception e2) {
                Log.i(TAG, "Could not get Obj", e2);
            }
            b.append(" target=");
            b.append(target.getClass().getName());
        } else {
            b.append(" barrier=");
            b.append(message.arg1);
        }
        b.append(" }");
        return b.toString();
    }

    private void initBgWorkingPackageList() {
        if (this.mPackageName == null && this.mEnableSystemUIChecking) {
            if (this.mChecker != null) {
                this.mPackageName = this.mChecker.getPackageName();
            }
            if ("com.android.systemui".equals(this.mPackageName) && mSingleton != null) {
                this.mIsSystemUi = true;
                mSingleton.start(null);
            }
        }
    }
}
