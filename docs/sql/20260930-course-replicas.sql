-- 在内容库执行一次；不删除既有表或数据。payload 保留不可变事件快照。
ALTER TABLE mq_message ADD COLUMN payload LONGTEXT NULL COMMENT '课程发布或下架时的正式快照';

-- 学习服务访问独立目录表；搜索服务使用 Elasticsearch，不建立 SQL 搜索表。
CREATE TABLE IF NOT EXISTS learning_course (
  id BIGINT NOT NULL PRIMARY KEY,
  event_id BIGINT NOT NULL,
  status CHAR(5) NOT NULL,
  name TEXT NOT NULL,
  tags TEXT NOT NULL,
  category VARCHAR(255) NOT NULL,
  payload LONGTEXT NOT NULL,
  INDEX idx_learning_status (status)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
