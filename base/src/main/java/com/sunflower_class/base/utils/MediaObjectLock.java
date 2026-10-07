package com.sunflower_class.base.utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 复用同一MySQL实例的命名锁，串行化跨库媒资绑定与删除，事务提交后才释放。 */
public final class MediaObjectLock implements AutoCloseable {

    private final Connection connection;
    private final String name;

    /** 专用连接保留锁到整个业务事务结束，不新增锁服务。 */
    public MediaObjectLock(DataSource source, String id) {
        if (id == null || !id.matches("[a-fA-F0-9]{32}")) throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "媒资编号无效"
        );
        name = "sunflower:media:" + id.toLowerCase();
        Connection acquired = null;
        try {
            acquired = source.getConnection();
            try (PreparedStatement statement = acquired.prepareStatement("SELECT GET_LOCK(?,5)")) {
                statement.setString(1, name);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next() || result.getInt(1) != 1) throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "媒资正在被其他操作使用，请稍后重试"
                    );
                }
            }
            connection = acquired;
        } catch (Exception error) {
            if (acquired != null) try {
                acquired.close();
            } catch (Exception ignored) {}
            if (error instanceof ResponseStatusException response) throw response;
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "无法取得媒资操作锁");
        }
    }

    /** 业务事务提交后释放命名锁，再归还连接。 */
    @Override
    public void close() {
        try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            statement.setString(1, name);
            statement.execute();
        } catch (Exception error) {
            throw new IllegalStateException("媒资锁释放失败", error);
        } finally {
            try {
                connection.close();
            } catch (Exception ignored) {}
        }
    }
}
