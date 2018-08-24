package top.potens.jnet.bootstrap;


import io.netty.channel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.potens.jnet.bean.RPCHeader;
import top.potens.jnet.event.EventSource;
import top.potens.jnet.handler.*;
import top.potens.jnet.helper.SendPoolHelper;
import top.potens.jnet.listener.FileCallback;
import top.potens.jnet.listener.RPCCallback;
import top.potens.jnet.protocol.HBinaryProtocol;
import top.potens.jnet.protocol.HBinaryProtocolDecoder;
import top.potens.jnet.protocol.HBinaryProtocolEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.timeout.IdleStateHandler;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.EventListener;
import java.util.concurrent.TimeUnit;

/**
 * Created by wenshao on 2018/5/6.
 * 主通信进程的client
 */
public class BossClient {
    private static final Logger logger = LoggerFactory.getLogger(BossClient.class);

    private int port;
    private String host;
    private FileHandler fileHandler;
    private FileCallback fileReceiveCallback;
    private RPCHandler mRPCHandler;
    private EventHandler mEventHandler;
    private EventSource eventSource;
    private NioEventLoopGroup workerGroup;
    private String fileUpSaveDir;
    private SendPoolHelper sendPoolHelper;
    private boolean isActivityAll;

    public BossClient() {
        initDefault();
        sendPoolHelper = new SendPoolHelper();
        isActivityAll = false;
        this.eventSource = new EventSource();
    }

    private void initDefault() {
        this.fileUpSaveDir = "/d/tmp";
    }

    public String getFileUpSaveDir() {
        return fileUpSaveDir;
    }
    // Fluent风格api=====================================

    /**
     * 设置server的地址
     *
     * @param host ip
     * @param port 端口
     * @return this
     */
    public BossClient connect(String host, int port) {
        this.host = host;
        this.port = port;
        return this;
    }

    /**
     * 设置接受文件回调
     *
     * @param fileCallback 回调
     * @return this
     */
    public BossClient receiveFile(FileCallback fileCallback) {
        this.fileReceiveCallback = fileCallback;
        return this;
    }

    /**
     * 添加server event
     *
     * @param eventListener listener
     * @return this
     */
    public BossClient addServerEventListener(EventListener eventListener) {
        this.eventSource.addListener(eventListener);
        return this;
    }

    /**
     * 设置文件上传保存路径
     *
     * @param dir 目录
     * @return this
     */
    public BossClient fileUpSaveDir(String dir) {
        this.fileUpSaveDir = dir;
        return this;
    }

    // 连接回调
    private interface ConnectionCallback {
        public void success();
    }
    // ===========


    /**
     * 发送本地的文件
     *
     * @param file         发送的文件对象
     * @param receive      接收人id
     * @param receiveId    接收人类型id
     * @param fileCallback 回调
     * @throws FileNotFoundException 文件不存在
     */
    public void sendFile(File file, byte receive, String receiveId, FileCallback fileCallback) throws FileNotFoundException {
        if (isActivityAll) {
            fileHandler.sendFile(file, receive, receiveId, fileCallback);
        } else {
            sendPoolHelper.addFile(file, receive, receiveId, fileCallback);
        }
    }

    /**
     * 发送rpc请求
     *
     * @param rpcHeader   请求头
     * @param rpcCallback 响应回调
     */
    public void sendRPC(RPCHeader rpcHeader, RPCCallback rpcCallback) {
        if (isActivityAll) {
            mRPCHandler.sendRPC(rpcHeader, rpcCallback);
        } else {
            sendPoolHelper.addRPC(rpcHeader, rpcCallback);
        }
    }

    public ChannelFuture start() {
        workerGroup = new NioEventLoopGroup();
        Bootstrap b = new Bootstrap();
        b.group(workerGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000)
                .handler(new ChannelInitializer<SocketChannel>() {
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        fileHandler = new FileHandler(fileReceiveCallback);
                        mRPCHandler = new RPCHandler();
                        mEventHandler = new EventHandler(eventSource);
                        pipeline.addLast("ping", new IdleStateHandler(0, 4, 0, TimeUnit.SECONDS));
                        pipeline.addLast("unpacking", new LengthFieldBasedFrameDecoder(HBinaryProtocol.MAX_LENGTH, 0, 4, 0, 4));
                        pipeline.addLast("decoder", new HBinaryProtocolDecoder());
                        pipeline.addLast("encoder", new HBinaryProtocolEncoder());
                        pipeline.addLast("heart", new HeartBeatClientHandler());
                        pipeline.addLast("file", fileHandler);
                        pipeline.addLast("rpc", mRPCHandler);
                        pipeline.addLast("event", mEventHandler);
                        pipeline.addLast("business", new BossClientHandler());
                        pipeline.addLast("last", new SimpleChannelInboundHandler<HBinaryProtocol>(){
                            @Override
                            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                // 连接完成
                                isActivityAll = true;
                                sendPoolHelper.execFile(fileHandler);
                                sendPoolHelper.execRPC(mRPCHandler);
                            }
                            @Override
                            protected void channelRead0(ChannelHandlerContext channelHandlerContext, HBinaryProtocol protocol) throws Exception {

                            }
                        });
                    }
                });

        ChannelFuture connect = b.connect(this.host, this.port);
        connect.channel().closeFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture channelFuture) throws Exception {
                // 连接断开
                isActivityAll = false;
                logger.debug("operationComplete");


            }
        });
        return connect;
    }

    // 释放资源
    public void release() {
        if (workerGroup != null) workerGroup.shutdownGracefully();
    }
}
