package com.xinbida.wukongim.manager;

import android.os.Handler;
import android.os.Looper;

/**
 * 2020-09-21 13:48
 * 管理者
 */
public class BaseManager {

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static void runOnMainThread(ICheckThreadBack iCheckThreadBack) {
        if (iCheckThreadBack == null) {
            return;
        }
        if (!Looper.getMainLooper().isCurrentThread()) {
            mainHandler.post(iCheckThreadBack::onMainThread);
        } else iCheckThreadBack.onMainThread();
    }

    public interface ICheckThreadBack {
        void onMainThread();
    }
}
