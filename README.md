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
