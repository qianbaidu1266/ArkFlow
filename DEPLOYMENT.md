# LangGraph4J 部署指南

## 环境要求

### 必需组件
- Java 17+
- Node.js 18+
- Maven 3.8+
- Redis 6+
- PostgreSQL 14+ (with pgvector extension)

### 可选组件
- Docker & Docker Compose
- Nginx (生产环境)

## 本地开发部署

### 1. 启动依赖服务

#### 使用 Docker (推荐)

```bash
# 启动 PostgreSQL 和 Redis
docker run -d --name langgraph4j-redis -p 6379:6379 redis:7-alpine

docker run -d --name langgraph4j-postgres \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=langgraph4j \
  -p 5432:5432 \
  ankane/pgvector:latest
```

#### 或使用 Docker Compose

```bash
docker-compose up -d postgres redis
```

### 2. 配置环境变量

```bash
# 创建 .env 文件
cat > .env << EOF
# LLM 配置
export OPENAI_API_KEY=your_openai_api_key
export LLM_MODEL=gpt-3.5-turbo

# Redis 配置
export REDIS_URI=redis://localhost:6379

# PostgreSQL 配置
export PGVECTOR_URL=jdbc:postgresql://localhost:5432/langgraph4j
export PGVECTOR_USER=postgres
export PGVECTOR_PASSWORD=postgres

# 服务端口
export SERVER_PORT=8080
EOF

# 加载环境变量
source .env
```

### 3. 构建并启动后端

```bash
cd backend

# 编译
mvn clean package -DskipTests

# 启动
java -jar target/langgraph4j-engine-1.0.0-SNAPSHOT.jar
```

后端服务将在 http://localhost:8080 启动

### 4. 构建并启动前端

```bash
cd frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

前端服务将在 http://localhost:3000 启动

### 5. 访问应用

打开浏览器访问 http://localhost:3000

## Docker 部署

### 完整部署

```bash
# 克隆项目
git clone <repository-url>
cd langgraph4j-engine

# 配置环境变量
cp .env.example .env
# 编辑 .env 文件，设置必要的配置

# 构建并启动所有服务
docker-compose up -d

# 查看日志
docker-compose logs -f

# 停止服务
docker-compose down
```

### 服务访问

- 前端: http://localhost:3000
- 后端 API: http://localhost:8080/api
- PostgreSQL: localhost:5432
- Redis: localhost:6379

## 生产环境部署

### 1. 服务器准备

```bash
# 更新系统
sudo apt update && sudo apt upgrade -y

# 安装 Docker
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER

# 安装 Docker Compose
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose
```

### 2. 配置 SSL (使用 Let's Encrypt)

```bash
# 安装 certbot
sudo apt install certbot python3-certbot-nginx -y

# 获取证书
sudo certbot --nginx -d your-domain.com
```

### 3. Nginx 配置

```nginx
# /etc/nginx/sites-available/langgraph4j
server {
    listen 80;
    server_name your-domain.com;
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    server_name your-domain.com;

    ssl_certificate /etc/letsencrypt/live/your-domain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/your-domain.com/privkey.pem;

    # 前端
    location / {
        proxy_pass http://localhost:3000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_cache_bypass $http_upgrade;
    }

    # 后端 API
    location /api {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_cache_bypass $http_upgrade;
        
        # 增加超时时间
        proxy_connect_timeout 300s;
        proxy_send_timeout 300s;
        proxy_read_timeout 300s;
    }
}
```

### 4. 启动服务

```bash
# 启用 Nginx 配置
sudo ln -s /etc/nginx/sites-available/langgraph4j /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx

# 启动应用
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

### 5. 监控和日志

```bash
# 查看应用日志
docker-compose logs -f --tail=100

# 查看资源使用
docker stats

# 重启服务
docker-compose restart
```

## Kubernetes 部署 (可选)

### 创建命名空间

```bash
kubectl create namespace langgraph4j
```

### 部署配置

```yaml
# k8s-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: langgraph4j-backend
  namespace: langgraph4j
spec:
  replicas: 2
  selector:
    matchLabels:
      app: langgraph4j-backend
  template:
    metadata:
      labels:
        app: langgraph4j-backend
    spec:
      containers:
      - name: backend
        image: langgraph4j/backend:latest
        ports:
        - containerPort: 8080
        env:
        - name: REDIS_URI
          value: "redis://redis:6379"
        - name: PGVECTOR_URL
          valueFrom:
            secretKeyRef:
              name: db-secret
              key: url
---
apiVersion: v1
kind: Service
metadata:
  name: langgraph4j-backend
  namespace: langgraph4j
spec:
  selector:
    app: langgraph4j-backend
  ports:
  - port: 8080
    targetPort: 8080
```

### 应用配置

```bash
kubectl apply -f k8s-deployment.yaml
```

## 备份和恢复

### 数据库备份

```bash
# 备份 PostgreSQL
docker exec langgraph4j-postgres pg_dump -U postgres langgraph4j > backup.sql

# 备份 Redis
docker exec langgraph4j-redis redis-cli BGSAVE
```

### 数据库恢复

```bash
# 恢复 PostgreSQL
docker exec -i langgraph4j-postgres psql -U postgres langgraph4j < backup.sql
```

## 故障排查

### 常见问题

1. **后端无法连接到数据库**
   - 检查 PostgreSQL 是否运行
   - 验证连接字符串和密码
   - 检查防火墙设置

2. **前端无法连接到后端**
   - 检查后端服务是否运行
   - 验证代理配置
   - 检查 CORS 设置

3. **LLM 调用失败**
   - 验证 API Key 是否正确
   - 检查网络连接
   - 查看后端日志

### 查看日志

```bash
# 后端日志
docker-compose logs backend

# 前端日志
docker-compose logs frontend

# 所有日志
docker-compose logs -f
```

## 性能优化

### JVM 调优

```bash
java -Xms2g -Xmx4g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -jar target/langgraph4j-engine-1.0.0-SNAPSHOT.jar
```

### 数据库优化

```sql
-- 创建索引
CREATE INDEX idx_kb_chunks_kb_id ON kb_document_chunks(kb_id);
CREATE INDEX idx_kb_chunks_document_id ON kb_document_chunks(document_id);
```

## 安全建议

1. **使用强密码**: 数据库、Redis 使用强密码
2. **启用 SSL**: 生产环境必须使用 HTTPS
3. **限制访问**: 使用防火墙限制不必要的端口访问
4. **定期更新**: 及时更新依赖和系统补丁
5. **监控日志**: 定期检查异常日志
