package com.lostfound.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储服务。
 * 当前实现：本地磁盘存储，返回可访问的 URL 路径。
 * 面试演进方向：阿里云 OSS / MinIO / 七牛云（替换实现类即可）。
 */
public interface FileService {

    /**
     * 上传文件到本地磁盘。
     *
     * @param file 上传的文件（可为 null）
     * @return 可访问的 URL 路径（如 /uploads/20240810_abc123.jpg），file 为 null 时返回 null
     */
    String upload(MultipartFile file);
}
