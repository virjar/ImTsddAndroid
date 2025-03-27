package com.chat.uikit;

import android.annotation.SuppressLint;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.chat.base.config.WKConfig;
import com.chat.uikit.chat.manager.WKIMUtils;
import com.xinbida.wukongim.WKIM;

@SuppressLint("SpecifyJobSchedulerIdRange")
public class AliveJobService extends JobService {

    private static final String TAG = "AliveJobService";


    public static void startJob(Context context) {
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        //setPersisted 在设备重启依然执行
        // 需要增加权限 RECEIVE_BOOT_COMPLETED
        JobInfo.Builder builder = new JobInfo.Builder(8, new ComponentName(context.getPackageName(),
                AliveJobService.class.getName())).setPersisted(true);

        // 小于7.0
        // 延迟执行任务
        builder.setMinimumLatency(1000);

        jobScheduler.schedule(builder.build());
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.e(TAG, "onStartJobAlive");
        if (TextUtils.isEmpty(WKConfig.getInstance().getToken())) {
            return false;
        }
        // 如果7.0以上 轮询
        startJob(this);

        WKIM.getInstance().getConnectionManager().connection();
        WKIMUtils.getInstance().initIMListener();
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }


}
