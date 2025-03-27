package com.xinbida.wukongim.netty;

import com.chat.base.utils.WKLogUtils;
import com.xinbida.wukongim.interfaces.IReceivedMsgListener;
import com.xinbida.wukongim.message.WKConnection;
import com.xinbida.wukongim.protocol.WKBaseMsg;

import java.util.concurrent.TimeUnit;

import io.netty.channel.EventLoop;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.DefaultThreadFactory;
import lombok.Setter;

public class ImClient implements ImConnection.ConnectionListener {

    private final String serverHost;
    private final int serverPort;
    private final IReceivedMsgListener iReceivedMsgListener;

    /**
     * 默认30s时间到服务器的连接超时
     */
    @Setter
    private int connectionTimeout = 30_000;


    private final int[] reconnectWaitSlot = new int[]{
            10, 10, 15, 15, 15, 20, 30, 30, 45
    };
    private int failedCount;

    private ImConnection currentConnection;
    /**
     * 线程组，这是重型对象，需要放到全局共享
     */
    private static final NioEventLoopGroup workerGroup = new NioEventLoopGroup(
            0, new DefaultThreadFactory("majora-client")
    );

    /**
     * 设定线程组第一个线程为主线程，主线程的作用是将一些可能存在并发竞争的操作规约在相同线程下，
     * 这样可以避免存在操作紊乱
     */
    private static final EventLoop mainLoop = workerGroup.next();

    private boolean isConnecting = false;

    public ImClient(String serverHost, int serverPort, IReceivedMsgListener iReceivedMsgListener) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.iReceivedMsgListener = iReceivedMsgListener;
    }

    public void start() {
        doOnMainThead(() -> {
            WKLogUtils.i("begin start connecting");
            if (currentConnection != null && currentConnection.isActive()) {
                WKLogUtils.i("current connection always active,skip connect");
                return;
            }
            if (isConnecting) {
                WKLogUtils.i("there is already connecting");
                return;
            }
            isConnecting = true;
            new ImConnection(serverHost, serverPort,
                    connectionTimeout, this, workerGroup, this,
                    iReceivedMsgListener
            );
        });
    }

    public void restart() {
        doOnMainThead(() -> {
            if (currentConnection == null || !currentConnection.isActive()) {
                return;
            }
            currentConnection.close();
        });
    }

    public void doOnMainThead(Runnable runnable) {
        doOnMainThead0(() -> {
            try {
                runnable.run();
            } catch (Exception e) {
                WKLogUtils.e("CLIENT", "execute main loop error", e);
            }
        });
    }

    public static void doOnMainThead0(Runnable runnable) {
        if (mainLoop.inEventLoop()) {
            runnable.run();
        } else {
            mainLoop.execute(runnable);
        }
    }

    @Override
    public void onConnected(ImConnection majoraConnection) {
        WKLogUtils.i("connection established");
        isConnecting = false;
        failedCount = 0;
        currentConnection = majoraConnection;
        WKConnection.getInstance().sendConnectMsg();
    }

    @Override
    public void onDisconnected(ImConnection majoraConnection) {
        isConnecting = false;
        int waitTime = failedCount < reconnectWaitSlot.length ? reconnectWaitSlot[failedCount] : 60;
        WKLogUtils.i("connection disconnected,will wait for " + waitTime + " seconds to reconnect");
        mainLoop.schedule(this::start, waitTime, TimeUnit.SECONDS);
    }

    public boolean isConnected() {
        return currentConnection != null && currentConnection.isActive();
    }

    public void writeWkMsg(WKBaseMsg msg) {
        currentConnection.writeToServer(msg);
    }

    public void close() {
        if(currentConnection == null){
            return;
        }
        currentConnection.close();
    }
}
