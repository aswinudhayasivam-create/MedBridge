package com.medbridge.demo;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;
class DemoGuardTest {
    @Test void demoFlagCannotActivateHostedOrRemoteWrites(){
        try(var context=new AnnotationConfigApplicationContext()){
            context.setEnvironment(new MockEnvironment().withProperty("app.demo-workflows","true"));
            context.addBeanFactoryPostProcessor(DemoConfiguration.demoEnvironmentGuard(context.getEnvironment()));
            assertThrows(IllegalStateException.class,context::refresh);
        }
    }
    @Test void demoDefaultsOffWithoutLocalDatabaseConfiguration(){
        try(var context=new AnnotationConfigApplicationContext()){
            context.setEnvironment(new MockEnvironment());
            context.addBeanFactoryPostProcessor(DemoConfiguration.demoEnvironmentGuard(context.getEnvironment()));
            assertDoesNotThrow(context::refresh);
        }
    }
}
