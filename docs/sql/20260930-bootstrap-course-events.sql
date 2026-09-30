-- 副本初始化：既有发布/下架快照没有新协议事件时补一条，重复执行不新增。
-- 不用当前快照填充旧事件的 payload，旧消息无法证明当时的数据版本。
INSERT INTO mq_message (
  message_type, business_key1, business_key2, business_key3, payload,
  execute_num, state, stage_state1, stage_state2, stage_state3, stage_state4
)
SELECT 'course_publish', p.id, p.company_id, p.status,
  JSON_OBJECT(
    'id', p.id, 'companyId', p.company_id, 'companyName', p.company_name,
    'name', p.name, 'users', p.`users`, 'tags', p.tags, 'username', p.username,
    'mt', p.mt, 'st', p.st, 'mtName', p.mt_name, 'stName', p.st_name,
    'grade', p.grade, 'teachmode', p.teachmode, 'pic', p.pic,
    'description', p.description, 'market', p.market, 'teachplan', p.teachplan,
    'teachers', p.teachers, 'status', p.status, 'remark', p.remark,
    'charge', p.charge, 'price', p.price, 'originalPrice', p.original_price,
    'validDays', p.valid_days,
    'createDate', DATE_FORMAT(p.create_date, '%Y-%m-%dT%H:%i:%s'),
    'onlineDate', DATE_FORMAT(p.online_date, '%Y-%m-%dT%H:%i:%s'),
    'offlineDate', DATE_FORMAT(p.offline_date, '%Y-%m-%dT%H:%i:%s')
  ), 0, '0', '0', '0', '0', '0'
FROM course_publish p
WHERE p.status IN ('30502', '30503')
  AND NOT EXISTS (
    SELECT 1 FROM mq_message m
    WHERE m.message_type = 'course_publish'
      AND m.business_key1 = p.id AND m.payload IS NOT NULL
  );
