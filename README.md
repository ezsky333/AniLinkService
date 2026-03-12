# AniLinkService

基于弹弹play开放平台的本地动漫媒体服务，提供媒体库扫描、番剧匹配、弹幕播放与后台管理。

![AniLinkService](https://socialify.git.ci/eventhorizonsky/AniLinkService/image?description=1&font=Bitter&issues=1&language=1&logo=&name=1&owner=1&pulls=1&stargazers=1&theme=Auto)

## 当前已实现功能

以下为当前代码中已经落地的能力：

- 安装与初始化
   - 首次安装向导（系统信息检查、站点配置、管理员账号、媒体库配置）
   - 安装完成后自动切换到正常站点路由
- 认证与用户体系
   - 用户登录、当前用户信息获取
   - 注册流程（图形验证码 + 邮箱验证码）
   - 基于 Sa-Token 的鉴权与角色控制（管理接口使用 `super-admin` 保护）
- 媒体库与扫描
   - 添加/删除媒体库，手动扫描单库或全库
   - 服务端目录浏览（选择媒体库路径）
   - 应用启动自动全库扫描
   - 目录监听文件新增/修改/删除
- 媒体文件索引与元数据
   - 扫描常见视频格式（`mp4/mkv/avi/mov`）
   - 通过 `ffprobe` 提取技术信息（分辨率、帧率、编码、HDR、时长等）
   - 文件 Hash 计算
   - 元数据/匹配异步队列与进度状态查询
- 动漫匹配与动漫库
   - 按文件信息调用弹弹匹配接口，自动回填 `animeId/episodeId`
   - 支持重匹配（自动候选 + 手动搜索）
   - 拉取番剧详情入库，封面本地缓存与静态访问
   - 动漫分页、搜索、详情、剧集列表、新番时间表
- 追番、播放历史与消息通知
   - 追番增删改查（含按状态筛选）
   - 播放进度同步与播放历史管理
   - 站内消息中心（未读统计、标记已读、按类型查询）
   - 新剧集匹配成功后自动向追番用户推送更新消息
- 播放、字幕与弹幕
   - 视频流接口支持 HTTP Range（支持拖拽进度播放）
   - 前端 Artplayer 播放 + `artplayer-plugin-danmuku`
   - 弹幕代理 `/api/v2/comment/{episodeId}`，30 分钟数据库缓存，支持 `withRelated=true`
   - 本地字幕管理：列表、上传、下载、删除、时间偏移、重新扫描
- 资源搜索与下载
   - 资源搜索代理（字幕组/类型/关键词）
   - 磁链下载任务创建、取消、重试、删除、绑定状态查询
   - 下载任务 SSE 实时进度推送
   - RSS 订阅下载（增删改查、手动触发、最近抓取内容查看）
- 远程访问
   - 远程访问入口与站点级开关控制
   - 可选令牌访问模式，支持用户远程访问密钥查看与重置
- 管理后台
   - 系统信息、服务配置、用户管理
   - 队列进度、媒体库管理、视频文件管理、字幕管理
   - 资源搜索下载、RSS 订阅下载、下载任务进度与下载器配置

## 技术栈

- 后端：Spring Boot 3.4.x, Spring Data JPA, Liquibase, Sa-Token, SpringDoc OpenAPI
- 数据库：H2（默认）/ PostgreSQL
- 前端：Vue 3, Vite, Vuetify, Vue Router, Axios
- 播放器：Artplayer + artplayer-plugin-danmuku
- 媒体分析：FFprobe（容器镜像内已安装 FFmpeg 工具集）
- BT 下载：jlibtorrent（含 Windows/Linux 平台依赖）

## 目录结构

```text
api/   后端服务（Spring Boot）
ui/    前端界面（Vue 3 + Vite）
data/  默认数据目录（H2 文件库等）
```

## 本地开发

### 环境要求

- JDK 17+
- Maven 3.8+
- Node.js 18+
- pnpm
- 本地运行后端时建议安装 `ffprobe`（Docker 镜像中已内置）

### 启动后端

在项目根目录执行：

```bash
cd api
mvn spring-boot:run
```

默认使用 H2 配置（`DB_PROFILE=h2`）。

如需 PostgreSQL：

```bash
cd api
DB_PROFILE=pgsql mvn spring-boot:run
```

### 启动前端

```bash
cd ui
pnpm install
pnpm dev
```

## Docker 部署

### 构建镜像

```bash
docker build -t anilink-service .
```

### 运行示例

```bash
docker run -d \
   --name anilink \
   -p 8081:8081 \
   -e DB_PROFILE=h2 \
   -e CONFIG_DIR=/app/config \
   -e SUBTITLE_DIR=/app/subtitles \
   -v ./config:/app/config \
   -v ./subtitles:/app/subtitles \
   anilink-service
```


## 主要接口示例

- 安装相关
   - `GET /api/init/system-info`
   - `POST /api/init/site-config`
   - `POST /api/init/media-library`
- 认证
   - `POST /api/auth/login`
   - `POST /api/auth/register`
   - `POST /api/auth/send-register-email-code`
   - `POST /api/auth/currentUser`
- 资源下载
   - `GET /api/resource-search/list`
   - `POST /api/resource-search/download`
   - `GET /api/resource-search/download-tasks`
   - `GET /api/resource-search/download-tasks/stream`
   - `GET /api/resource-search/rss-subscriptions`
- 动漫与弹幕
   - `GET /api/animes`
   - `GET /api/animes/{animeId}/raw-json`
   - `GET /api/animes/{animeId}/episodes`
   - `GET /api/animes/shin/raw-json`
   - `GET /api/v2/comment/{episodeId}?withRelated=true`
- 用户功能
   - `GET /api/follows`
   - `POST /api/play-history/progress`
   - `GET /api/messages/unread-count`
- 播放
   - `GET /api/media-files/stream/{id}`
   - `GET /api/media-files/{id}/subtitles`
- 字幕管理
   - `GET /api/subtitles`
   - `POST /api/subtitles/upload`
   - `PUT /api/subtitles/{id}/offset`
   - `POST /api/subtitles/rescan/{mediaFileId}`
- 远程访问
   - `GET /api/remote-access/credential`
   - `POST /api/remote-access/credential/regenerate`

## API 文档

启动后访问：

- `http://localhost:8081/swagger-ui/index.html`

## 鸣谢

本项目的实现离不开以下优秀开源项目与平台支持：

- Sa-Token: https://sa-token.cc/
- FFmpeg: https://ffmpeg.org/
- dandanplay 开放平台: https://doc.dandanplay.com/open/
- Artplayer: https://artplayer.org/
- artplayer-plugin-danmuku: https://github.com/zhw2590582/ArtPlayer/tree/master/packages/artplayer-plugin-danmuku
- jlibtorrent: https://github.com/frostwire/frostwire-jlibtorrent