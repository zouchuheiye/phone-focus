# 「去做正事」宣传MV

最新短版：[MV-03 · 10秒动漫女生版](v3/README.md)，同一动漫角色设置时段、点击手机、放下后学习；加入本轮两张用户截图、中文女生合成配音、字幕与“关注加群，免费领取”，成片位于`v3/output`。人物由内置imagegen生成，完整提示词和素材保存在v3目录。

新版1.3.1的宣传片见 [MV-02 · 给正事留一段时间](v2/README.md)，36秒竖屏，展示双框选时、多段独立开关、空档可用与每日重复；文件位于`v2/output`。以下保留MV-01原版说明。

32秒竖屏产品短片，适用于手机观看：1080×1920 / 30fps / H.264 MP4 / AAC立体声。

- 成片：`output/phone-focus-promo-vertical-1080p.mp4`
- 封面：`output/cover.jpg`
- 原创纯音乐与音效：`output/original-score.wav`（120 BPM，无人声）
- 分镜：`storyboard.json`；后期修改字幕可参考 `output/captions.srt`
- 成片抽帧、音视频参数和校验：`qa/`

以「只看一眼 → 再刷三分钟 → 自动息屏 → 完成眼前这一页」为画面主线，延续应用的绿色、米白与暖金色。

手机界面与桌面均为代码绘制的示意动画，片内已标注「功能动画演示 · 非实机录屏」，计时过程标注已加速。结尾说明首次需授权锁屏并允许后台/自启动。没有将软件的未验收真机行为伪装成实机录像。

素材与配乐为本次原创程序生成，无外部图像、音乐采样或人声。中文使用本机Microsoft YaHei字体渲染。视频不包含下载网址、二维码或公开发布动作。

## 重制

需要Python3.11、Pillow、NumPy、SciPy和PATH中的FFmpeg/FFprobe：

```powershell
py -3.11 promo/render_mv.py --previews-only
py -3.11 promo/render_mv.py
py -3.11 promo/verify_mv.py
```

`render_mv.py`包含构图、镜头、字号和字幕文案；`music.py`包含原创配器、旋律和16秒处的锁屏音效。重制会覆盖本宣传片输出，不修改应用APK和签名。
