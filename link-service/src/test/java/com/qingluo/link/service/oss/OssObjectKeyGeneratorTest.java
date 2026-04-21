package com.qingluo.link.service.oss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class OssObjectKeyGeneratorTest {

    @Test
    void Should_Expose_Object_Key_Generator_Class_For_Oss_Service_Layer() {
        assertThatCode(() -> Class.forName("com.qingluo.link.service.oss.OssObjectKeyGenerator"))
            .doesNotThrowAnyException();
    }

    @Test
    void Should_Generate_Object_Key_With_Suffix_When_File_Suffix_Is_Present() throws Exception {
        Class<?> generatorClass = Class.forName("com.qingluo.link.service.oss.OssObjectKeyGenerator");
        Constructor<?> constructor = generatorClass.getDeclaredConstructor();
        Object generator = constructor.newInstance();
        Method method = generatorClass.getMethod("generate", String.class, String.class);

        String objectKey = (String) method.invoke(generator, "avatar", "png");

        assertThat(objectKey).startsWith("avatar/");
        assertThat(objectKey).endsWith(".png");
    }

    @Test
    void Should_Generate_Object_Key_Without_Suffix_When_File_Suffix_Is_Blank() throws Exception {
        Class<?> generatorClass = Class.forName("com.qingluo.link.service.oss.OssObjectKeyGenerator");
        Constructor<?> constructor = generatorClass.getDeclaredConstructor();
        Object generator = constructor.newInstance();
        Method method = generatorClass.getMethod("generate", String.class, String.class);

        String objectKey = (String) method.invoke(generator, "cert", "");

        assertThat(objectKey).startsWith("cert/");
        assertThat(objectKey).doesNotContain("..");
        assertThat(objectKey.substring("cert/".length())).doesNotContain(".");
    }
}
