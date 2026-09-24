package com.wuyuxiao.aippt.service;

import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;
import java.util.Set;

@Service
public class DocumentTextService {
    private static final long MAX_TEXT = 300_000;
    private static final Set<String> ALLOWED = Set.of("pdf","doc","docx","md","markdown","txt");
    private final Tika tika = new Tika();
    public String extract(MultipartFile file) {
        if (file == null || file.isEmpty()) return "";
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!ALLOWED.contains(ext)) throw new IllegalArgumentException("仅支持 PDF、Word、Markdown 和 TXT 文件");
        try (InputStream in = file.getInputStream()) {
            String value = tika.parseToString(in).strip();
            return value.length() > MAX_TEXT ? value.substring(0, (int) MAX_TEXT) : value;
        } catch (Exception e) { throw new IllegalArgumentException("文档解析失败：" + e.getMessage(), e); }
    }
}

