package com.xinbida.wukongim.netty;

import com.chat.base.utils.WKLogUtils;
import com.xinbida.wukongim.protocol.WKBaseMsg;

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

    private final Channel lowLevelConnection;

    public ImConnection(String host, int port, int connectionTimeout,
                        ImClient imClient, NioEventLoopGroup workerGroup,
                        ConnectionListener connectionListener) {
        this.host = host;
        this.port = port;
        this.connectionTimeout = connectionTimeout;
        this.imClient = imClient;
        this.workerGroup = workerGroup;
        this.connectionListener = connectionListener;
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
        });
        return channelFuture.channel();

    }


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WKBaseMsg msg) throws Exception {

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
        majoraClient.doOnMainThead(() -> {
            connectedInvoked = true;
            connectionListener.onConnected(MajoraConnection.this);
        });
    }

    private boolean disconnectInvoked = false;

    private void invokeOnDisconnected() {
        if (disconnectInvoked) {
            return;
        }
        majoraClient.doOnMainThead(() -> {
            disconnectInvoked = true;
            connectionListener.onDisconnected(MajoraConnection.this);
        });
    }


    public boolean isActive() {
        return lowLevelConnection.isActive();
    }

    public void close() {
        lowLevelConnection.close();
    }

    void writeToMajora(IProto iProto) {
        if (lowLevelConnection.isActive()) {
            lowLevelConnection.writeAndFlush(iProto);
        }
    }

    public interface ConnectionListener {
        void onConnected(ImConnection majoraConnection);

        void onDisconnected(ImConnection majoraConnection);
    }

}
