package com.lostfound.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ItemCreateRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题最长 100 字")
    private String title;

    @NotBlank(message = "类型不能为空，LOST 或 FOUND")
    private String type;

    @NotBlank(message = "类别不能为空")
    private String category;

    @Size(max = 200, message = "地点最长 200 字")
    private String location;

    @Size(max = 2000, message = "描述最长 2000 字")
    private String description;

    @Size(max = 100, message = "联系方式最长 100 字")
    private String contact;
}
