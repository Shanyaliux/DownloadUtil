# DownloadUtil

## 使用方法

- 根目录的 `build.gradle` 添加 `maven { url 'https://jitpack.io' }`  ， 位置如下

```
allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
```

- 添加依赖
  
```
implementation 'com.github.Shanyaliux:DownloadUtil:1.0.0'
```

- 调用方法

```java
/**
* 下载工具的创建
*@param Url 网络资源路径
*@param Path 本地存储路径
*@param ThreadNum 下载线程数量 （若服务器请求数据没有Content-Type，则默认单线程显示，且无法监听下载进度）
*@param new DownloadUtil.OnDownloadListener() 下载状态监听接口
*/
DownloadUtil downloadUtil = DownloadUtil.getInstance(Url, Path, ThreadNum, new DownloadUtil.OnDownloadListener() {
                @Override
                public void onDownloadSuccess() {
                    //下载成功
                }

                @Override
                public void onDownloadProgress(int progress) {
                    //下载进度（若服务器请求数据没有Content-Type，其无效）
                }

                @Override
                public void onDownloadFailed(Exception e) {
                    //下载失败
                }
            });


/**
* 下载开始函数 (目前建议单独放一个线程执行)
*/

new Thread(() -> {
    downloadUtil.download();
}).start();

```

博客链接：[https://www.shanya.world/archives/6f11b78c.html](https://www.shanya.world/archives/6f11b78c.html)
