# 项目版本

## 当前版本

- versionName：1.4.0；versionCode：6；包名com.phonefocus.guard。
- APK：releases/phone-focus-1.4.0.apk；46224534字节；SHA256：da8882e5275365eecfd89c740521afea53121922391187cb8a8a69e1a5828491。
- 与1.3.1同签名，原时段/开关数据兼容；min26/target35/compile36，截图能力需API30（Android11）。
- 新开关默认关闭；本人确认截图用途并在系统启用无障碍服务。可能需要关闭再开启旧服务更新能力。
- 内置ML Kit text-recognition-chinese:16.0.1中文/拉丁模型，最终APK无网络权限；不保存截图/OCR原文。
- 2026-11-04工程交付通过；GitHub同步中；真机及实际OCR准确率待验收。签名材料仍在.local，不上传。

## 版本历史

- 1.4.0 / 2026-11-04：每10分钟本机截图OCR规则识别娱乐并请求锁屏，35项规则/周期/结果有效性检查与原回归通过；构建/Lint0错误20警告、同签名及最终APK截图/模型/无网络权限通过。证据qa/verification-1.4.0.json；设备计数0。

- 展示页SITE-01 / 2026-10-23：/docs静态HTML/CSS/JS，宣传片同款人物、原10秒视频、页面和APK下载；本地浏览器和视觉复核通过；GitHub Pages已built并返回HTTP200，线上文件校验及一次Edge视频播放通过。仓库zouchuheiye/phone-focus公开，Release v1.3.1含原签名APK。Android版本与原二进制哈希不变；证据qa/github-publication.json。

- 宣传素材MV-03 / 2026-10-22：10秒竖屏1080p/30fps，同一动漫女生操作手机后学习，两张用户截图入画，中文通用合成女声、字幕与领取CTA。输出promo/v3/output/phone-focus-anime-10s.mp4，3043978字节，SHA256 3fd77737e2920ae29c4699a9aa18b8be818188a6d882501012a80eeb94cd9613；证据promo/v3/qa/verification.json。独立媒体版，APK与旧MV保留不变。

- 宣传素材MV-02 / 2026-10-22：对应应用1.3.1，36秒竖屏1080p/30fps，原创120BPM纯音乐，输出promo/v2/output/phone-focus-daily-mv-1080p.mp4；2301287字节，SHA256 872d66a4ac4d5fd0175dc1dff7089729c8dcab3f7fd2e9cd8fa0034e7cf41807。独立媒体交付，APK版本/哈希不变；证据promo/v2/qa/verification.json。旧MV保留。

- 1.3.1 / 2026-10-22：横排双框、数字滚轮替换每日钟面选时；闹钟式多时段卡片，各自开关/修改/确认删除，分离空档及旧数据兼容。E-20261022-ALARMUI-1.3.1工程通过，原生显示触控/实际拦截待设备。

- 1.3.0 / 2026-10-22：每日时段成为首页，手机本人授权后自动重复，支持多段/跨午夜/重叠和时区重算；无内容读取无障碍覆盖页。E-20261022-DAILY-1.3.0工程交付通过，设备实际效果待验收，不能阻止撤权或卸载。APK56232字节，SHA256 08c7b92321be64b1c59950f70a5b016247ded73a2c988c2d1af29474a4232232。

- 1.2.0 / 2026-10-22：设备所有者定时专注、权限与配置工具、精确到期、重启恢复、紧急恢复和原策略回滚；E-20261022-OWNER-1.2.0工程交付通过，设备配置/原生行为待验收。APK44768字节，SHA256 ab5ab5e95330beee13702b8facd329cd880f009f82f250caef4de206c3e8e7a5。

- 宣传素材MV-01 / 2026-10-21：32秒竖屏1080p、30fps，原创配乐，输出promo/output/phone-focus-promo-vertical-1080p.mp4；SHA256 3caa56f3d656370f839fcaecaf4be5a4296d73e83f8f3bb9535ff38095382eaa。独立媒体版本，应用APK仍为1.1.0。

- 1.1.0 / 2026-10-21：到时自动锁屏、设备管理器授权/解除、立即息屏测试、开机/打开应用恢复、通知权限可选。工程Gate通过（E-20261021-AUTOLOCK-1.1.0），实机Gate待设备。
- 1.0.0 / 2026-10-21：提醒版本地签名APK保留，versionCode1，SHA256 f9ccb7575c311bef5c0f88a6e3669d612a9c26ba78dae3ff24a5ddb9ad052eec；工程记录见qa/v1.0.0；实机未验收。
