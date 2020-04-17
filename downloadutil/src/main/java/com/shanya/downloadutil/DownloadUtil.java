package com.shanya.downloadutil;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class DownloadUtil {
    //资源路径
    private String path;
    //文件保存位置
    private String targetFile;
    //下载线程数量（当请求来的Content-Length为null时默认单线程下载）
    private int threadNum;
    //下载线程对象
    private DownloadThread[] threads;
    //文件大小（当请求来的Content-Length为null 其没有用）
    private int fileSize;
    //每个线程已下载数据存储，用来计算进度
    private int[] lengths;
    //监听下载状态
    private OnDownloadListener onDownloadListener;

    private static final String TAG = "DownloadUtil";

    //Singleton单例化
    private static DownloadUtil downloadUtil;
    public static DownloadUtil getInstance(String path, String targetFile, int threadNum, OnDownloadListener onDownloadListener){
        if (downloadUtil == null) {
            downloadUtil = new DownloadUtil(path, targetFile, threadNum,onDownloadListener);
        }
        return downloadUtil;
    }

    //构造函数
    private DownloadUtil(final String path, final String targetFile, final int threadNum, final OnDownloadListener onDownloadListener) {
        this.path = path;
        this.targetFile = targetFile;
        this.threadNum = threadNum;
        threads = new DownloadThread[threadNum];
        lengths = new int[threadNum];
        this.onDownloadListener = onDownloadListener;
    }

    //下载
    public void download() {
        //创建OkHttpClient实例
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .build();

        okhttp3.Request request = new Request.Builder()
                .get()
                .url(path)
                .build();
        Call call = client.newCall(request);
        //异步请求
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                //下载失败回调
                onDownloadListener.onDownloadFailed(e);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                //判断连接是否成功
                if (response.code() == HttpURLConnection.HTTP_OK) {
                    Headers headers = response.headers();
                    //打印请求来的一些信息
//                    for (int i = 0; i < headers.size(); i++) {
//                        Log.d(TAG, "onResponse: ----> "  + headers.name(i) + " === " + headers.value(i));
//                    }
                    //获取文件大小
                    if (headers.get("Content-Length") != null){
                        fileSize = Integer.parseInt(headers.get("Content-Length"));
                        //根据线程数分解每个线程需下载的文件大小
                        int currentPartSize = fileSize / threadNum + 1;
                        //创建RandomAccessFile 填写“rw” 没有文件会自动创建
                        RandomAccessFile file = new RandomAccessFile(targetFile, "rw");
                        //设置本地文件大小
                        file.setLength(fileSize);
                        file.close();
                        for (int i = 0; i < threadNum; i++) {
                            //每条线程使用一个RandomAccessFile 进行下载
                            RandomAccessFile currentPart = new RandomAccessFile(targetFile, "rw");
                            //计算每条线程下载的开始位置
                            int startPos = i * currentPartSize;
                            //定位该线程的下载位置
                            currentPart.seek(startPos);
                            //创建下载线程
                            threads[i] = new DownloadThread(i,startPos, currentPartSize, currentPart);
                            //启动下载线程
                            threads[i].start();
                        }
                    }else{//读取不到文件长度则执行以下内容
                        fileSize = -1;
                        //创建本地文件
                        File outFile = new File(targetFile);
                        //判断文件是否存在 不存在则新建
                        if (!outFile.getParentFile().exists()) {
                            outFile.mkdirs();
                        }
                        if (!outFile.exists()) {
                            outFile.createNewFile();
                        }
                        //创建文件输出流
                        FileOutputStream fos = new FileOutputStream(outFile);
                        //创建输入流
                        InputStream inputStream = null;
                        //判断请求数据是否为null
                        if (response.body() != null) {
                            //获取InputStream实例
                            inputStream = response.body().byteStream();
                            byte[] buffer = new byte[1024];
                            int len;
                            //读取网络数据
                            while ((len = inputStream.read(buffer, 0, buffer.length)) != -1) {
                                fos.write(buffer, 0, len);
                            }
                            //写入文件
                            fos.flush();
                        }
                        fos.close();
                        inputStream.close();
                        //下载成功回调
                        onDownloadListener.onDownloadSuccess();

                    }

                }

            }
        });
    }

    /**
     * //获取下载百分比（若没有请求到文件大小 则不会调用该方法）
     * @return 下载进度
     */
    private double getCompleteRate(){
        int sumSize = 0;
        for (int i = 0; i < threadNum; i++) {
                sumSize += lengths[i];
        }
        return sumSize * 1.0 / fileSize;
    }

    /**
     * 下载线程
     */
    private class DownloadThread extends Thread{
        //该线程开始下载的位置
        private int startPos;
        //该线程负责的文件大小
        private int currentPartSize;
        //该线程下载使用的 RandomAccessFile
        private RandomAccessFile currentPart;
        //线程序号，计算下载进度时需要
        int num;

        //构造函数
        DownloadThread(int num,int startPos, int currentPartSize, RandomAccessFile currentPart) {
            this.num = num;
            this.startPos = startPos;
            this.currentPartSize = currentPartSize;
            this.currentPart = currentPart;
        }

        @Override
        public void run() {
            super.run();
            //OkHttpClient 实例
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .build();
            okhttp3.Request request = new Request.Builder()
                    .get()
                    .url(path)
                    .build();
            Call call = client.newCall(request);

            try {
                //同步请求
                Response execute = call.execute();
                int code = execute.code();
                //判断是否连接成功
                if (code == HttpURLConnection.HTTP_OK) {
                    InputStream inputStream = null;
                    if (execute.body() != null) {
                        inputStream = execute.body().byteStream();
                        //跳过 startPos 个字节，表明该线程只下载自己负责的那部分
                        inputStream.skip(startPos);
                        byte[] buffer = new byte[1024];
                        int len;
                        //读取网络数据，并写入本地文件中
                        while (lengths[num] < currentPartSize && (len = inputStream.read(buffer)) > 0) {
                            currentPart.write(buffer, 0, len);
                            //累计该线程下载的总大小
                            lengths[num] += len;
                            //更新进度条
                            if (getCompleteRate() >= 1.0){
                                onDownloadListener.onDownloadSuccess();
                            }
                            onDownloadListener.onDownloadProgress((int)(getCompleteRate() * 100));
                        }
                    }
                    currentPart.close();
                    inputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 下载状态回调接口
     */
    public interface OnDownloadListener{
        //下载成功
        void onDownloadSuccess();
        //更新进度
        void onDownloadProgress(int progress);
        //下载失败
        void onDownloadFailed(Exception e);
    }
}
