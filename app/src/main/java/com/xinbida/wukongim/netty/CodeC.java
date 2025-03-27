package com.xinbida.wukongim.netty;

import com.xinbida.wukongim.message.WKProto;
import com.xinbida.wukongim.message.WKRead;
import com.xinbida.wukongim.message.type.WKMsgType;
import com.xinbida.wukongim.protocol.WKBaseMsg;
import com.xinbida.wukongim.protocol.WKPongMsg;
import com.xinbida.wukongim.utils.WKTypeUtils;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;

public class CodeC extends ByteToMessageCodec<WKBaseMsg> {


    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        while (true) {
            int readerIndex = in.readerIndex();
            WKBaseMsg wkBaseMsg = decodeOnce(in);
            if (wkBaseMsg == null) {
                in.readerIndex(readerIndex);
                break;
            }
            out.add(wkBaseMsg);
        }
    }

    private WKBaseMsg decodeOnce(ByteBuf in) {
        //https://githubim.com/server/advance/proto.html
        // 参考悟空Im的二进制协议
        byte fixHeader = in.readByte();
        // 高四位是包类型
        int packetType = WKTypeUtils.getInstance().getHeight4(fixHeader);
        if (packetType == WKMsgType.PONG) {
            return new WKPongMsg();
        }
        if (packetType >= 10) {
            throw new IllegalStateException("错误的包类型");
        }

        // 低四位是标志位
        boolean dup = (fixHeader & 0x08) != 0;
        boolean syncOnce = (fixHeader & 0x04) != 0;
        boolean redDot = (fixHeader & 0x02) != 0;
        boolean noPersist = (fixHeader & 0x01) != 0;

        int payloadLength = readPayloadLength(in);
        if (payloadLength < 0 || in.readableBytes() < payloadLength) {
            return null;
        }
        // todo 需要避免使用byte[]数组，这会导致gc
        byte[] bytes = new byte[payloadLength];
        in.readBytes(bytes);
        WKBaseMsg wkBaseMsg = doDecode(packetType, fixHeader, bytes);
        wkBaseMsg.packetType = (short) (packetType & 0xffff);
        wkBaseMsg.flag = fixHeader;
        wkBaseMsg.remainingLength = payloadLength;
        wkBaseMsg.sync_once = syncOnce;
        wkBaseMsg.red_dot = redDot;
        wkBaseMsg.no_persist = noPersist;
        return wkBaseMsg;
    }

    private static WKBaseMsg doDecode(int packetType, int fixHeader, byte[] bytes) {
        WKRead wkRead = new WKRead(bytes);
        if (packetType == WKMsgType.CONNACK) {
            boolean hasServerVersion = (fixHeader & 0x08) != 0;
            return WKProto.deConnectAckMsg(wkRead, hasServerVersion ? 1 : 0);
        } else if (packetType == WKMsgType.SENDACK) {
            return WKProto.deSendAckMsg(wkRead);
        } else if (packetType == WKMsgType.DISCONNECT) {
            return WKProto.deDisconnectMsg(wkRead);
        } else if (packetType == WKMsgType.RECEIVED) {
            return WKProto.deReceivedMsg(wkRead);
        } else if (packetType == WKMsgType.PONG) {
            return new WKPongMsg();
        }
        throw new IllegalStateException("Failed to parse protocol type：" + packetType);

    }

    private int readPayloadLength(ByteBuf in) {
        int multiplier = 1;
        int length = 0;
        int digit;
        for (int i = 0; i < 4; i++) {
            if (in.readableBytes() == 0) {
                //剩余长度被分包
                return -1;
            }
            digit = in.readByte(); //一个字节的有符号或者无符号，转换转换为四个字节有符号 int类型
            length += (digit & 0x7f) * multiplier;
            multiplier *= 128;
            if ((digit & 0x80) == 0) {
                return length;
            }
        }
        throw new IllegalStateException("错误的包长度结构");
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
