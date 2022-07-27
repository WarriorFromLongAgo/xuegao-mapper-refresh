package com.xuegao.xuegaomapperrefresh.refresh;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.boot.autoconfigure.MybatisProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

@Configuration
@EnableConfigurationProperties({MybatisProperties.class})
@AutoConfigureAfter({DataSourceAutoConfiguration.class})
@ConditionalOnClass({SqlSessionFactory.class, SqlSessionFactoryBean.class})
public class MapperRefreshConfig implements ApplicationContextAware, ApplicationListener<ApplicationReadyEvent> {
    Logger logger = LoggerFactory.getLogger(MapperRefreshConfig.class);
    private Map<String, Long> fileStatus = new HashMap();
    private static Integer DelaySeconds = 3;
    @Autowired
    private MybatisProperties properties;
    @Autowired
    private ResourceLoader resourceLoader = new DefaultResourceLoader();
    @Value("${mybatis.keyGenMode:IDENTITY}")
    private String keyGenMode;
    ApplicationContext context;

    public MapperRefreshConfig() {
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent applicationReadyEvent) {
        if (this.properties != null) {
            Resource[] resources = this.properties.resolveMapperLocations();
            Resource[] var3 = resources;
            int var4 = resources.length;

            for (int var5 = 0; var5 < var4; ++var5) {
                Resource resource = var3[var5];

                try {
                    URL url = resource.getURL();
                    File file = new File(url.getFile());
                    this.fileStatus.put(resource.getFilename(), file.lastModified());
                } catch (IOException var9) {
                    var9.printStackTrace();
                }
            }
        }
    }

    @Bean(
            name = {"mapperRefreshBean"}
    )
    public MapperRefreshBean refreshBean(DataSource dataSource) {
        MapperRefreshBean refreshBean = new MapperRefreshBean(this.properties, this.keyGenMode, dataSource, this.context);
        this.startRereshTask(refreshBean);
        return refreshBean;
    }

    private void startRereshTask(MapperRefreshBean mapperRefreshBean) {
        if (!Arrays.stream(this.context.getEnvironment().getActiveProfiles()).noneMatch((t) -> {
            return "dev".equals(t);
        })) {
            (new Thread(() -> {
                while (true) {
                    Resource[] resources = this.properties.resolveMapperLocations();
                    boolean needFresh = false;
                    Resource[] var4 = resources;
                    int var5 = resources.length;

                    for (int var6 = 0; var6 < var5; ++var6) {
                        Resource resource = var4[var6];
                        URL url;
                        File file;
                        if (this.fileStatus.keySet().contains(resource.getFilename())) {
                            try {
                                url = resource.getURL();
                                file = new File(url.getFile());
                                long lastModified = file.lastModified();
                                if (!Objects.equals(lastModified, this.fileStatus.get(resource.getFilename()))) {
                                    needFresh = true;
                                    this.fileStatus.put(resource.getFilename(), lastModified);
                                }
                            } catch (IOException var14) {
                                var14.printStackTrace();
                            }
                        } else {
                            try {
                                url = resource.getURL();
                                file = new File(url.getFile());
                                this.fileStatus.put(resource.getFilename(), file.lastModified());
                            } catch (IOException var15) {
                                var15.printStackTrace();
                            }
                        }
                    }

                    if (needFresh) {
                        try {
                            mapperRefreshBean.refreshSqlSessionFacotry();
                            this.logger.info("刷新mapper");
                        } catch (Exception var13) {
                            var13.printStackTrace();
                        }
                    }

                    try {
                        Thread.sleep((long) (DelaySeconds * 1000));
                    } catch (InterruptedException var12) {
                        var12.printStackTrace();
                    }
                }
            })).start();
        }
    }
}
