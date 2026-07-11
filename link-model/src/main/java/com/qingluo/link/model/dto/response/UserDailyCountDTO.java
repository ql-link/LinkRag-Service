package com.qingluo.link.model.dto.response;

import lombok.Data;

/** 数据访问层日度计数投影。 */
@Data
public class UserDailyCountDTO {
    private String date;
    private long count;
}
