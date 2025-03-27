package com.xinbida.wukongim.message;

import android.text.TextUtils;

import com.xinbida.wukongim.WKIMApplication;
import com.xinbida.wukongim.utils.WKLoggerUtils;

import org.xsocket.connection.IConnectExceptionHandler;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.IConnectionTimeoutHandler;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.IDisconnectHandler;
import org.xsocket.connection.IIdleTimeoutHandler;
import org.xsocket.connection.INonBlockingConnection;

import java.io.IOException;
import java.nio.BufferUnderflowException;

/**
 * 2020-12-18 10:28
 * 连接客户端
 */
class ConnectionClient implements IDataHandler, IConnectHandler,
        IDisconnectHandler, IConnectExceptionHandler,
        IConnectionTimeoutHandler, IIdleTimeoutHandler {
    private final String TAG = "ConnectionClient";
    private boolean isConnectSuccess;

    interface IConnResult {
        void onResult(INonBlockingConnection iNonBlockingConnection);
    }
    IConnResult iConnResult;
    ConnectionClient(IConnResult iConnResult) {
        this.iConnResult = iConnResult;
        isConnectSuccess = false;
    }

    @Override
    public boolean onConnectException(INonBlockingConnection iNonBlockingConnection, IOException e) {
        WKLoggerUtils.getInstance().e(TAG, "connection exception");
        WKConnection.getInstance().forcedReconnection();
        close(iNonBlockingConnection);
        return true;
    }

    @Override
    public boolean onConnect(INonBlockingConnection iNonBlockingConnection) throws BufferUnderflowException {
        isConnectSuccess = true;
        iConnResult.onResult( iNonBlockingConnection);
        return false;

    }

    @Override
    public boolean onConnectionTimeout(INonBlockingConnection iNonBlockingConnection) {
        if (!isConnectSuccess) {
            WKLoggerUtils.getInstance().e(TAG, "connection timeout");
            WKConnection.getInstance().forcedReconnection();
        }
        return true;
    }

    @Override
    public boolean onData(INonBlockingConnection iNonBlockingConnection) throws BufferUnderflowException {
        Object id = iNonBlockingConnection.getAttachment();
        if (id instanceof String) {
            if (id.toString().startsWith("close")) {
                return true;
            }
            if (!TextUtils.isEmpty(WKConnection.getInstance().socketSingleID) && !WKConnection.getInstance().socketSingleID.equals(id)) {
                WKLoggerUtils.getInstance().e(TAG, "onData method The received message ID does not match the connected ID");
                try {
                    iNonBlockingConnection.close();
                    if (WKConnection.getInstance().connection != null) {
                        WKConnection.getInstance().connection.close();
                    }
                } catch (IOException e) {
                    WKLoggerUtils.getInstance().e(TAG, "onData close connection error");
                }
                if (WKIMApplication.getInstance().isCanConnect) {
                    WKConnection.getInstance().forcedReconnection();
                }
                return true;
            }
        }
        int available_len;
        int bufLen = 102400;
        try {
            available_len = iNonBlockingConnection.available();
            if (available_len == -1) {
                return true;
            }
            int readCount = available_len / bufLen;
            if (available_len % bufLen != 0) {
                readCount++;
            }

            for (int i = 0; i < readCount; i++) {
                int readLen = bufLen;
                if (i == readCount - 1) {
                    if (available_len % bufLen != 0) {
                        readLen = available_len % bufLen;
                    }
                }
                byte[] buffBytes = iNonBlockingConnection.readBytesByLength(readLen);
                if (buffBytes.length > 0) {
                    MessageHandler.getInstance().cutBytes(buffBytes,  WKConnection.getInstance());
                }
            }

        } catch (IOException e) {
            WKLoggerUtils.getInstance().e(TAG, "Handling Received Data Exception:" + e.getMessage());
        }
        return true;
    }

    @Override
    public boolean onDisconnect(INonBlockingConnection iNonBlockingConnection) {
        WKLoggerUtils.getInstance().e(TAG, "Connection disconnected");
        try {
            if (iNonBlockingConnection != null && !TextUtils.isEmpty(iNonBlockingConnection.getId()) && iNonBlockingConnection.getAttachment() != null) {
                String id = iNonBlockingConnection.getId();
                Object attachmentObject = iNonBlockingConnection.getAttachment();
                if (attachmentObject instanceof String) {
                    String att = (String) attachmentObject;
                    String attStr = "close" + id;
                    WKLoggerUtils.getInstance().e("手动关闭");
                    if (att.equals(attStr)) {
                        WKLoggerUtils.getInstance().e("手动关闭直接返回");
                        return true;
                    }
                }
            }
            if (WKIMApplication.getInstance().isCanConnect) {
                WKLoggerUtils.getInstance().e("手动关闭需要重连");
                WKConnection.getInstance().forcedReconnection();
            } else {
                WKLoggerUtils.getInstance().e(TAG, "No reconnection allowed");
            }
            close(iNonBlockingConnection);
        } catch (Exception ignored) {

        }

        return true;
    }

    @Override
    public boolean onIdleTimeout(INonBlockingConnection iNonBlockingConnection) {
        if (!isConnectSuccess) {
            WKLoggerUtils.getInstance().e(TAG, "Idle timeout");
            WKConnection.getInstance().forcedReconnection();
            close(iNonBlockingConnection);
        }
        return true;
    }

    private void close(INonBlockingConnection iNonBlockingConnection) {
        try {
            if (iNonBlockingConnection != null)
                iNonBlockingConnection.close();
        } catch (IOException e) {
            WKLoggerUtils.getInstance().e(TAG, "close connection error");
        }
    }
}