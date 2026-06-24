package com.tcgtracker.config;

import java.net.URI;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Two JDBC datasources:
 *
 *  - MySQL  (@Primary) — write side; JPA/Hibernate binds to this one.
 *  - Postgres (Supabase) — read-side catalog; accessed only via the
 *    {@code postgresJdbcTemplate} bean, never JPA.
 *
 * Defining the MySQL datasource explicitly (rather than relying on auto-config)
 * is required: as soon as a second DataSource bean exists, Spring Boot's
 * DataSourceAutoConfiguration backs off, so both must be declared here with a
 * clear @Primary.
 */
@Configuration
public class DataSourceConfig {

    // ── MySQL (write side, primary) ─────────────────────────────────────────
    // Built explicitly (not via @ConfigurationProperties) so DataSourceBuilder
    // maps `url` onto Hikari's `jdbcUrl` correctly.
    @Bean
    @Primary
    public DataSource mysqlDataSource(
        @Value("${spring.datasource.url}") String url,
        @Value("${spring.datasource.username}") String username,
        @Value("${spring.datasource.password}") String password,
        @Value("${spring.datasource.driver-class-name}") String driverClassName
    ) {
        return DataSourceBuilder.create()
            .url(url)
            .username(username)
            .password(password)
            .driverClassName(driverClassName)
            .build();
    }

    // ── Postgres / Supabase (read-side catalog) ─────────────────────────────
    // POSTGRES_DSN is libpq form: postgresql://user:pass@host:port/db
    // Convert to JDBC: jdbc:postgresql://host:port/db + separate user/pass.
    @Bean
    public DataSource postgresDataSource(@Value("${POSTGRES_DSN}") String dsn) {
        URI uri = URI.create(dsn);
        String[] userInfo = uri.getUserInfo().split(":", 2);
        String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + ":" + uri.getPort() + uri.getPath();
        return DataSourceBuilder.create()
            .url(jdbcUrl)
            .username(userInfo[0])
            .password(userInfo.length > 1 ? userInfo[1] : "")
            .driverClassName("org.postgresql.Driver")
            .build();
    }

    @Bean
    public JdbcTemplate postgresJdbcTemplate(@Qualifier("postgresDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }
}
