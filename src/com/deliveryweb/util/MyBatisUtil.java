package com.deliveryweb.util;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import java.io.IOException;
import java.io.InputStream;

/**
 * MyBatis SqlSessionFactory 全局单例。
 * 读操作使用 {@link #withMapper}；写操作统一使用 {@link #withTx} 事务模板。
 */
public final class MyBatisUtil {

    private static final SqlSessionFactory SQL_SESSION_FACTORY;

    static {
        try (InputStream input = Resources.getResourceAsStream("mybatis-config.xml")) {
            SQL_SESSION_FACTORY = new SqlSessionFactoryBuilder().build(input);
        } catch (IOException e) {
            throw new ExceptionInInitializerError("mybatis-config.xml 加载失败：" + e.getMessage());
        }
    }

    private MyBatisUtil() {
    }

    /**
     * 打开会话（自动提交关闭，由调用方负责关闭）。
     */
    public static SqlSession openSession() {
        return SQL_SESSION_FACTORY.openSession();
    }

    /**
     * 只读查询：会话自动关闭。
     */
    public static <M, R> R withMapper(Class<M> mapperClass, MapperCallback<M, R> callback) {
        try (SqlSession session = openSession()) {
            return callback.run(session.getMapper(mapperClass));
        }
    }

    /**
     * 事务模板：成功提交，异常回滚，自动关闭。
     */
    public static <T> T withTx(TxCallback<T> callback) {
        SqlSession session = openSession();
        try {
            T result = callback.run(session);
            session.commit();
            return result;
        } catch (RuntimeException | Error e) {
            session.rollback();
            throw e;
        } finally {
            session.close();
        }
    }

    @FunctionalInterface
    public interface MapperCallback<M, R> {
        R run(M mapper);
    }

    @FunctionalInterface
    public interface TxCallback<T> {
        T run(SqlSession session);
    }
}
