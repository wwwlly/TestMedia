# TestMedia

这是一个独立的最小 Android 测试应用。当前包含用于对比 `DialogFragment` 三种 Window 动画配置的测试页面：

- 自定义 `windowAnimationStyle`：透明度与缩放动画，200 ms。
- 系统默认 Dialog Window 动画。
- 独立 animation style 中将进入、退出动画都设为 `@null`。

三个入口共用同一个 `TestDialogFragment` 和 `dialog_test.xml`，只通过参数切换 Dialog 主题中的 Window 动画配置。

## 构建

使用 JDK 17：

```shell
./gradlew assembleDebug
```

Windows：

```powershell
.\gradlew.bat assembleDebug
```

## 核对 Window 动画

Dialog 的 `onStart()` 会以 tag `DialogWindowAnimation` 输出当前模式及 `window.attributes.windowAnimations`：

```shell
adb logcat -s DialogWindowAnimation:I
```

也可以在 Dialog 显示时执行：

```shell
adb shell dumpsys window windows
```

## 歌曲列表与播放详情

首页的媒体入口进入 `MediaActivity`，歌曲使用 RecyclerView 和 ListAdapter 展示。
点击歌曲进入 `PlaybackActivity` 并播放选中歌曲；当前歌曲行展示播放状态。
点击播放中或暂停中的当前歌曲只打开详情，保留进度与状态；点击其他歌曲、已停止或失败的歌曲会重新发起播放。
详情页展示封面、歌名、歌手、专辑、播放状态和进度，支持上下曲、播放/暂停、停止与拖动进度。
通知栏点击后进入当前播放详情，不会重新播放歌曲。退出页面仅释放媒体会话连接，不停止后台播放。
两页通过 MediaSessionConnection 连接 MusicBrowserService，不依赖具体播放器或媒体数据源。

界面验证：列表选歌、详情上下曲/暂停恢复/停止/seek、返回列表后重新打开详情，以及从通知栏恢复详情。
