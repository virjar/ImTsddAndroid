package com.xinbida.wukongim.netty;

import com.xinbida.wukongim.message.WKProto;
import com.xinbida.wukongim.protocol.WKBaseMsg;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;

public class CodeC extends ByteToMessageCodec<WKBaseMsg> {


    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List out) throws Exception {

    }

    /**
     * 编码
     */
    @Override
    protected void encode(ChannelHandlerContext ctx, WKBaseMsg msg, ByteBuf out) throws Exception {
        // todo 不能使用byte数组，这导致内存分配在堆上，将会占用大量内存资源，后续需要优化，目前阶段仅仅完成到netty框架的切换
        byte[] bytes = WKProto.getInstance().encodeMsg(msg);
        out.writeBytes(bytes);
    }
}
