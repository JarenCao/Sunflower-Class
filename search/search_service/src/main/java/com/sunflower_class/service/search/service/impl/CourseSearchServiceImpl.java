package com.sunflower_class.service.search.service.impl;

import com.sunflower_class.base.course.CourseEvent;
import com.sunflower_class.base.model.PageResult;
import com.sunflower_class.model.dto.CourseSearchDocument;
import com.sunflower_class.service.search.service.CourseSearchService;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 通过 Spring 原生 RestClient 访问现有 Elasticsearch，无须新增另一套客户端依赖。 */
@Service
public class CourseSearchServiceImpl implements CourseSearchService {

    private RestClient client;

    @Autowired
    private JsonMapper json;

    @Value("${sunflower.elasticsearch.index}")
    private String index;

    private volatile boolean initialized;

    // 固定数量的本机锁合并热点回源，不按课程数量创建无限增长的锁对象。
    private final ReentrantLock[] cacheLocks = IntStream.range(0, 64)
        .mapToObj(i -> new ReentrantLock())
        .toArray(ReentrantLock[]::new);
    private final Semaphore cacheLoads = new Semaphore(16);

    @Autowired
    private StringRedisTemplate redis;

    /** 地址与索引名由 Nacos 提供，设置有限超时供消费重试恢复故障。 */
    @Value("${sunflower.elasticsearch.endpoint}")
    private String endpoint;

    /** 注入完成后校验索引名称，并保留原请求超时。 */
    @PostConstruct
    public void initialize() {
        if (!index.matches("[a-z0-9_][a-z0-9_-]*")) throw new IllegalArgumentException(
            "课程索引名无效"
        );
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(10));
        client = RestClient.builder().baseUrl(endpoint).requestFactory(factory).build();
    }

    /** 按事件外部版本写索引；下架保留版本墓碑，避免旧发布消息让课程重新出现。 */
    @Override
    @SuppressWarnings("unchecked")
    public void save(CourseEvent event) {
        // 写索引前后各推进代数，旧查询只能写入旧代数，不能回填成新的有效缓存。
        // 失效失败不发送成功回执，复用现有消息延迟重试；查询仍可回退ES。
        redis.opsForValue().increment(generationKey());
        ensureIndex();
        Map<String, Object> snapshot = json.readValue(event.getSnapshot(), Map.class);
        String category = Objects.toString(snapshot.get("mtName"), "");
        if (category.isBlank()) category = Objects.toString(snapshot.get("stName"), "课程");
        CourseSearchDocument document = new CourseSearchDocument(
            event.getCourseId(),
            event.getEventId(),
            event.getStatus(),
            normalize(Objects.toString(snapshot.get("name"), "")),
            normalize(Objects.toString(snapshot.get("tags"), "")),
            category.isBlank() ? "课程" : category,
            snapshot
        );
        try {
            client
                .put()
                .uri(
                    "/{index}/_doc/{id}?version={version}&version_type=external&refresh=wait_for",
                    index,
                    event.getCourseId(),
                    event.getEventId()
                )
                .contentType(MediaType.APPLICATION_JSON)
                .body(json.writeValueAsString(document))
                .retrieve()
                .toBodilessEntity();
        } catch (RestClientResponseException error) {
            // external 只接受更高版本；版本冲突说明同版本或更新版本已落库。
            if (
                error.getStatusCode().value() != 409 ||
                !error.getResponseBodyAsString().contains("version_conflict_engine_exception")
            ) throw error;
        }
        redis.opsForValue().increment(generationKey());
    }

    /** 保留服务端搜索、分页与过滤，只缓存公开结果，不缓存学习资格或密码。 */
    @Override
    @SuppressWarnings("unchecked")
    public PageResult<Map<String, Object>> list(
        long page,
        long size,
        String query,
        String category
    ) {
        String queryKey;
        try {
            queryKey = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(
                    json
                        .writeValueAsString(List.of(page, size, normalize(query), category))
                        .getBytes(StandardCharsets.UTF_8)
                )
            );
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
        return cached(
            "list:" + queryKey,
            () -> uncachedList(page, size, query, category),
            body -> {
                Map value = json.readValue(body, Map.class);
                return new PageResult<>(
                    (List<Map<String, Object>>) value.get("items"),
                    ((Number) value.get("count")).longValue(),
                    ((Number) value.get("page")).longValue(),
                    ((Number) value.get("pageSize")).longValue()
                );
            }
        );
    }

    /** 详情复用原有发布校验；404短期缓存，基础设施故障不缓存。 */
    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> get(long courseId) {
        return cached(
            "detail:" + courseId,
            () -> uncachedGet(courseId),
            body -> {
                Map value = json.readValue(body, Map.class);
                if (
                    !(value.get("id") instanceof Number id) ||
                    id.longValue() != courseId ||
                    !"30502".equals(value.get("status"))
                ) throw new IllegalArgumentException("缓存数据无效");
                return value;
            }
        );
    }

    private String generationKey() {
        return "sunflower:search:" + index + ":v1:generation";
    }

    /** 有界TTL抖动分散到期回源；命名空间及查询散列防止超长键和分页串用。 */
    private <T> T cached(String suffix, Supplier<T> loader, Function<String, T> decode) {
        ReentrantLock lock = cacheLocks[Math.floorMod(suffix.hashCode(), cacheLocks.length)];
        boolean acquired = false;
        try {
            acquired = lock.tryLock(250, TimeUnit.MILLISECONDS);
            if (!acquired) throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "查询繁忙，请稍后重试"
            );
            return loadCached(suffix, loader, decode);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "查询已中断");
        } finally {
            if (acquired) lock.unlock();
        }
    }

    private <T> T loadCached(String suffix, Supplier<T> loader, Function<String, T> decode) {
        String generation = null,
            key = null;
        try {
            redis.opsForValue().setIfAbsent(generationKey(), "0");
            generation = redis.opsForValue().get(generationKey());
            key = generationKey() + ":" + generation + ":" + suffix;
            String value = redis.opsForValue().get(key);
            if (value != null) {
                if (
                    "__NOT_FOUND__".equals(value) && suffix.startsWith("detail:")
                ) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "课程未发布或不存在");
                try {
                    return decode.apply(value);
                } catch (RuntimeException invalid) {
                    redis.delete(key);
                }
            }
        } catch (DataAccessException unavailable) {
            // Redis不可用时直接查询ES，不能返回演示数据或把故障当成空列表。
        }
        // Redis 故障时仍允许真实查询，但限制单实例并发回源，避免拖垮 ES。
        if (!cacheLoads.tryAcquire()) throw new ResponseStatusException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "查询繁忙，请稍后重试"
        );
        T value;
        try {
            value = loader.get();
        } catch (ResponseStatusException missing) {
            if (
                missing.getStatusCode().value() == 404 &&
                suffix.startsWith("detail:") &&
                key != null &&
                generation != null
            ) {
                try {
                    // 只缓存真实不存在，发布代数变化即失效；服务故障不得写成不存在。
                    if (generation.equals(redis.opsForValue().get(generationKey()))) redis
                        .opsForValue()
                        .set(
                            key,
                            "__NOT_FOUND__",
                            Duration.ofSeconds(ThreadLocalRandom.current().nextInt(3, 6))
                        );
                } catch (DataAccessException unavailable) {
                    // 保留原来的 404 结果，缓存不可用不改变业务语义。
                }
            }
            throw missing;
        } finally {
            cacheLoads.release();
        }
        if (key != null && generation != null) {
            try {
                if (generation.equals(redis.opsForValue().get(generationKey()))) redis
                    .opsForValue()
                    .set(
                        key,
                        json.writeValueAsString(value),
                        Duration.ofSeconds(ThreadLocalRandom.current().nextInt(15, 21))
                    );
            } catch (DataAccessException unavailable) {
                // 查询已成功，缓存写失败不改变业务响应。
            }
        }
        return value;
    }

    /** 搜索结果来自 ES，关键词规范化后按标题/标签包含匹配，默认页大小由接口设为十条。 */
    @SuppressWarnings("unchecked")
    private PageResult<Map<String, Object>> uncachedList(
        long page,
        long size,
        String query,
        String category
    ) {
        ensureIndex();
        long current = Math.max(1, page);
        long limit = Math.min(100, Math.max(1, size));
        if (current > 10000 / limit) throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "请缩小搜索范围后再分页"
        );
        List<Object> filters = new ArrayList<>();
        filters.add(Map.of("term", Map.of("status", "30502")));
        if (!category.isBlank()) filters.add(Map.of("term", Map.of("category", category)));
        String normalized = normalize(query);
        if (!normalized.isEmpty()) {
            // 转义 ES 通配符，用户输入星号或问号时按普通字符查找。
            String pattern =
                "*" +
                normalized.replace("\\", "\\\\").replace("*", "\\*").replace("?", "\\?") +
                "*";
            filters.add(
                Map.of(
                    "bool",
                    Map.of(
                        "minimum_should_match",
                        1,
                        "should",
                        List.of(
                            Map.of("wildcard", Map.of("searchName", Map.of("value", pattern))),
                            Map.of("wildcard", Map.of("searchTags", Map.of("value", pattern)))
                        )
                    )
                )
            );
        }
        JsonNode response = search(
            Map.of(
                "query",
                Map.of("bool", Map.of("filter", filters)),
                "from",
                (current - 1) * limit,
                "size",
                limit,
                "track_total_hits",
                true,
                "sort",
                List.of(Map.of("eventId", "desc"), Map.of("id", "desc"))
            )
        );
        JsonNode hits = response.path("hits");
        List<Map<String, Object>> items = new ArrayList<>();
        for (JsonNode hit : hits.path("hits"))
            items.add(json.convertValue(hit.path("_source").path("snapshot"), Map.class));
        return new PageResult<>(items, hits.path("total").path("value").asLong(), current, limit);
    }

    /** 只聚合已发布文档分类，不依赖当前搜索页的条数。 */
    @Override
    public List<String> categories() {
        ensureIndex();
        JsonNode response = search(
            Map.of(
                "size",
                0,
                "query",
                Map.of("term", Map.of("status", "30502")),
                "aggs",
                Map.of(
                    "categories",
                    Map.of(
                        "terms",
                        Map.of("field", "category", "size", 1000, "order", Map.of("_key", "asc"))
                    )
                )
            )
        );
        List<String> items = new ArrayList<>();
        for (JsonNode bucket : response.path("aggregations").path("categories").path("buckets"))
            items.add(bucket.path("key").asString());
        return items;
    }

    /** ES 实时读取文档仍要校验发布状态，下架墓碑不得作为公开详情返回。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> uncachedGet(long courseId) {
        ensureIndex();
        try {
            String body = client
                .get()
                .uri("/{index}/_doc/{id}", index, courseId)
                .retrieve()
                .body(String.class);
            JsonNode source = json.readTree(body).path("_source");
            if (
                !"30502".equals(source.path("status").asString())
            ) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "课程未发布或不存在");
            return json.convertValue(source.path("snapshot"), Map.class);
        } catch (RestClientResponseException error) {
            if (error.getStatusCode().value() == 404) throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "课程未发布或不存在"
            );
            throw error;
        }
    }

    /** 懒创建专用索引，ES 停机时服务仍可启动，后续消息重试可再次创建。 */
    private synchronized void ensureIndex() {
        if (initialized) return;
        int status = client
            .head()
            .uri("/{index}", index)
            .exchange((request, response) -> response.getStatusCode().value());
        if (status == 404) {
            Map<String, ? extends Map<String, ?>> properties = Map.of(
                "id",
                Map.of("type", "long"),
                "eventId",
                Map.of("type", "long"),
                "status",
                Map.of("type", "keyword"),
                "searchName",
                Map.of("type", "wildcard"),
                "searchTags",
                Map.of("type", "wildcard"),
                "category",
                Map.of("type", "keyword"),
                "snapshot",
                Map.of("type", "object", "enabled", false)
            );
            try {
                client
                    .put()
                    .uri("/{index}", index)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(
                        json.writeValueAsString(
                            Map.of(
                                "settings",
                                Map.of("number_of_shards", 1, "number_of_replicas", 0),
                                "mappings",
                                Map.of("dynamic", "strict", "properties", properties)
                            )
                        )
                    )
                    .retrieve()
                    .toBodilessEntity();
            } catch (RestClientResponseException error) {
                // 多实例同时初始化时，仅忽略已经由另一实例创建索引的情况。
                if (
                    error.getStatusCode().value() != 400 ||
                    !error.getResponseBodyAsString().contains("resource_already_exists_exception")
                ) throw error;
            }
        } else if (status != 200) throw new IllegalStateException("课程索引不可访问");
        initialized = true;
    }

    /** 用已有 JSON 处理 ES 查询响应，不在应用层再次筛选或分页。 */
    private JsonNode search(Map<String, Object> body) {
        String response = client
            .post()
            .uri("/{index}/_search", index)
            .contentType(MediaType.APPLICATION_JSON)
            .body(json.writeValueAsString(body))
            .retrieve()
            .body(String.class);
        return json.readTree(response);
    }

    /** 统一全角英文、大小写和首尾空格，保持与用户原有搜索体验一致。 */
    private String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKC).trim().toLowerCase(Locale.ROOT);
    }
}
