# AniLinkService - 基于弹弹play开放平台搭建的番剧识别与弹幕播放器
![AniLinkService](https://socialify.git.ci/eventhorizonsky/AniLinkService/image?description=1&font=Bitter&issues=1&language=1&logo=&name=1&owner=1&pulls=1&stargazers=1&theme=Auto)

## 项目介绍

本项目基于弹弹play开放平台搭建，是一个类似Emby的本地动漫媒体服务器，主要功能包括：
- 扫描本地动漫视频文件
- 自动匹配弹幕和元数据
- 提供Web界面播放和管理
- 支持多端观看

## 技术架构

### 后端服务
- 核心框架：Spring Boot 3.x
- API文档：SpringDoc OpenAPI 3.0
- 视频处理：FFmpeg (待集成)
- 元数据刮削：弹弹play开放API + 自定义解析器
- 数据库：H2 (开发环境) / PostgreSQL (生产环境)

### 前端界面
- 核心框架：Vue 3 + Vite
- 播放器：Artplayer
- UI组件库： (待集成)
- 状态管理： (待集成)

### 部署方案
- 容器化：Docker (已支持)
- 持续集成：GitHub Actions

## 核心功能

### 已实现
- [x] 项目搭建

### 待实现
- [ ] 视频文件扫描与索引
- [ ] 弹幕匹配与同步播放
- [ ] 元数据刮削与管理
- [ ] 用户认证与权限系统
- [ ] 多端播放适配

## 愿景目标

1. **智能化识别**
   - 动漫文件识别，元数据匹配
   - 支持文件归档、硬链接等

2. **在线播放**
   - 弹幕缓存同步
   - 支持加载多种字幕

3. **多端适配**
   - 完善Web端播放体验
   - 未来扩展移动端支持


## 开发指南

### 环境要求
- JDK 17+
- Node.js 18+
- Maven 3.8+

### 快速开始
```bash
# 后端启动
#通过IDE直接运行 AniLinkServiceApplication.java

# 前端启动
pnpm install
pnpm run dev
```