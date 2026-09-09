package com.skilltracker.domain;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.BasicBinder;
import org.hibernate.type.descriptor.jdbc.BasicExtractor;
import org.hibernate.type.descriptor.jdbc.JdbcType;

/**
 * Binds a converted {@code String} to a native PostgreSQL enum column.
 *
 * <p>Hibernate's built-in {@code NAMED_ENUM} type derives the labels from the Java enum constants,
 * which would force those constants to be spelled exactly like the database labels. Sending the
 * value as {@link Types#OTHER} lets PostgreSQL coerce it instead, so {@link TaskStatus} keeps
 * conventional Java names while the stored labels stay {@code todo}, {@code in_progress}, and so on.
 */
public class PostgresEnumJdbcType implements JdbcType {

    @Override
    public int getJdbcTypeCode() {
        return Types.OTHER;
    }

    @Override
    public <X> ValueBinder<X> getBinder(JavaType<X> javaType) {
        return new BasicBinder<>(javaType, this) {
            @Override
            protected void doBind(PreparedStatement statement, X value, int index, WrapperOptions options)
                    throws SQLException {
                statement.setObject(index, getJavaType().unwrap(value, String.class, options), Types.OTHER);
            }

            @Override
            protected void doBind(CallableStatement statement, X value, String name, WrapperOptions options)
                    throws SQLException {
                statement.setObject(name, getJavaType().unwrap(value, String.class, options), Types.OTHER);
            }
        };
    }

    @Override
    public <X> ValueExtractor<X> getExtractor(JavaType<X> javaType) {
        return new BasicExtractor<>(javaType, this) {
            @Override
            protected X doExtract(ResultSet resultSet, int index, WrapperOptions options) throws SQLException {
                return getJavaType().wrap(resultSet.getString(index), options);
            }

            @Override
            protected X doExtract(CallableStatement statement, int index, WrapperOptions options) throws SQLException {
                return getJavaType().wrap(statement.getString(index), options);
            }

            @Override
            protected X doExtract(CallableStatement statement, String name, WrapperOptions options)
                    throws SQLException {
                return getJavaType().wrap(statement.getString(name), options);
            }
        };
    }
}
