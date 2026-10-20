# 媒体数据源

替换数据源：实现 `MediaDataSource`，在 `MediaDataSourceFactory.create(context)` 返回新实现。
替换播放引擎：实现 `AudioPlayer`，修改 `AudioPlayerFactory`。两处独立配置。
SDK 未指定，目前只提供本地 `MusicCatalog`，未引入任何 SDK 依赖。

服务持有数据源并负责释放。页面只从 MediaBrowser 取得条目，不直接访问本地数据。
数据源负责将 SDK 模型转换成 `Song`，系统协议转换由 `MediaCatalogMapper` 负责。

- `loadSongs`：异步返回有序列表；ID 必须非空且唯一。空列表表示没有歌曲，失败通过 `Result.failure` 返回。
- `resolveSource`：根据 ID 在播放前取得 `AudioSource`。临时 URL、鉴权请求头应在这里刷新，不存入 `Song`。
- 请求方法和回调均在主线程执行，SDK 网络/磁盘操作不得阻塞主线程。允许同步完成；每次请求只回调一次。
- 返回 `MediaRequest`，其 cancel 应注销对应 SDK 回调或取消任务。即使 SDK 无法中断，也应屏蔽过期结果。
- `release` 取消全部请求并释放实例拥有的监听器；不要误关闭其他模块共用的全局 SDK。

服务支持异步浏览（detach/sendResult）、列表加载失败后重试，以及换歌/暂停/停止/销毁时取消播放源请求。
额外用请求代次过滤迟到结果，避免上一首歌异步返回后覆盖新选择。
列表首次加载后保存在服务内，直到服务销毁。当前接口不包含分页、搜索或 SDK 主动推送目录更新。

SDK 适配时可按以下逻辑实现：

```text
loadSongs(callback)
  SDK 查询列表 → 切回主线程 → SDK 模型映射为 List<Song> → callback(Result.success(...))
  SDK 失败 → 切回主线程 → callback(Result.failure(...))
  返回 MediaRequest，用来取消查询/注销回调

resolveSource(mediaId, callback)
  SDK 按稳定 ID 查询播放地址 → 切回主线程
  callback(Result.success(AudioSource.Uri(url, headers)))
  返回该次请求的 MediaRequest
```

如果 SDK 只有“通过歌曲 ID 在其内部播放器播放”的接口，没有可用 URI，则需扩展双方约定的 AudioSource 类型并提供对应播放器适配器。
数据源可独立替换，但播放引擎必须支持它返回的媒体源类型。不要用虚构的 URL 代替 SDK 对象。

测试重点：异步列表加载、空列表、列表失败后重试、播放地址失败后重试、临时 URL 刷新，以及快速切歌/停止后的迟到回调。
