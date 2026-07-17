package com.qingluo.link.service.impl.blog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.Page;
import com.qingluo.link.components.oss.service.IOssService;
import com.qingluo.link.mapper.BlogAssetMapper;
import com.qingluo.link.mapper.BlogPostMapper;
import com.qingluo.link.model.dto.entity.BlogPost;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.model.dto.response.BlogPostPublicListDTO;
import com.qingluo.link.model.enums.BlogPostStatus;
import com.qingluo.link.service.BlogContentStorageService;
import com.qingluo.link.service.cache.BusinessCacheProperties;
import com.qingluo.link.service.cache.PublishedBlogIndexCache;
import java.time.LocalDateTime;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlogPostServiceImplTest {

    @Mock private BlogPostMapper blogPostMapper;
    @Mock private BlogAssetMapper blogAssetMapper;
    @Mock private BlogContentStorageService contentStorage;
    @Mock private IOssService ossService;
    @Mock private PublishedBlogIndexCache publishedBlogIndexCache;
    @Mock private BlogObjectCleanupScheduler cleanupScheduler;

    private BlogPostServiceImpl service;

    @BeforeEach
    void setUp() {
        BusinessCacheProperties properties = new BusinessCacheProperties();
        properties.setBlogPublishedIndexCapacity(100);
        service = new BlogPostServiceImpl(
            blogPostMapper,
            blogAssetMapper,
            contentStorage,
            ossService,
            publishedBlogIndexCache,
            properties,
            cleanupScheduler,
            new ObjectMapper());
    }

    @Test
    void publishedList_usesOneFixedIndexAndQueriesOverflowPageDirectly() {
        Page<BlogPost> indexPage = posts(1, 100, 150);
        Page<BlogPost> overflowPage = posts(101, 10, 150);
        when(blogPostMapper.selectList(any())).thenReturn(indexPage, overflowPage);
        when(publishedBlogIndexCache.get(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<?> loader = invocation.getArgument(0, Supplier.class);
            return loader.get();
        });

        PageResult<BlogPostPublicListDTO> first = service.listPublished(1, 10);
        PageResult<BlogPostPublicListDTO> overflow = service.listPublished(11, 10);

        assertThat(first.getItems()).hasSize(10);
        assertThat(first.getItems().get(0).getId()).isEqualTo(1L);
        assertThat(first.getTotal()).isEqualTo(150L);
        assertThat(overflow.getItems()).hasSize(10);
        assertThat(overflow.getItems().get(0).getId()).isEqualTo(101L);
        assertThat(overflow.getTotal()).isEqualTo(150L);
        verify(publishedBlogIndexCache, times(1)).get(any());
        verify(blogPostMapper, times(2)).selectList(any());
    }

    private Page<BlogPost> posts(int firstId, int count, long total) {
        Page<BlogPost> page = new Page<>(1, count);
        for (int offset = 0; offset < count; offset++) {
            long id = firstId + offset;
            BlogPost post = new BlogPost();
            post.setId(id);
            post.setTitle("Post " + id);
            post.setSlug(String.format("%032x", id));
            post.setSummary("Summary " + id);
            post.setStatus(BlogPostStatus.PUBLISHED.name());
            post.setPublishedAt(LocalDateTime.of(2026, 7, 16, 12, 0).minusMinutes(offset));
            page.add(post);
        }
        page.setTotal(total);
        return page;
    }
}
