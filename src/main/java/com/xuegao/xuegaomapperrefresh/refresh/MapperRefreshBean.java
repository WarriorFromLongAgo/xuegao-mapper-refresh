package com.xuegao.xuegaomapperrefresh.refresh;

import com.xuegao.framework.mybatis.CommonXMLMapperBuilder;
import com.xuegao.framework.mybatis.KeyGenMode;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import javax.sql.DataSource;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.boot.autoconfigure.MybatisProperties;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.CollectionUtils;

public class MapperRefreshBean {
    private ApplicationContext context;
    private MybatisProperties properties;
    private ResourceLoader resourceLoader = new DefaultResourceLoader();
    private String keyGenMode;
    private DataSource dataSource;
    private DefaultSqlSessionFactory sqlSessionFactory;
    private SqlSessionTemplate sqlSessionTemplate;

    public MapperRefreshBean(MybatisProperties properties, String keyGenMode, DataSource dataSource, ApplicationContext context) {
        this.properties = properties;
        this.keyGenMode = keyGenMode;
        this.dataSource = dataSource;
        this.context = context;
    }

    private void addBean(String beanName, Class beanClass, Object... constructValues) {
        DefaultListableBeanFactory beanDefReg = (DefaultListableBeanFactory)((ConfigurableApplicationContext)this.context).getBeanFactory();
        BeanDefinitionBuilder beanDefBuilder = BeanDefinitionBuilder.genericBeanDefinition(beanClass);
        if (constructValues != null && constructValues.length > 0) {
            for(int i = 0; i < constructValues.length; ++i) {
                beanDefBuilder.addConstructorArgValue(constructValues[i]);
            }
        }

        BeanDefinition beanDef = beanDefBuilder.getBeanDefinition();
        beanDefReg.registerBeanDefinition(beanName, beanDef);
    }

    private void removeBean(String beanName) {
        DefaultListableBeanFactory beanDefReg = (DefaultListableBeanFactory)((ConfigurableApplicationContext)this.context).getBeanFactory();
        beanDefReg.getBeanDefinition(beanName);
        beanDefReg.removeBeanDefinition(beanName);
    }

    public void refreshSqlSessionFacotry() throws Exception {
        SqlSessionFactory oldSqlSessionFactory = (SqlSessionFactory)this.context.getBean("sqlSessionFactory");
        Configuration oldConfiguration = oldSqlSessionFactory.getConfiguration();
        CommonXMLMapperBuilder builder = new CommonXMLMapperBuilder();
        builder.setBaseResultMap("BaseResultMap");
        builder.setBaseTableName("BaseTable");
        builder.setGenerationType("GenerationType");
        builder.setBaseColumns("BaseColumns");
        builder.setKeyGenMode(KeyGenMode.parse(StringUtils.defaultString(this.keyGenMode, KeyGenMode.IDENTITY.getCode())));
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(this.dataSource);
        if (this.properties.getConfigLocation() != null) {
            factory.setConfigLocation(this.resourceLoader.getResource(this.properties.getConfigLocation()));
        }

        Configuration configuration = this.properties.getConfiguration();
        if (Objects.nonNull(configuration)) {
            Properties variables = configuration.getVariables();
            if (!CollectionUtils.isEmpty(variables)) {
                factory.setConfigurationProperties(variables);
            }
        }

        factory.setTypeAliasesPackage(this.properties.getTypeAliasesPackage());
        factory.setTypeHandlersPackage(this.properties.getTypeHandlersPackage());
        Resource[] mapperLocations = builder.builderCommonMapper(this.properties.resolveMapperLocations());
        factory.setMapperLocations(mapperLocations);
        SqlSessionFactory sqlSessionFactory = factory.getObject();
        List<Interceptor> interceptors = oldConfiguration.getInterceptors();
        if (!CollectionUtils.isEmpty(interceptors)) {
            Iterator var9 = interceptors.iterator();

            while(var9.hasNext()) {
                Interceptor interceptor = (Interceptor)var9.next();
                sqlSessionFactory.getConfiguration().addInterceptor(interceptor);
            }
        }

        SqlSessionFactory sqlSessionFactory = new DefaultSqlSessionFactory(sqlSessionFactory.getConfiguration());
        this.sqlSessionTemplate = new SqlSessionTemplate(sqlSessionFactory);
    }

    public DefaultSqlSessionFactory getSqlSessionFactory() {
        return this.sqlSessionFactory;
    }

    public SqlSessionTemplate getSqlSessionTemplate() {
        return this.sqlSessionTemplate;
    }
}
