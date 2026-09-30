package com.sunflower_class.service.search.service.impl;

import com.sunflower_class.base.course.CourseEvent;
import com.sunflower_class.base.model.PageResult;
import com.sunflower_class.model.dto.CourseSearchDocument;
import com.sunflower_class.service.search.service.CourseSearchService;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/** 通过 Spring 原生 RestClient 访问现有 Elasticsearch，无须新增另一套客户端依赖。 */
@Service
public class CourseSearchServiceImpl implements CourseSearchService {

    private final RestClient client;
    private final JsonMapper json;
    private final String index;
    private volatile boolean initialized;

    /** 地址与索引名由 Nacos 提供，设置有限超时供消费重试恢复故障。 */
    public CourseSearchServiceImpl(
        JsonMapper json,
        @Value("${sunflower.elasticsearch.endpoint}") String endpoint,
        @Value("${sunflower.elasticsearch.index}") String index
    ) {
        if (!index.matches("[a-z0-9_][a-z0-9_-]*")) throw new IllegalArgumentException(
            "课程索引名无效"
        );
        this.json = json;
        this.index = index;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.client = RestClient.builder().baseUrl(endpoint).requestFactory(factory).build();
    }

    /** 按事件外部版本写索引；下架保留版本墓碑，避免旧发布消息让课程重新出现。 */
    @Override
    @SuppressWarnings("unchecked")
    public void save(CourseEvent event) {
        ensureIndex();
        Map<String, Object> snapshot = json.readValue(event.snapshot(), Map.class);
        String category = Objects.toString(snapshot.get("mtName"), "");
        if (category.isBlank()) category = Objects.toString(snapshot.get("stName"), "课程");
        var document = new CourseSearchDocument(
            event.courseId(),
            event.eventId(),
            event.status(),
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
                    event.courseId(),
                    event.eventId()
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
    }

    /** 搜索结果来自 ES，关键词规范化后按标题/标签包含匹配，默认页大小由接口设为十条。 */
    @Override
    @SuppressWarnings("unchecked")
    public PageResult<Map<String, Object>> list(
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
        var response = search(
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
        var hits = response.path("hits");
        List<Map<String, Object>> items = new ArrayList<>();
        for (var hit : hits.path("hits"))
            items.add(json.convertValue(hit.path("_source").path("snapshot"), Map.class));
        return new PageResult<>(items, hits.path("total").path("value").asLong(), current, limit);
    }

    /** 只聚合已发布文档分类，不依赖当前搜索页的条数。 */
    @Override
    public List<String> categories() {
        ensureIndex();
        var response = search(
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
        for (var bucket : response.path("aggregations").path("categories").path("buckets"))
            items.add(bucket.path("key").asString());
        return items;
    }

    /** ES 实时读取文档仍要校验发布状态，下架墓碑不得作为公开详情返回。 */
    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> get(long courseId) {
        ensureIndex();
        try {
            String body = client
                .get()
                .uri("/{index}/_doc/{id}", index, courseId)
                .retrieve()
                .body(String.class);
            var source = json.readTree(body).path("_source");
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
            var properties = Map.of(
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
    private tools.jackson.databind.JsonNode search(Map<String, Object> body) {
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
