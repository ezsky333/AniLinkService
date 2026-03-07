# AniLinkService

基于弹弹play开放平台的本地动漫媒体服务，提供媒体库扫描、番剧匹配、弹幕播放与后台管理。

![AniLinkService](https://socialify.git.ci/eventhorizonsky/AniLinkService/image?description=1&font=Bitter&issues=1&language=1&logo=&name=1&owner=1&pulls=1&stargazers=1&theme=Auto)

## 当前已实现功能

以下为当前代码中已经落地的能力：

- 安装向导
   - 首次安装流程（系统信息检查、站点配置、管理员账号、媒体库配置）
   - 安装完成后自动进入正常站点路由
- 认证与权限
   - 用户登录与获取当前用户信息
   - 基于 Sa-Token 的鉴权
   - 管理接口使用 `super-admin` 角色保护
- 媒体库管理
   - 添加/删除媒体库
   - 手动扫描单个或全部媒体库
   - 服务端目录浏览（用于选择媒体库路径）
   - 应用启动后自动执行一次全库扫描
- 媒体文件索引与元数据
   - 扫描常见视频文件（`mp4/mkv/avi/mov`）
   - 目录监听文件变更（新增、修改、删除）
   - 通过 `ffprobe` 提取技术元数据（分辨率、帧率、编码、HDR、时长等）
   - 计算文件 Hash
   - 异步元数据队列与队列状态查询
- 弹弹play匹配与本地化缓存
   - 按文件信息调用弹弹匹配接口，自动回填 `animeId/episodeId` 等字段
   - 拉取番剧详情并入库
   - 下载封面到本地目录并通过静态路径提供访问
- 动漫库与搜索
   - 动漫列表分页查询
   - 关键词搜索
   - 动漫详情与本地剧集列表
   - 新番时间表（按周展示）
- 播放与弹幕
   - 视频流接口支持 HTTP Range（可拖拽进度播放）
   - 前端 Artplayer 播放
   - 弹幕代理接口 `/api/v2/comment/{episodeId}`
   - 弹幕 30 分钟数据库缓存，支持 `withRelated=true`
   - 弹弹弹幕格式转换并接入 Artplayer 弹幕插件
- 管理后台
   - 系统信息查看
   - 站点配置与 Dandan App 配置
   - 媒体库管理
   - 视频文件管理（查看详情、编辑番剧信息、删除、批量重新获取元数据）
   - 动漫库管理（列表、搜索、查看剧集）

## 技术栈

- 后端：Spring Boot 3.4.x, Spring Data JPA, Liquibase, Sa-Token, SpringDoc OpenAPI
- 数据库：H2（默认）/ PostgreSQL
- 前端：Vue 3, Vite, Vuetify, Vue Router, Axios
- 播放器：Artplayer + artplayer-plugin-danmuku
- 媒体分析：FFprobe（容器镜像内已安装 FFmpeg 工具集）

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
   -e DANDAN_IMAGE_DIR=/images/dandan \
   -v ./config:/app/config \
   -v ./dandan-images:/images/dandan \
   anilink-service
```

`DANDAN_IMAGE_DIR` 用于显式指定 `/images/dandan/**` 的本地封面存储目录。

## 主要接口示例

- 安装相关
   - `GET /api/init/system-info`
   - `POST /api/init/site-config`
   - `POST /api/init/media-library`
- 认证
   - `POST /api/auth/login`
   - `POST /api/auth/currentUser`
- 动漫与弹幕
   - `GET /api/animes`
   - `GET /api/animes/{animeId}/raw-json`
   - `GET /api/animes/{animeId}/episodes`
   - `GET /api/animes/shin/raw-json`
   - `GET /api/v2/comment/{episodeId}?withRelated=true`
- 播放
   - `GET /api/media-files/stream/{id}`

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

## 当前待完善

- 用户中心页面功能仍较基础（当前为占位内容）
- 自动化测试覆盖率有待补充
- Docker Compose/一体化部署示例可继续完善