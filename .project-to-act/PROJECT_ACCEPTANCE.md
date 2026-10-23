# 项目验收

## 当前验收结论

SITE-01本地网页验收通过（E-20261023-GITHUB-SITE）：Edge验证320/390/768/1440宽度、图片加载、无溢出、视频播放10秒1080p、下载APK哈希与原版一致、FAQ、无控制台错误。远端源码公开、Release资产uploaded且GitHub摘要匹配、Pages built及HTTP200已核实（E-20261023-GITHUB-PUBLISHED）；线上页面/样式/脚本/图片/视频/APK与本地哈希一致，一次Edge线上播放10秒1080p通过。后续复查网络超时保留记录；不改变安卓真机Gate。

MV-03媒体交付通过（E-20261022-PROMO-MV-03）：10秒1080p/30fps/300帧，同一动漫角色四动作、用户两截图透视合成、完整中文合成女声/字幕/CTA齐备。完整解码、精确音视频时长、音频信号、语音生成元数据完整性及八张成片抽帧检查通过；开场/手机/领取原尺寸复核。属于动漫分镜演绎加局部动作，不是真实手机全流程录像，不推定应用真机或戒手机效果验收通过。

MV-02媒体交付通过（E-20261022-PROMO-MV-02）：36秒1080p/30fps、原创配乐与字幕/封面/分镜齐备，完整解码、音频检查、编码状态变化及12张成片抽帧检查通过；双框与片尾另看原尺寸，中文无裁切/遮挡。手机画面为功能动画，非实机；应用1.3.1真机待验收结论不变。

1.3.1双框多时段版工程交付通过（E-20261022-ALARMUI-1.3.1）：每日规则173284项与原133711项检查通过，Release、原签名升级、编译后权限及编辑器类检查通过；Lint0错误16警告。没有连接手机，矩形选框/数字滚轮的真实显示、触控、取消和旋转恢复仍待验收，不能以源码/DEX存在性代替。以下旧版本结论保留。

1.3.0每日时段版工程交付通过（E-20261022-DAILY-1.3.0）：每日计划173148项检查与原计时/息屏/恢复检查通过，Release、签名/升级一致性、编译后首页与无障碍能力审查通过；Lint0错误12警告。未连接手机，实际覆盖、电话、到点恢复、后台及重启未验收；手机授权可绕过，不能宣称绝对强制或全型号支持。旧版本结论以下保留为历史。

1.2.0工程交付通过（E-20261022-OWNER-1.2.0）。实际设备所有者配置与系统强制模式待手机验收；没有连接手机，本轮没有真实权限变更。新增能力不作为全机型支持或绝对不可绕过证明。

宣传MV-01媒体交付通过（E-20261021-PROMO-MV-01）：成片、原创音乐、封面与可复现素材齐备，不作为应用真实设备验收。

1.1.0工程交付通过，真实设备可用性待验收。当前没有已连接安卓手机或模拟器，不宣称真实息屏、授权流程或后台存活已经通过。

## 验收标准

| ID | 标准 | 状态 | 方法及证据 |
|---|---|---|---|
| A-01 | 180秒前不锁、到时调用锁屏 | 工程通过，设备待验收 | AutoScreenOffTest、ScreenSessionTest |
| A-02 | 熄屏归零、再次亮屏新计时、不重复锁屏 | 工程通过，设备待验收 | qa/timer-test-1.1.0.txt |
| A-03 | 撤权/权限竞争/系统调用异常停止，不伪装成功 | 控制分支通过，设备待验收 | 382项锁屏决策检查，假系统适配器 |
| A-04 | Release构建、Lint、签名及升级签名一致 | 工程通过 | qa/build-1.1.0.txt、qa/upgrade-signature-1.1.0.txt |
| A-05 | APK只含force-lock管理策略，无网络/内容权限 | 工程通过 | qa/apk-device-admin-1.1.0.txt、qa/apk-permissions-1.1.0.txt |
| A-06 | 授权/立即测试/后台/开机/撤权/解除/通知拒绝 | 待设备 | qa/device-checklist.md |
| A-07 | 宣传MV格式、整片解码、音轨及中文画面 | 通过 | promo/qa/verification.json、encoded-contact-sheet.jpg |
| A-08 | 设备所有者专注计时、恢复码、独立回滚 | 纯Java检查通过，Android效果待设备 | qa/OwnerFocusTest.java，3363项 |
| A-09 | 配置工具只读默认、选定设备、失败不修改 | 模拟ADB12项通过，未真实配置 | qa/owner-setup-tests-1.2.0.txt |
| A-10 | 1.2.0构建/Lint/签名/组件 | 通过，0错误11警告 | qa/verification-1.2.0.json |
| A-11 | 所有者配置、系统Lock Task、电话、重启、到期及紧急恢复 | 待设备，不得声称通过 | qa/device-owner-checklist.md |
| A-12 | 每日重复、多时段、跨午夜、并集边界及DST | 纯Java173148项通过 | qa/DailyScheduleTest.java、qa/timer-test-1.3.0.txt |
| A-13 | 1.3.0手机每日入口、原签名升级、无障碍不读内容 | 编译后APK审查通过，授权流程待设备 | qa/verify_daily_release.py、qa/verification-1.3.0.json |
| A-14 | 无电脑的手机授权、实际覆盖、电话、结束及后台恢复 | 待设备，不得声称通过 | qa/daily-schedule-device-checklist.md |
| A-15 | 分离时段空档、独立开关、旧数据兼容与持久化 | 规则通过，每日测试共173284项 | qa/DailyScheduleTest.java、qa/timer-test-1.3.1.txt |
| A-16 | 双框/数字滚轮、每段开关/编辑/删除、取消及草稿恢复 | 源码/构建通过，真机显示触控待验收 | qa/verification-1.3.1.json、qa/daily-schedule-device-checklist.md |
| A-17 | 1.3.1安装包签名、升级一致、权限及构建 | 通过，0错误16警告 | qa/verify_alarm_release.py、qa/build-1.3.1.txt |
| A-18 | MV-02格式、完整解码、配乐/字幕及成片视觉 | 通过 | promo/v2/qa/verification.json、encoded-contact-sheet.jpg、encoded-02.jpg、encoded-11.jpg |
| A-19 | MV-03精确10秒、同一动漫角色使用过程、两截图入开场、女声/字幕/CTA完整 | 媒体检查通过；非连续实机录像 | promo/v3/qa/verification.json、encoded-contact-sheet.jpg、voice-boundaries.json、mix.json |
| A-20 | SITE-01响应式、原视频播放、原APK下载与截图展示 | 本地浏览器/视觉通过，线上原文件与一次播放通过 | qa/site-verification.json、qa/site-desktop.png、qa/site-mobile.png、qa/github-publication.json |
| A-21 | GitHub上传、Release和Pages发布，排除签名私钥及本地配置 | 通过：公开main、Release uploaded、Pages built/HTTP200，远端禁传路径为空 | qa/publish-audit.json、qa/github-publication.json |

## 证据索引

- E-20261023-GITHUB-PUBLISHED：2026-10-23 16:17（Asia/Shanghai），qa/verify_github_publication.py首轮退出0；核实公开main提交da7369bd50a174f24c1c3b9351fc6e838261498c、Release v1.3.1四资产uploaded及摘要、Pages built（main /docs）和HTML HTTP200。线上样式/脚本/人物图/视频/APK逐字节SHA256与本地一致；Edge线上视频实际播放1080×1920、10秒且无JS错误，截图qa/site-live-desktop.png已视觉复核。证据qa/github-publication.json；后续复查播放和导航遇到超时，记录在同一JSON，不保证任意网络可达。部署工作流https://github.com/zouchuheiye/phone-focus/actions/runs/39923186787为success；签名/本地配置远端路径检查为空。有效范围为该提交的网站和发布资产，不证明Android真机行为。

- E-20261023-GITHUB-SITE：qa/verify_site.py退出0，本地四种宽度无溢出、图片正常、原视频播放和下载原APK哈希一致，FAQ与控制台检查通过；桌面与手机截图视觉复核通过，见qa/site-verification.json。

- E-20261022-PROMO-MV-03：2026-10-22（Asia/Shanghai），精确时间/源码/素材/提示词哈希见promo/v3/qa/verification.json。make_voice.py、mix_audio.py、render_mv.py、verify_mv.py最终退出0。H.264 yuv420p/AAC立体声，1080×1920/30fps/10秒/300帧，3043978字节，SHA256 3fd77737e2920ae29c4699a9aa18b8be818188a6d882501012a80eeb94cd9613；完整音视频解码退出0。
- MV-03通用女声zh-CN-XiaoxiaoNeural，原声8.52秒、延后0.18秒，词边界元数据覆盖全部文案，最后一词含延迟止于8.24秒，无截断；PCM混音严格480000帧/48kHz，最终音视频均10秒。编码音频峰值0.8044、RMS0.1218；未把元数据检查写成主观听音验收。
- MV-03视觉审查：最终八张成片抽帧、开场/点手机/领取原尺寸检查通过。单角色服装/场景一致，人物占主要画面，截图位于手持手机中，手指遮挡保留，字幕/CTA无裁切或遮挡；标注AI动漫与截图合成，不编造群号/二维码。用户截图仅证明可见静态页面，不补齐实际设备行为清单。
- MV-03保留失败与修复：内置imagegen首请求网络失败、重试1024×1536成功；初次9.92秒滤镜混音及补尾重试不合格，改为样本索引混音后通过；透视边缘黑点已修正并复核。日志promo/v3/qa/initial-audio-duration.json及image-generation.json。有效范围仅当前媒体与资产哈希。

- E-20261022-PROMO-MV-02：2026-10-22（Asia/Shanghai），精确时间/源码/素材哈希见promo/v2/qa/verification.json。promo/v2/render_mv.py、promo/v2/verify_mv.py退出0；ffprobe、ffmpeg完整音视频解码与抽帧退出0。1080×1920/30fps/36秒/1080帧、H.264 yuv420p/AAC立体声，2301287字节，SHA256 872d66a4ac4d5fd0175dc1dff7089729c8dcab3f7fd2e9cd8fa0034e7cf41807。
- MV-02音频峰值0.8603、RMS0.1489，每2秒片段非静音；全片无外部音乐采样或人声，原创程序配乐。检查12张实际编码抽帧及双框/片尾原尺寸图，中文/数字/边界说明可读，无裁切或重叠。中间时段先关闭后恢复，再进入上午限制/午间空档/下午限制的演示，状态叙事一致。标注动画非实机与时间加速；有效范围仅该媒体哈希，不是设备执行或全型号证明，也未声称主观听音验收。

- E-20261022-ALARMUI-1.3.1：2026-10-22（Asia/Shanghai），完整时间、源码SHA256见qa/verification-1.3.1.json。test.ps1、build.ps1、qa/verify_alarm_release.py均退出0；每日规则173284项及原129966/382/3363项通过。aapt/apksigner/adb只读检查退出0。
- 1.3.1 APK60120字节，SHA256 d434bcedadd15694ade7cfac85101b349331edacbacf40c9e21be883948d0599，与1.3.0同签名。DEX含TimeRangeEditorDialog与NumberPicker；首页仍为ScheduleSetupActivity，权限和无内容读取能力保持。设备计数0，无原生截图或触控证据，签名/源码有效范围仅当前哈希。
- Lint0错误16警告：原12项加新中文界面4条SetTextI18n提示；未隐藏错误。初次编译setPadding参数缺失修正后通过，qa/build-1.3.1-initial-failure.txt保留失败。单位测试不验证原生显示、取消/旋转触控或OEM后台行为。

- E-20261022-DAILY-1.3.0：2026-10-22（Asia/Shanghai），完整时间及源码哈希见qa/verification-1.3.0.json。test.ps1退出0：原129966/382/3363项、新每日173148项；build.ps1退出0；qa/verify_daily_release.py退出0，aapt/apksigner/adb只读检查退出0。有效范围限记录的APK/源码哈希。
- 1.3.0 APK56232字节，SHA256 08c7b92321be64b1c59950f70a5b016247ded73a2c988c2d1af29474a4232232；与1.2.0证书一致。编译后启动入口ScheduleSetupActivity、管理员特性可选、无障碍受BIND_ACCESSIBILITY_SERVICE保护，canRetrieveWindowContent=false，仅TYPE_WINDOW_STATE_CHANGED，无手势能力、INTERNET或SYSTEM_ALERT_WINDOW。新首页恢复此前明确启用的三分钟后台检测；设备计数0，无实际安装/授予权限。
- 1.3.0 Lint0错误12警告：保留1.2.0的11项，加isAccessibilityTool仅API31+生效的兼容提示；未屏蔽错误。初次编译两处非公开SDK接口失败已替换为公开事件标记和权限控制器允许列表，失败记录qa/build-1.3.0-initial-failure.txt，最终构建通过。

- E-20261022-OWNER-1.2.0：2026-10-22（Asia/Shanghai）。test.ps1、qa/test-owner-setup.ps1、build.ps1、aapt及apksigner检查退出0；完整时间、源码哈希与方法在qa/verification-1.2.0.json。129966项原计时、382项息屏控制、3363项管理专注及12项模拟ADB保护检查通过。
- 1.2.0 APK为44768字节，SHA256 ab5ab5e95330beee13702b8facd329cd880f009f82f250caef4de206c3e8e7a5。签名验证且与1.1.0一致。权限新增SCHEDULE_EXACT_ALARM；管理器仍BIND_DEVICE_ADMIN保护；FocusAlarmReceiver不导出；FocusHome默认禁用，专注时临时启用；恢复组件支持Direct Boot。无网络/内容读取权限。
- Lint0错误11警告：ApplySharedPref（为持久恢复保留同步commit）、InlinedApi（API28日期时间限制常量，仅所有者模式在API28+启动）、OldTargetApi（target35/compile36）、UnusedAttribute（API33返回手势兼容）、SetTextI18n（中文单语言界面）。没有将警告写成零或屏蔽未检查错误。
- tools/manage-device-owner.ps1真实只读执行报告0台连接设备；-Apply路径仅在假ADB测试里运行。未实际安装到手机、授予所有者、清账号、刷机、重置手机或更改系统密码。无原生截图与运行证据，不把纯Java/模拟ADB视为设备验收。
- 对当前安装包与源码哈希有效，实际权限、电话、到期和重启行为须由目标手机新鲜证据补齐。

- E-20261021-PROMO-MV-01：2026-10-21（Asia/Shanghai）；promo/render_mv.py与promo/verify_mv.py退出0。1080×1920、30fps、32秒、960帧、H.264 yuv420p/AAC立体声，2622568字节，SHA256 3caa56f3d656370f839fcaecaf4be5a4296d73e83f8f3bb9535ff38095382eaa。整片解码0退出，音频peak0.862/RMS0.126，各2秒片段有声，音视频时长一致。已检查八个成片抽帧及中文/演示标签，无溢出。证据与生成源码哈希见promo/qa/verification.json。有效范围仅此素材哈希，不代表实机录像或设备功能通过。

- E-20261021-AUTOLOCK-1.1.0：2026-10-21，时间及源文件哈希见qa/verification-1.1.0.json。test.ps1、build.ps1、aapt dump、apksigner最终退出状态均为0。
- 计时129966项检查，服务实际使用的AutoScreenOff控制分支382项检查通过；锁屏适配器使用假OS，不是设备验证。
- Release签名APK为25464字节，SHA256 1f1be321025d3cb0847cd0d4c61c5df92950c06a6423679f44b537496f69a2e3。Lint为0错误、1条OldTargetApi警告（target35/compile36）。
- APK解析确认force-lock为唯一管理策略；接收器有BIND_DEVICE_ADMIN保护；仅申请通知、前台服务、特殊前台服务、开机接收，无网络或内容读取权限。与1.0.0签名一致。
- 有效期仅限记录中的源码与APK哈希。无手机型号/系统实测记录，禁止将lockNow返回或「上次息屏指令」当作物理屏幕已通过的证据。
- E-20261021-ENGINEERING（1.0.0）：历史验证已归档qa/v1.0.0，旧APK保留，不适用于1.1.0变更。

## Gate 记录

- G-09 GitHub发布与SITE-01展示：通过，E-20261023-GITHUB-PUBLISHED及E-20261023-GITHUB-SITE；公开仓库/Release/Pages和私钥排除已验证，网络复查超时留痕；不更改G-02及手机授权验收状态。

- G-08十秒动漫MV-03媒体交付：通过，E-20261022-PROMO-MV-03；不变更实际手机端到端Gate。

- G-07新版宣传MV-02媒体交付：通过，E-20261022-PROMO-MV-02；不变更应用真机Gate。

- G-06双框多时段工程交付：通过，E-20261022-ALARMUI-1.3.1；原生显示/触控和实际拦截Gate待设备，不豁免。

- G-05每日时段工程交付：通过，E-20261022-DAILY-1.3.0；每日真机拦截Gate为待设备，必须验证电话和授权撤销，不能以规则测试豁免。

- G-04设备所有者版工程交付：通过，E-20261022-OWNER-1.2.0；实际授予权限与强制模式为待设备Gate，不予豁免。

- G-03宣传媒体交付：通过，E-20261021-PROMO-MV-01。

- G-01工程交付：通过，E-20261021-AUTOLOCK-1.1.0。
- G-02真机可用：待设备，未豁免。

## 验收记录

- 2026-10-23：按本轮明确授权完成GitHub公开仓库、Release和宣传片同款展示页，线上资源摘要与一次实际播放通过；后续网络超时记录保留。原Android APK与视频不变，未上传签名私钥，不改变真机待验收结论。

- 2026-10-22：MV-03十秒动漫角色短片、女生合成配音、字幕、两张截图合成、封面/提示词/素材/源码完成，最终音视频检查和抽帧视觉通过。旧MV和APK保留，无外部发布或关注/加群动作。

- 2026-10-22：MV-02成片、新原创纯音乐、封面、SRT、分镜和渲染/校验源码完成；编码、完整解码、音频信号和成片抽帧视觉检查通过。无外部发布，旧MV和APK保留，应用实际拦截能力仍待手机验证。

- 2026-10-22：1.3.1双框数字选时、每段开关/编辑/删除、旧数据兼容和分离时段规则已实现并通过工程检查；同签名APK交付。真实界面、触控、旋转草稿恢复与手机自动执行仍未验收，清单已追加。

- 2026-10-22：1.3.0手机每日自动时段规则、构建、Lint和编译后能力/原签名升级检查通过。无设备，尚未完成实际无障碍授权、覆盖显示、紧急呼叫及后台验收；交付安装包及每日模式验收清单。

- 2026-10-22：1.2.0规则、恢复、脚本保护、构建、Lint和APK审查通过；无设备，真实授权/Lock Task/结束恢复/电话/重启/紧急解除待验收。软件交付与手机权限变更分开记录。

- 2026-10-21：宣传MV-01通过参数/完整解码/音频信号与成片抽帧检查；手机画面为代码动画，已标注非实机录屏，片尾保留锁屏授权/后台运行及解锁条件。原创配乐无人声，无外部采样。

- 2026-10-21：1.1.0计时/锁屏决策测试、构建、Lint、签名、同签名升级、安装包权限/策略审查通过，设备行为仍待验收。首次aapt绝对中文路径解析失败，改用当前目录相对APK路径后解析通过，未改变安装包。
- 2026-10-21：1.0.0提醒版工程通过，真机未验收；原始记录见qa/v1.0.0。
