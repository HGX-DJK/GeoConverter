package com.hgx.controller;

import com.hgx.model.GeometryType;
import com.hgx.service.ConverterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

@Controller
public class ConverterController {

    @Autowired
    private ConverterService converterService;

    private final String uploadDir = "uploads";
    private final String outputDir = "outputs";
    private final String tempDir = "temp";

    public ConverterController() {
        File uploadDirectory = new File(uploadDir);
        if (!uploadDirectory.exists()) {
            uploadDirectory.mkdirs();
        }
        File outputDirectory = new File(outputDir);
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs();
        }
        File tempDirectory = new File(tempDir);
        if (!tempDirectory.exists()) {
            tempDirectory.mkdirs();
        }
    }

    @GetMapping("/")
    public String index() {
        return "index.html";
    }

    @PostMapping("/convert-to-shapefile")
    public ResponseEntity<Resource> convertToShapefile(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "geometryColumn", required = false) String geometryColumn,
            @RequestParam(value = "geometryType", required = false) String geometryTypeStr,
            @RequestParam(value = "encoding", defaultValue = "UTF-8") String encoding) throws Exception {

        if (file.isEmpty()) {
            throw new IOException("Uploaded file is empty");
        }

        Path uploadPath = Paths.get(uploadDir, file.getOriginalFilename());
        Files.write(uploadPath, file.getBytes());
        File inputFile = uploadPath.toFile();

        GeometryType geometryType = null;
        if (geometryTypeStr != null && !geometryTypeStr.isEmpty()) {
            geometryType = GeometryType.valueOf(geometryTypeStr.toUpperCase());
        }

        File outputDirectory = new File(outputDir, getBaseName(file.getOriginalFilename()) + "_" + System.currentTimeMillis());
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs();
        }

        File outputShp = converterService.convertToShapefile(inputFile, outputDirectory, geometryColumn, geometryType, encoding);

        String zipFileName = getBaseName(file.getOriginalFilename()) + ".zip";
        File zipFile = createZipFile(outputDirectory, zipFileName);

        inputFile.delete();
        deleteDirectory(outputDirectory);

        if (zipFile == null || !zipFile.exists() || zipFile.length() == 0) {
            throw new IOException("Failed to create ZIP file");
        }

        FileSystemResource resource = new FileSystemResource(zipFile);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + zipFile.getName());
        headers.add(HttpHeaders.CONTENT_TYPE, "application/zip");

        return ResponseEntity.ok()
                .headers(headers)
                .contentLength(zipFile.length())
                .body(resource);
    }

    @PostMapping("/convert-from-shapefile")
    public ResponseEntity<Resource> convertFromShapefile(
            @RequestPart("file") List<MultipartFile> files,
            @RequestParam("outputFormat") String outputFormat,
            @RequestParam(value = "encoding", defaultValue = "UTF-8") String encoding) throws Exception {

        if (files == null || files.isEmpty()) {
            throw new IOException("No file uploaded");
        }

        File inputFile = null;
        String originalFilename = files.get(0).getOriginalFilename();
        boolean isZip = originalFilename.toLowerCase().endsWith(".zip");

        if (isZip) {
            // 解压 ZIP 文件到临时目录
            File zipFile = new File(tempDir, originalFilename);
            Files.write(zipFile.toPath(), files.get(0).getBytes());

            File extractDir = new File(tempDir, "shp_" + System.currentTimeMillis());
            extractDir.mkdirs();

            unzipFile(zipFile, extractDir);

            // 查找 .shp 文件
            File shpFile = findShpFile(extractDir);
            if (shpFile == null) {
                throw new IOException("No .shp file found in ZIP archive");
            }

            inputFile = shpFile;
        } else {
            // 保存所有上传的 shp 相关文件到同一目录
            for (MultipartFile mf : files) {
                String name = mf.getOriginalFilename();
                Path uploadPath = Paths.get(uploadDir, name);
                Files.write(uploadPath, mf.getBytes());
                if (name.toLowerCase().endsWith(".shp")) {
                    inputFile = uploadPath.toFile();
                }
            }
        }

        String outputFileName = getBaseName(originalFilename) + "." + outputFormat;
        File outputFile = new File(outputDir, outputFileName);

        File convertedFile = converterService.convertFromShapefile(inputFile, outputFile, encoding);

        // 清理临时文件
        if (isZip) {
            inputFile.delete();
            File parentDir = inputFile.getParentFile();
            if (parentDir != null && parentDir.getName().startsWith("shp_")) {
                deleteDirectory(parentDir);
            }
        } else {
            inputFile.delete();
            // 清理同一目录下的其他 shp 相关文件
            String baseName = getBaseName(inputFile.getName());
            for (String ext : new String[]{".shx", ".dbf", ".prj", ".cpg"}) {
                File sidecar = new File(uploadDir, baseName + ext);
                sidecar.delete();
            }
        }

        FileSystemResource resource = new FileSystemResource(convertedFile);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + convertedFile.getName());

        String contentType;
        switch (outputFormat.toLowerCase()) {
            case "csv":
                contentType = "text/csv";
                break;
            case "xls":
                contentType = "application/vnd.ms-excel";
                break;
            case "xlsx":
                contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                break;
            default:
                contentType = "application/octet-stream";
        }

        headers.add(HttpHeaders.CONTENT_TYPE, contentType);

        return ResponseEntity.ok()
                .headers(headers)
                .contentLength(convertedFile.length())
                .body(resource);
    }

    private File findShpFile(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().toLowerCase().endsWith(".shp")) {
                    return file;
                }
            }
        }
        return null;
    }

    private void unzipFile(File zipFile, File destDir) throws IOException {
        try (ZipFile zip = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                File newFile = new File(destDir, entry.getName());

                // 安全检查：防止 zip slip 攻击
                String destDirPath = destDir.getCanonicalPath();
                String newFilePath = newFile.getCanonicalPath();
                if (!newFilePath.startsWith(destDirPath)) {
                    continue;
                }

                if (entry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    newFile.getParentFile().mkdirs();
                    try (InputStream is = zip.getInputStream(entry);
                         FileOutputStream fos = new FileOutputStream(newFile)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = is.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
            }
        }
    }

    private String getBaseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private File createZipFile(File directory, String zipFileName) throws IOException {
        File zipFile = new File(outputDir, zipFileName);

        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        addToZip(file, zos);
                    }
                }
            }
        }

        return zipFile;
    }

    private void addToZip(File file, ZipOutputStream zos) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            ZipEntry entry = new ZipEntry(file.getName());
            zos.putNextEntry(entry);

            byte[] buffer = new byte[8192];
            int length;
            while ((length = fis.read(buffer)) >= 0) {
                zos.write(buffer, 0, length);
            }

            zos.closeEntry();
        }
    }

    private void deleteDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        directory.delete();
    }

    private void copySidecarFileIfExists(String uploadDir, String baseName, String ext) throws IOException {
        File sidecarFile = new File(uploadDir, baseName + ext);
        if (sidecarFile.exists()) {
            // sidecar 文件已存在于 uploadDir，无需复制
        }
    }

}
