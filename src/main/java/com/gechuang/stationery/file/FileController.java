package com.gechuang.stationery.file;

import com.gechuang.stationery.common.BusinessException;
import com.gechuang.stationery.common.ErrorCode;
import com.gechuang.stationery.common.RestResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private static final long MAX_IMAGE_SIZE = 2L * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of(
            MediaType.IMAGE_JPEG_VALUE,
            MediaType.IMAGE_PNG_VALUE,
            "image/webp"
    );

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RestResponse<Map<String, Object>> uploadImage(@RequestPart("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_422, "上传文件不能为空");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_422, "图片不能超过 2MB");
        }
        String contentType = file.getContentType();
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.VALIDATION_422, "仅支持 JPG、PNG、WebP 图片");
        }
        Path directory = Path.of("uploads", "images").toAbsolutePath().normalize();
        Files.createDirectories(directory);
        String extension = extensionOf(file.getOriginalFilename(), contentType);
        String storedName = UUID.randomUUID().toString().replace("-", "") + extension;
        Path target = directory.resolve(storedName);
        Files.copy(file.getInputStream(), target);
        return RestResponse.success(Map.of(
                "url", "/uploads/images/" + storedName,
                "fileName", storedName,
                "size", file.getSize(),
                "contentType", contentType
        ));
    }

    private String extensionOf(String originalFilename, String contentType) {
        if (StringUtils.hasText(originalFilename) && originalFilename.contains(".")) {
            String extension = originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase(Locale.ROOT);
            if (Set.of(".jpg", ".jpeg", ".png", ".webp").contains(extension)) {
                return extension;
            }
        }
        if (MediaType.IMAGE_PNG_VALUE.equals(contentType)) {
            return ".png";
        }
        if ("image/webp".equals(contentType)) {
            return ".webp";
        }
        return ".jpg";
    }
}
