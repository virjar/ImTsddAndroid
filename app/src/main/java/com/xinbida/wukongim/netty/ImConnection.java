package com.xinbida.wukongim.netty;

import com.xinbida.wukongim.protocol.WKBaseMsg;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class ImConnection extends SimpleChannelInboundHandler<WKBaseMsg> {


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WKBaseMsg msg) throws Exception {

    }

    public interface ConnectionListener {
        void onConnected(ImConnection majoraConnection);

        void onDisconnected(ImConnection majoraConnection);
    }

}
