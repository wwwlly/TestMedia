# 播放器封装

业务持有 `AudioPlayer` 接口，Android 引擎通过 `AndroidAudioPlayer` 实现。
第三方 SDK 后续另建适配器，将其状态和错误映射到接口，业务不直接引用 SDK。
已接入 MusicBrowserService，未修改 PlaybackService，也未使用 Media3。

服务仅依赖 AudioPlayer；切换引擎时，在 AudioPlayerFactory.create 中返回新的适配器即可。
工厂每次创建独立实例，播放器的监听器、播放状态和释放由服务管理。第三方适配器必须遵守接口的主线程及状态约定。
MusicBrowserService 将媒体会话命令转发到接口，并同步状态、时长和进度；播放期间使用前台通知维持后台服务。
媒体数据由 MediaDataSourceFactory 创建，与播放引擎独立切换。MusicCatalog 是本地实现，通过 resolveSource 提供播放源。
目前四条演示数据均使用 assets/music/test.mp3，并非歌曲信息对应的真实音频。

```kotlin
// 主线程创建、调用、读取状态和接收回调。
val player: AudioPlayer = AudioPlayerFactory.create(context)
player.listener = object : AudioPlayer.Listener {
    override fun onStateChanged(state: PlaybackState) {
        // 更新界面或媒体会话。
    }
    override fun onError(error: PlayerError) {
        // 展示错误；重试时重新 prepare，而不是直接 play。
    }
}
player.prepare(AudioSource.Asset("music/test.mp3"), autoPlay = true)
// player.pause()
// player.play()
// player.seekTo(10_000)
// 所有者结束使用时必须释放；Activity 退到后台是否释放由业务决定。
// player.release()
```

状态约定：

- prepare：任意未释放状态 → PREPARING → READY；autoPlay 为 true 时随后进入 PLAYING。
- play：READY/PAUSED/COMPLETED → PLAYING；PREPARING 时只记录播放意图。
- pause：PLAYING → PAUSED；PREPARING 时取消自动播放。
- 播放结束：COMPLETED；再次 play 从头重播。
- stop：取消加载并释放当前引擎 → IDLE；再次播放需要 prepare。
- 加载或播放失败：ERROR，通过 onError 返回错误详情；prepare 可重试。
- release：RELEASED，重复释放无副作用。

未准备时 durationMs 为 null，positionMs 为 0。进度由调用方按需读取；seek 完成有回调。
缓冲回调不替换播放状态；媒体源切换、停止、错误或释放时，上层应清除缓冲提示。
非法调用状态、线程或参数属于编程错误，抛出异常；媒体加载/播放错误通过监听器上报。
Asset 使用 openFd，要求资源未压缩。网络 URI 需要宿主声明 INTERNET 权限。

后台服务、播放列表和通知控制由 MusicBrowserService 管理，不属于播放器适配器。
音频焦点由 MusicBrowserService 统一管理：获得焦点后播放，临时丢失时暂停，恢复后按播放意图恢复；停止、错误、完成或销毁时释放焦点。
耳机拔出处理尚未接入。会话显示 PLAYING 只能确认引擎状态，是否实际出声还需确认设备路由及听音结果。

验证时应覆盖：assets 播放、准备期间暂停/停止/换源、暂停恢复、seek、完成后重播、错误后重新准备、重复 release。
