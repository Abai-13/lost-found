package com.lostfound.service.impl;

import com.lostfound.common.BusinessException;
import com.lostfound.common.ResultCode;
import com.lostfound.service.FileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 本地磁盘文件存储实现。
 *
 * 存储路径：项目根目录/uploads/
 * 访问 URL：/uploads/yyyyMMdd_uuid.ext
 * 文件名策略：日期_UUID前8位.扩展名 — 防重名、可追溯上传日期
 */
@Slf4j
@Service
public class FileServiceImpl implements FileService {

    /** 上传目录，可通过 application.yml 覆盖：file.upload-dir */
    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Override
    public String upload(MultipartFile file) {
        // 没有文件就不处理（图片是可选的）
        if (file == null || file.isEmpty()) {
            return null;
        }

        // 1. 提取原始文件名并校验格式
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.contains(".")) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "无法识别的文件格式");
        }
        String extension = originalName.substring(originalName.lastIndexOf(".")).toLowerCase();
        if (!extension.equals(".jpg") && !extension.equals(".png")
                && !extension.equals(".jpeg") && !extension.equals(".gif")) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "不支持的图片格式：" + extension + "，仅支持 jpg/png/jpeg/gif");
        }

        // 2. 生成唯一文件名：日期_UUID.扩展名
        //    例：20240810_a1b2c3d4.jpg
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String newFileName = dateStr + "_" + uuid + extension;

        // 3. 确保上传目录存在（首次启动时创建）
        File dir = new File(uploadDir);
        if (!dir.exists()) {
            dir.mkdirs();
            log.info("创建上传目录: {}", dir.getAbsolutePath());
        }

        // 4. 将文件写入磁盘
        try {
            File dest = new File(dir, newFileName);
            file.transferTo(dest);
            log.info("文件上传成功: {} → {}", originalName, dest.getAbsolutePath());
        } catch (IOException e) {
            log.error("文件上传失败: {}", originalName, e);
            throw new RuntimeException("文件上传失败，请重试", e);
        }

        // 5. 返回可访问的 URL 路径（存入数据库）
        return "/uploads/" + newFileName;
    }
}
