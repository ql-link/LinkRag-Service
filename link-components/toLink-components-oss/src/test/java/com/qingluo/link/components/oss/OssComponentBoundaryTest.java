package com.qingluo.link.components.oss;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OssComponentBoundaryTest {

    @Test
    void Should_Not_Expose_Upload_Controller_From_Oss_Component() {
        assertThatThrownBy(() -> Class.forName("com.qingluo.link.components.oss.controller.OssFileController"))
            .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void Should_Not_Expose_Public_Preview_Controller_From_Oss_Component() {
        assertThatThrownBy(() -> Class.forName("com.qingluo.link.components.oss.controller.LocalOssPreviewController"))
            .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void Should_Not_Keep_Business_Upload_Rules_In_Oss_Component() {
        assertThatThrownBy(() -> Class.forName("com.qingluo.link.components.oss.model.OssFileConfig"))
            .isInstanceOf(ClassNotFoundException.class);
    }
}
