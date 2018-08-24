package top.potens.jnet.helper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.potens.jnet.bean.RPCHeader;
import top.potens.jnet.handler.FileHandler;
import top.potens.jnet.handler.RPCHandler;
import top.potens.jnet.listener.FileCallback;
import top.potens.jnet.listener.RPCCallback;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by wenshao on 2018/8/24.
 * 请求池类
 */
public class SendPoolHelper {
    private static final Logger logger = LoggerFactory.getLogger(SendPoolHelper.class);

    private List<FilePoolObject> filePoolObjectList = new ArrayList<>();
    private List<RPCPoolObject> rpcPoolObjectList = new ArrayList<>();


    /**
     * 添加RPC请求
     * @param rpcHeader     rpc请求头
     * @param rpcCallback   请求回调
     */
    public void addRPC(RPCHeader rpcHeader, RPCCallback rpcCallback) {
        logger.debug("add suc");
        rpcPoolObjectList.add(new RPCPoolObject(rpcHeader, rpcCallback));
    }

    /**
     *
     * @param file              File对象
     * @param receive           接受人的身份
     * @param receiveId         接受人的id
     * @param fileCallback      请求回调
     */
    public void addFile( File file, byte receive, String receiveId, FileCallback fileCallback) {
        logger.debug("add suc");
        filePoolObjectList.add(new FilePoolObject( file, receive, receiveId, fileCallback));
    }

    /**
     * 执行file请求
     * @param fileHandler   fileHandler
     */
    public void execFile(FileHandler fileHandler) {
        logger.debug("execFile start:" + filePoolObjectList.size());
        for (FilePoolObject filePoolObject : filePoolObjectList){
            try {
                fileHandler.sendFile(filePoolObject.file, filePoolObject.receive, filePoolObject.receiveId, filePoolObject.fileCallback);
            } catch (FileNotFoundException e) {
                logger.error("exec error", e);
            }
        }
    }

    /**
     * 执行rpc请求
     * @param rpcHandler    rpcHandler
     */
    public void execRPC(RPCHandler rpcHandler) {
        logger.debug("execRPC start:" + rpcPoolObjectList.size());
        for (RPCPoolObject rpcPoolObject : rpcPoolObjectList) {
            rpcHandler.sendRPC(rpcPoolObject.rpcHeader, rpcPoolObject.rpcCallback);
        }
    }


    // file请求对象
    private class FilePoolObject{
        File file;
        byte receive;
        String receiveId;
        FileCallback fileCallback;
        public FilePoolObject( File file, byte receive, String receiveId, FileCallback fileCallback) {
            this.file = file;
            this.receive = receive;
            this.receiveId = receiveId;
            this.fileCallback = fileCallback;
        }

    }
    // RPC请求对象
    private class RPCPoolObject{
        RPCHeader rpcHeader;
        RPCCallback rpcCallback;
        public RPCPoolObject(RPCHeader rpcHeader, RPCCallback rpcCallback){
            this.rpcHeader = rpcHeader;
            this.rpcCallback = rpcCallback;
        }
    }



}
