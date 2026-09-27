import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.*;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 运行迁移前停止业务服务。连接凭据从 Nacos 读取，不输出到日志。
 */
class NormalizeStatusCodes {

    static final String PREFIX = "bak_20260925_";
    static final ObjectMapper JSON = new ObjectMapper();
    static final Map<String, String> LEVEL = Map.of(
        "204001",
        "30301",
        "204002",
        "30302",
        "204003",
        "30303",
        "200001",
        "30301",
        "200002",
        "30302",
        "200003",
        "30303"
    );
    static final Map<String, String> MODE = Map.of("200002", "30101", "200003", "30102");
    static final Map<String, String> CHARGE = Map.of(
        "201000",
        "30201",
        "201001",
        "30202",
        "70101",
        "30201",
        "70102",
        "30202"
    );
    static final Map<String, String> PUBLISH = Map.of(
        "203001",
        "30501",
        "203002",
        "30502",
        "203003",
        "30503",
        "30404",
        "30502"
    );
    static final Map<String, String> PROCESS = Map.of(
        "0",
        "20300",
        "1",
        "20301",
        "2",
        "20302",
        "3",
        "20303",
        "4",
        "20304"
    );
    static final Map<String, String> ACTIVE = Map.of("0", "10102", "1", "10101", "203002", "10101");

    /**
     * 迁移字段定义，记录目标表列、旧码映射和迁移后的合法值集合。
     */
    record Field(String table, String column, Map<String, String> aliases, Set<String> allowed) {}

    static final List<Field> FIELDS = new ArrayList<>();

    /**
     * 登记需迁移的表字段、旧码映射和合法编码集合，供统一检查和迁移使用。
     */
    static void field(String t, String c, Map<String, String> aliases, String... allowed) {
        FIELDS.add(new Field(t, c, aliases, Set.of(allowed)));
    }

    static {
        for (String t : List.of("course_base", "course_publish", "course_publish_pre")) {
            field("sunflower_class." + t, "grade", LEVEL, "30301", "30302", "30303");
            field("sunflower_class." + t, "teachmode", MODE, "30101", "30102");
        }
        for (String t : List.of("course_market", "course_publish", "course_publish_pre"))
            field("sunflower_class." + t, "charge", CHARGE, "30201", "30202");
        field(
            "sunflower_class.course_base",
            "status",
            Map.of("203001", "30501", "203002", "30502", "203003", "30503", "30403", "30502"),
            "30501",
            "30502",
            "30503"
        );
        field("sunflower_class.course_publish", "status", PUBLISH, "30501", "30502", "30503");
        for (String t : List.of("course_base", "course_audit"))
            field(
                "sunflower_class." + t,
                "audit_status",
                Map.of(),
                "30401",
                "30402",
                "30403",
                "30404"
            );
        field(
            "sunflower_class.course_publish_pre",
            "status",
            Map.of(),
            "30401",
            "30402",
            "30403",
            "30404"
        );
        field("sunflower_class.teachplan", "status", ACTIVE, "10101", "10102");
        for (String t : List.of("media_files", "media_process", "media_process_history"))
            field("media." + t, "status", PROCESS, "20300", "20301", "20302", "20303", "20304");
        field("media.media_files", "audit_status", Map.of(), "20201", "20202", "20203");
        field("media.media_files", "file_type", Map.of(), "20101", "20102", "20103");
    }

    /**
     * 在原库名下为指定表添加备份前缀，返回备份表的完整名称。
     */
    static String backup(String t) {
        return t.substring(0, t.indexOf('.') + 1) + PREFIX + t.substring(t.indexOf('.') + 1);
    }

    /**
     * 从配置文本提取指定键的值；未找到时抛出异常，不输出连接凭据。
     */
    static String value(String text, String key) {
        var m = Pattern.compile("(?m)^\\s*" + key + ":\\s*(.+)$").matcher(text);
        if (!m.find()) throw new IllegalArgumentException("Missing configuration: " + key);
        return m.group(1).trim();
    }

    /**
     * 检查字段编码是否合法；迁移后还检查快照 JSON，迁移前阻止无法判定的历史发布状态。
     */
    static void verify(Connection c, boolean migrated) throws Exception {
        for (var f : FIELDS)
            try (
                var s = c.createStatement();
                var r = s.executeQuery(
                    "SELECT DISTINCT " +
                        f.column +
                        " FROM " +
                        f.table +
                        " WHERE " +
                        f.column +
                        " IS NOT NULL"
                )
            ) {
                while (r.next()) {
                    String v = r.getString(1);
                    if (
                        !f.allowed.contains(v) && (migrated || !f.aliases.containsKey(v))
                    ) throw new IllegalStateException(
                        "Unknown code: " + f.table + "." + f.column + "=" + v
                    );
                }
            }
        if (migrated) for (String table : List.of(
            "sunflower_class.course_publish",
            "sunflower_class.course_publish_pre"
        )) {
            try (
                var statement = c.createStatement();
                var rows = statement.executeQuery("SELECT id,market,teachplan FROM " + table)
            ) {
                while (rows.next())
                    for (int i = 2; i <= 3; i++) {
                        String raw = rows.getString(i);
                        if (raw == null || raw.isBlank()) continue;
                        JsonNode original = JSON.readTree(raw),
                            normalized = original.deepCopy();
                        normalizeJson(normalized);
                        if (!original.equals(normalized)) throw new IllegalStateException(
                            "Legacy snapshot code: " + table + " id=" + rows.getLong(1)
                        );
                    }
            }
        }
        // 历史缺陷曾把审核中写入发布状态；仅在已通过审核的发布快照可佐证时修复。
        if (!migrated) try (
            var s = c.createStatement();
            var r = s.executeQuery(
                "SELECT b.id FROM sunflower_class.course_base b LEFT JOIN sunflower_class.course_publish p ON p.id=b.id WHERE b.status='30403' AND (b.audit_status<>'30404' OR p.status IS NULL OR p.status NOT IN ('30404','203002','30502'))"
            )
        ) {
            if (r.next()) throw new IllegalStateException(
                "Ambiguous publication state; migration stopped"
            );
        }
    }

    /**
     * 递归转换快照 JSON 中的收费、等级、教学模式和记录状态，保留数值状态原有类型。
     */
    static void normalizeJson(JsonNode node) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            for (String key : List.of("charge", "grade", "teachmode", "status")) {
                JsonNode v = obj.get(key);
                if (v == null || v.isNull()) continue;
                Map<String, String> map = switch (key) {
                    case "charge" -> CHARGE;
                    case "grade" -> LEVEL;
                    case "teachmode" -> MODE;
                    default -> ACTIVE;
                };
                String replacement = map.get(v.asText());
                if (replacement != null) {
                    if (key.equals("status") && v.isNumber()) obj.put(
                        key,
                        Integer.parseInt(replacement)
                    );
                    else obj.put(key, replacement);
                }
            }
            obj.elements().forEachRemaining(NormalizeStatusCodes::normalizeJson);
        } else if (node.isArray()) node.elements().forEachRemaining(
            NormalizeStatusCodes::normalizeJson
        );
    }

    /**
     * 调整状态字段的数据库默认值；migrated 决定使用新五位编码还是回退默认值。
     */
    static void defaults(Connection c, boolean migrated) throws Exception {
        try (var s = c.createStatement()) {
            for (String table : List.of(
                "course_base",
                "course_publish",
                "course_publish_pre",
                "teachplan"
            )) {
                String code = table.equals("teachplan")
                    ? "10101"
                    : table.equals("course_publish_pre")
                      ? "30402"
                      : "30501";
                s.execute(
                    "ALTER TABLE sunflower_class." +
                        table +
                        " ALTER COLUMN status SET DEFAULT '" +
                        (migrated ? code : "1") +
                        "'"
                );
            }
            s.execute(
                "ALTER TABLE media.media_files ALTER COLUMN status SET DEFAULT '" +
                    (migrated ? "20301" : "1") +
                    "'"
            );
        }
    }

    /**
     * 按 check、apply、verify 或 rollback 执行状态编码检查、迁移、验证或回滚；写入前应停止业务服务。
     */
    public static void main(String[] args) throws Exception {
        String action = args.length == 0 ? "check" : args[0];
        if (
            !Set.of("check", "apply", "verify", "rollback").contains(action)
        ) throw new IllegalArgumentException("check|apply|verify|rollback");
        String server = System.getenv().getOrDefault("NACOS_SERVER_ADDR", "localhost:8848");
        String ns = System.getenv().getOrDefault("NACOS_NAMESPACE", "dev");
        var config = HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(
                    URI.create(
                        "http://" +
                            server +
                            "/nacos/v1/cs/configs?dataId=content-service-dev.yaml&group=sunflower_class&tenant=" +
                            ns
                    )
                ).build(),
                HttpResponse.BodyHandlers.ofString()
            )
            .body();
        try (
            var c = DriverManager.getConnection(
                value(config, "url"),
                value(config, "username"),
                value(config, "password")
            )
        ) {
            var tables = new LinkedHashSet<String>();
            // 从迁移字段提取并去重表名，确保每张表只备份一次。
            FIELDS.forEach(f -> tables.add(f.table));
            tables.add("system.dictionary");
            if (action.equals("check") || action.equals("verify")) {
                verify(c, action.equals("verify"));
                System.out.println(action + " passed; fields=" + FIELDS.size());
                return;
            }
            if (action.equals("rollback")) {
                c.setAutoCommit(false);
                try (var s = c.createStatement()) {
                    for (String t : tables) {
                        if (t.equals("system.dictionary")) {
                            s.executeUpdate("DELETE FROM system.dictionary WHERE code='203'");
                            continue;
                        }
                        var cols = new LinkedHashSet<String>();
                        FIELDS.stream()
                            // 只保留当前回滚表的字段定义。
                            .filter(f -> f.table.equals(t))
                            // 收集需恢复的列名，避免覆盖本次迁移以外的业务字段。
                            .forEach(f -> cols.add(f.column));
                        if (t.endsWith("course_publish") || t.endsWith("course_publish_pre")) {
                            cols.add("market");
                            cols.add("teachplan");
                        }
                        s.executeUpdate(
                            "UPDATE " +
                                t +
                                " a JOIN " +
                                backup(t) +
                                " b ON a.id=b.id SET " +
                                String.join(
                                    ",",
                                    cols
                                        .stream()
                                        // 将每个列名转换为从备份表恢复原表字段的 SQL 赋值片段。
                                        .map(col -> "a." + col + "=b." + col)
                                        .toList()
                                )
                        );
                    }
                    c.commit();
                    defaults(c, false);
                    System.out.println(
                        "Original fields restored; restore previous application before starting services."
                    );
                } catch (Exception e) {
                    c.rollback();
                    throw e;
                }
                return;
            }
            verify(c, false);
            try (
                var s = c.createStatement();
                var r = s.executeQuery("SELECT count(*) FROM system.dictionary WHERE code='203'")
            ) {
                r.next();
                if (r.getInt(1) != 0) throw new IllegalStateException(
                    "Dictionary 203 already exists; inspect before migration"
                );
            }
            // 修改数据前先创建独立备份表；备份表已存在时终止，避免覆盖原始备份。
            try (var s = c.createStatement()) {
                for (String t : tables) s.execute("CREATE TABLE " + backup(t) + " LIKE " + t);
            }
            c.setAutoCommit(false);
            try (var s = c.createStatement()) {
                for (String t : tables)
                    s.executeUpdate("INSERT INTO " + backup(t) + " SELECT * FROM " + t);
                for (var f : FIELDS)
                    for (var entry : f.aliases.entrySet())
                        try (
                            var p = c.prepareStatement(
                                "UPDATE " +
                                    f.table +
                                    " SET " +
                                    f.column +
                                    "=? WHERE " +
                                    f.column +
                                    "=?"
                            )
                        ) {
                            p.setString(1, entry.getValue());
                            p.setString(2, entry.getKey());
                            p.executeUpdate();
                        }
                s.executeUpdate(
                    "UPDATE media.media_files SET status='20302' WHERE file_type<>'20102' AND status='20301'"
                );
                for (String table : List.of(
                    "sunflower_class.course_publish",
                    "sunflower_class.course_publish_pre"
                )) {
                    try (
                        var r = s.executeQuery("SELECT id,market,teachplan FROM " + table);
                        var p = c.prepareStatement(
                            "UPDATE " + table + " SET market=?,teachplan=? WHERE id=?"
                        )
                    ) {
                        while (r.next()) {
                            for (int i = 2; i <= 3; i++) {
                                String raw = r.getString(i);
                                if (raw == null || raw.isBlank()) {
                                    p.setString(i - 1, raw);
                                    continue;
                                }
                                var node = JSON.readTree(raw);
                                normalizeJson(node);
                                p.setString(i - 1, JSON.writeValueAsString(node));
                            }
                            p.setLong(3, r.getLong(1));
                            p.addBatch();
                        }
                        p.executeBatch();
                    }
                }
                s.executeUpdate(
                    "INSERT INTO system.dictionary(name,code,item_values) VALUES ('媒资处理状态','203','[{\"code\":\"20300\",\"desc\":\"隐藏\"},{\"code\":\"20301\",\"desc\":\"待处理\"},{\"code\":\"20302\",\"desc\":\"可用\"},{\"code\":\"20303\",\"desc\":\"处理失败\"},{\"code\":\"20304\",\"desc\":\"处理中\"}]')"
                );
                verify(c, true);
                c.commit();
                defaults(c, true);
                System.out.println(
                    "Migration committed; original rows backed up with prefix " + PREFIX
                );
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }
}
