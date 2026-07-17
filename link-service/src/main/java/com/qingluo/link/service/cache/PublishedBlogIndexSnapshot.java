package com.qingluo.link.service.cache;

import com.qingluo.link.model.dto.response.BlogPostPublicListDTO;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublishedBlogIndexSnapshot {

    private List<BlogPostPublicListDTO> items;
    private long total;
    private int capacity;
}
