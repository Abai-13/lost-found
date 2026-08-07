package com.lostfound.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("item")
public class Item {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    /** LOST: 寻物启事, FOUND: 失物招领 */
    private String type;

    /** 类别: 电子产品/证件/衣物/书籍/其他 */
    private String category;

    private String location;

    private String description;

    private String imageUrl;

    private String contact;

    /** UNCLAIMED: 未认领, CLAIMED: 已认领 */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
