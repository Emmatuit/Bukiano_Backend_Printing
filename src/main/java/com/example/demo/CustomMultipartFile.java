package com.example.demo;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.springframework.web.multipart.MultipartFile;

public class CustomMultipartFile implements MultipartFile {

    private final String name;
    private final String originalFilename;
    private final String contentType;
    private final byte[] content;

    public CustomMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
        this.name = name;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.content = content;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getOriginalFilename() {
        return originalFilename;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean isEmpty() {
        return content.length == 0;
    }

    @Override
    public long getSize() {
        return content.length;
    }

    @Override
    public byte[] getBytes() {
        return content;
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(content);
    }

    @Override
    public void transferTo(File dest) throws IOException, IllegalStateException {
        try (OutputStream out = new FileOutputStream(dest)) {
            out.write(content);
        }
    }
}
