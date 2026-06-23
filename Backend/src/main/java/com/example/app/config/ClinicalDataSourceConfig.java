package com.example.app.config;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import jakarta.persistence.EntityManagerFactory;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
    basePackages = "com.example.app.repository.clinical",
    entityManagerFactoryRef = "clinicalEntityManagerFactory",
    transactionManagerRef = "clinicalTransactionManager"
)
public class ClinicalDataSourceConfig {

    @Bean(name = "clinicalDataSourceProperties")
    @ConfigurationProperties("clinical.datasource")
    public DataSourceProperties clinicalDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "clinicalDataSource")
    public DataSource clinicalDataSource(
            @Qualifier("clinicalDataSourceProperties") DataSourceProperties props) {
        return props.initializeDataSourceBuilder().build();
    }

    @Bean(name = "clinicalEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean clinicalEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("clinicalDataSource") DataSource dataSource) {
        Map<String, Object> jpaProps = new HashMap<>();
        jpaProps.put("hibernate.hbm2ddl.auto", "update");
        jpaProps.put("hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
        jpaProps.put("hibernate.show_sql", "false");
        jpaProps.put("hibernate.format_sql", "true");
        return builder
            .dataSource(dataSource)
            .packages("com.example.app.model")
            .persistenceUnit("clinical")
            .properties(jpaProps)
            .build();
    }

    @Bean(name = "clinicalTransactionManager")
    public PlatformTransactionManager clinicalTransactionManager(
            @Qualifier("clinicalEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }
}
