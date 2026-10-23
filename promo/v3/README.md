# MV-03 · 10秒动漫女生版

按本轮要求制作：总长10秒、竖屏1080p、同一动漫女生完整使用过程、中文女生合成配音和同步字幕，结尾“关注加群，免费领取”。用户两张截图加入开场人物手里的手机，不以纯应用展示作为主画面。

- 视频：`output/phone-focus-anime-10s.mp4`
- 封面：`output/cover.jpg`
- 女声：`output/female-voice-raw.mp3`；完整混音：`output/soundtrack.wav`
- 字幕：`output/captions.srt`，画面内已烧录，不依赖外挂。
- 分镜：`storyboard.json`；校验和编码后抽帧：`qa/`。

画面过程：0—2.6秒设置时段；2.6—4.3秒点击手机并显示提供的禁用页截图；4.3—6.3秒放下手机；6.3—10秒回到书本写题并出现领取口号。采用同一个22岁成年动漫女生，发型、绿色发夹、衣服和书房保持一致。插画关键帧加局部手/笔变形、运镜、点击光圈形成动漫分镜短片，不是真人或手机的连续实拍。

配音稿：

> 考研戒手机，学习期间戒手机。帮孩子少玩手机，家长和备考党必备小软件。关注加群，免费领取！

女声为通用合成声音`zh-CN-XiaoxiaoNeural`，不是对某个真人声音的克隆。通过[edge-tts项目](https://github.com/rany2/edge-tts)生成，原声约8.52秒、延后0.18秒进入10秒时间线，完整保留结尾；低音量配乐取自本项目原创MV-02配乐。在线语音只提交上面的宣传文字，没有上传用户截图。

人物画使用内置`image_gen.imagegen`生成；最终完整提示词见[image-prompt.txt](image-prompt.txt)。首次高分辨率生成遇网络错误，内置工具重试为1024×1536四格图后成功，未使用外部图像API备用通道。保存的原图为`assets/anime-storyboard.png`，两张用户截图为`assets/user-focus-screen.jpg`及`assets/user-schedule-screen.jpg`；透视布局和手指遮挡配置见`assets/layout.json`。不改写截图内的“未启用”等真实状态文字。

画面标注“AI动漫演绎”和截图合成展示，片尾保留手机授权与撤权说明。素材不构成所有型号或戒手机效果保证；不改变APK或手机权限。用户未提供群号、二维码，因此只显示指定口号，不编造群信息，也不执行关注、加群或发布。

重制剪辑（生成插画和原始配音保留后）：

```powershell
py -3.11 promo/v3/mix_audio.py
py -3.11 promo/v3/render_mv.py --previews-only
py -3.11 promo/v3/render_mv.py
py -3.11 promo/v3/verify_mv.py
```

配音生成脚本为`make_voice.py`，依赖安装在项目`.local/media-python`中的edge-tts；网络合成时沿用系统代理。渲染依赖Pillow、NumPy、FFmpeg/FFprobe。源码、人物提示词、原始资产及输出哈希记录在`qa/verification.json`，不覆盖MV-01/02。
