package com.xuegao.xuegaomapperrefresh.refresh;

import java.lang.reflect.Type;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.mybatis.spring.SqlSessionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Aspect
@Profile({"dev"})
public class MapperExecuteAop implements ApplicationContextAware {
    Logger logger = LoggerFactory.getLogger(MapperExecuteAop.class);
    ApplicationContext context;

    public MapperExecuteAop() {
    }

    @Around("execution(* com.xuegao..mapper.*Mapper.*(..))")
    public Object rounding(ProceedingJoinPoint joinPoint) throws Throwable {
        this.logger.info("执行mapper拦截");
        Object proceed = null;
        Type[] genericInterfaces = AopUtils.getTargetClass(joinPoint.getTarget()).getGenericInterfaces();
        if (genericInterfaces != null) {
            int i = 0;
            if (i < genericInterfaces.length) {
                if ("com.xuegao.framework.mapper.GenericMapper".equals(genericInterfaces[i].getTypeName())) {
                    proceed = joinPoint.proceed();
                    return proceed;
                }

                Object[] args = joinPoint.getArgs();
                Signature signature = joinPoint.getSignature();
                MapperRefreshBean mapperRefreshBean = (MapperRefreshBean)this.context.getBean("mapperRefreshBean");
                SqlSessionTemplate sqlSessionTemplate = null;
                if (mapperRefreshBean != null && mapperRefreshBean.getSqlSessionTemplate() != null) {
                    sqlSessionTemplate = mapperRefreshBean.getSqlSessionTemplate();
                } else {
                    sqlSessionTemplate = (SqlSessionTemplate)this.context.getBean("sqlSessionTemplate");
                }

                Class cl = this.getClass().getClassLoader().loadClass(genericInterfaces[i].getTypeName());
                Object mapper = sqlSessionTemplate.getMapper(cl);
                MethodSignature methodSignature = (MethodSignature)signature;
                proceed = MethodUtils.invokeMethod(mapper, methodSignature.getName(), args);
                return proceed;
            }
        }

        return proceed;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
    }
}
