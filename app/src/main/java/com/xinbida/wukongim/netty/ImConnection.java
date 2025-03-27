package com.xinbida.wukongim.netty;

import com.chat.base.utils.WKLogUtils;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.db.MsgDbManager;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.interfaces.IReceivedMsgListener;
import com.xinbida.wukongim.message.MessageHandler;
import com.xinbida.wukongim.message.WKProto;
import com.xinbida.wukongim.message.type.WKMsgType;
import com.xinbida.wukongim.protocol.WKBaseMsg;
import com.xinbida.wukongim.protocol.WKConnectAckMsg;
import com.xinbida.wukongim.protocol.WKDisconnectMsg;
import com.xinbida.wukongim.protocol.WKPongMsg;
import com.xinbida.wukongim.protocol.WKSendAckMsg;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;

public class ImConnection extends SimpleChannelInboundHandler<WKBaseMsg> {

    private final String host;
    private final int port;
    private final int connectionTimeout;
    private final ImClient imClient;

    private final NioEventLoopGroup workerGroup;
    private final ConnectionListener connectionListener;
    private final IReceivedMsgListener mIReceivedMsgListener;

    private final Channel lowLevelConnection;

    public ImConnection(String host, int port, int connectionTimeout,
                        ImClient imClient, NioEventLoopGroup workerGroup,
                        ConnectionListener connectionListener, IReceivedMsgListener iReceivedMsgListener) {
        this.host = host;
        this.port = port;
        this.connectionTimeout = connectionTimeout;
        this.imClient = imClient;
        this.workerGroup = workerGroup;
        this.connectionListener = connectionListener;
        this.mIReceivedMsgListener = iReceivedMsgListener;
        this.lowLevelConnection = connect();
    }


    private Channel connect() {
        Bootstrap bootstrap = new Bootstrap().group(workerGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectionTimeout)
                .handler(new ChannelInitializer<SocketChannel>() {

                    @Override
                    public void initChannel(SocketChannel ch) {
                        IdleStateHandler idleStateHandler = new IdleStateHandler(
                                30 + 12, 0, 0
                        );
                        ch.pipeline().addLast(
                                idleStateHandler, // 超时控制
                                new CodeC(),// 编解码
                                ImConnection.this // 业务
                        );

                        ch.closeFuture().addListener((ChannelFutureListener) future -> {
                                    WKLogUtils.i("server connection closed");
                                    invokeOnDisconnected();
                                }
                        );
                    }
                });
        WKLogUtils.i("begin to connect to " + host + ":" + port);
        ChannelFuture channelFuture = bootstrap.connect(host, port);
        channelFuture.addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                WKLogUtils.i("connect to " + host + ":" + port + " failed " + future.cause());
                invokeOnDisconnected();
                return;
            }
            WKLogUtils.i("connect to " + host + ":" + port + " success");
            invokeOnConnected();
        });
        return channelFuture.channel();

    }


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WKBaseMsg g_msg) throws Exception {
        if (g_msg.packetType == WKMsgType.CONNACK) {
            WKConnectAckMsg loginStatusMsg = (WKConnectAckMsg) g_msg;
            mIReceivedMsgListener.loginStatusMsg(loginStatusMsg.reasonCode);
        } else if (g_msg.packetType == WKMsgType.SENDACK) {
            //发送ack
            WKSendAckMsg sendAckMsg = (WKSendAckMsg) g_msg;
            WKMsg wkMsg = null;
            if (!g_msg.no_persist) {
                wkMsg = MsgDbManager.getInstance().updateMsgSendStatus(sendAckMsg.clientSeq, sendAckMsg.messageSeq, sendAckMsg.messageID, sendAckMsg.reasonCode);
            }
            if (wkMsg == null) {
                wkMsg = new WKMsg();
                wkMsg.clientSeq = sendAckMsg.clientSeq;
                wkMsg.messageID = sendAckMsg.messageID;
                wkMsg.status = sendAckMsg.reasonCode;
                wkMsg.messageSeq = (int) sendAckMsg.messageSeq;
            }
            WKIM.getInstance().getMsgManager().setSendMsgAck(wkMsg);

            mIReceivedMsgListener
                    .sendAckMsg(sendAckMsg);
        } else if (g_msg.packetType == WKMsgType.RECEIVED) {
            //收到消息
            WKMsg message = WKProto.getInstance().baseMsg2WKMsg(g_msg);
//            message.header.noPersist = no_persist == 1;
//            message.header.redDot = red_dot == 1;
//            message.header.syncOnce = sync_once == 1;
            MessageHandler.getInstance().handleReceiveMsg(message);
            // mIReceivedMsgListener.receiveMsg(message);
        } else if (g_msg.packetType == WKMsgType.DISCONNECT) {
            //被踢消息
            WKDisconnectMsg disconnectMsg = (WKDisconnectMsg) g_msg;
            mIReceivedMsgListener.kickMsg(disconnectMsg);
        } else if (g_msg.packetType == WKMsgType.PONG) {
            mIReceivedMsgListener.pongMsg((WKPongMsg) g_msg);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            WKLogUtils.i("channel idle, close to restart");
            ctx.close();
            return;
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        WKLogUtils.e("CLIENT", "exception caught: ", cause);
        ctx.close();
    }

    private boolean connectedInvoked = false;

    private void invokeOnConnected() {
        if (connectedInvoked) {
            return;
        }
        imClient.doOnMainThead(() -> {
            connectedInvoked = true;
            connectionListener.onConnected(ImConnection.this);
        });
    }

    private boolean disconnectInvoked = false;

    private void invokeOnDisconnected() {
        if (disconnectInvoked) {
            return;
        }
        imClient.doOnMainThead(() -> {
            disconnectInvoked = true;
            connectionListener.onDisconnected(ImConnection.this);
        });
    }


    public boolean isActive() {
        return lowLevelConnection.isActive();
    }

    public void close() {
        lowLevelConnection.close();
    }

    void writeToServer(WKBaseMsg wkBaseMsg) {
        if (lowLevelConnection.isActive()) {
            lowLevelConnection.writeAndFlush(wkBaseMsg);
        }
    }

    public interface ConnectionListener {
        void onConnected(ImConnection majoraConnection);

        void onDisconnected(ImConnection majoraConnection);
    }

}
