package com.qingluo.link.service;

import com.qingluo.link.model.dto.request.CreateBlogPostRequest;
import com.qingluo.link.model.dto.request.SaveBlogContentRequest;
import com.qingluo.link.model.dto.request.UpdateBlogPostRequest;
import com.qingluo.link.model.dto.response.BlogPostAdminDetailDTO;
import com.qingluo.link.model.dto.response.BlogPostAdminListDTO;
import com.qingluo.link.model.dto.response.BlogPostPublicDetailDTO;
import com.qingluo.link.model.dto.response.BlogPostPublicListDTO;
import com.qingluo.link.model.dto.response.PageResult;
import org.springframework.web.multipart.MultipartFile;

public interface BlogPostService {

    PageResult<BlogPostAdminListDTO> listAdmin(int page, int pageSize, String status);

    BlogPostAdminDetailDTO detailAdmin(Long postId);

    BlogPostAdminDetailDTO create(Long operatorId, CreateBlogPostRequest request);

    BlogPostAdminDetailDTO update(Long operatorId, Long postId, UpdateBlogPostRequest request);

    BlogPostAdminDetailDTO importContent(Long operatorId, Long postId, MultipartFile file);

    BlogPostAdminDetailDTO saveContent(Long operatorId, Long postId, SaveBlogContentRequest request);

    BlogPostAdminDetailDTO publish(Long operatorId, Long postId);

    BlogPostAdminDetailDTO unpublish(Long operatorId, Long postId);

    void delete(Long operatorId, Long postId);

    PageResult<BlogPostPublicListDTO> listPublished(int page, int pageSize);

    BlogPostPublicDetailDTO publicDetail(String slug);
}
