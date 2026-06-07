package com.langgraph4j.engine.rag;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 文档提取器
 * 支持 TXT、Markdown、PDF、DOC、DOCX、XLSX 格式的文本提取
 */
@Slf4j
public class DocumentExtractor {

    private static final Map<String, String> MIME_TYPES = new HashMap<>();
    static {
        MIME_TYPES.put("txt", "text/plain");
        MIME_TYPES.put("md", "text/markdown");
        MIME_TYPES.put("pdf", "application/pdf");
        MIME_TYPES.put("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        MIME_TYPES.put("doc", "application/msword");
        MIME_TYPES.put("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    /**
     * 提取文档文本内容
     *
     * @param filePath 文件路径
     * @param fileType 文件类型（txt, md, pdf, docx）
     * @return 提取的文本内容
     */
    public String extract(String filePath, String fileType) throws IOException {
        if (fileType == null) {
            fileType = detectFileType(filePath);
        }

        return switch (fileType.toLowerCase()) {
            case "txt", "text" -> extractText(filePath);
            case "md", "markdown" -> extractText(filePath);
            case "pdf" -> extractPdf(filePath);
            case "docx" -> extractDocx(filePath);
            case "doc" -> extractDoc(filePath);
            case "xlsx" -> extractXlsx(filePath);
            default -> extractText(filePath); // 默认按纯文本处理
        };
    }

    /**
     * 提取纯文本文件（TXT/MD）
     */
    private String extractText(String filePath) throws IOException {
        return Files.readString(Path.of(filePath), StandardCharsets.UTF_8);
    }

    /**
     * 提取 PDF 文件内容
     * 需要 Apache PDFBox 依赖
     */
    private String extractPdf(String filePath) throws IOException {
        try {
            // 使用反射调用 PDFBox，避免编译时依赖
            Class<?> loaderClass = Class.forName("org.apache.pdfbox.Loader");
            Object document = loaderClass.getMethod("loadPDF", java.io.File.class)
                    .invoke(null, new java.io.File(filePath));

            Class<?> stripperClass = Class.forName("org.apache.pdfbox.text.PDFTextStripper");
            Object stripper = stripperClass.getDeclaredConstructor().newInstance();
            String text = (String) stripperClass.getMethod("getText", document.getClass())
                    .invoke(stripper, document);

            document.getClass().getMethod("close").invoke(document);
            return text;
        } catch (ClassNotFoundException e) {
            log.warn("PDFBox not available, falling back to raw text extraction");
            return extractText(filePath);
        } catch (Exception e) {
            log.error("Failed to extract PDF content: {}", filePath, e);
            throw new IOException("Failed to extract PDF: " + e.getMessage(), e);
        }
    }

    /**
     * 提取 DOCX 文件内容
     * 需要 Apache POI 依赖
     */
    private String extractDocx(String filePath) throws IOException {
        try {
            Class<?> factoryClass = Class.forName("org.apache.poi.xwpf.extractor.XWPFWordExtractor");
            Class<?> documentClass = Class.forName("org.apache.poi.xwpf.usermodel.XWPFDocument");

            Object document = documentClass.getConstructor(java.io.InputStream.class)
                    .newInstance(Files.newInputStream(Path.of(filePath)));
            Object extractor = factoryClass.getConstructor(documentClass)
                    .newInstance(document);
            String text = (String) extractor.getClass().getMethod("getText").invoke(extractor);

            extractor.getClass().getMethod("close").invoke(extractor);
            return text;
        } catch (ClassNotFoundException e) {
            log.warn("Apache POI not available, falling back to raw text extraction");
            return extractText(filePath);
        } catch (Exception e) {
            log.error("Failed to extract DOCX content: {}", filePath, e);
            throw new IOException("Failed to extract DOCX: " + e.getMessage(), e);
        }
    }

    /**
     * 提取 DOC 文件内容（旧版 Word 二进制格式）
     * 需要 Apache POI Scratchpad 依赖
     */
    private String extractDoc(String filePath) throws IOException {
        try {
            Class<?> extractorClass = Class.forName("org.apache.poi.hwpf.extractor.WordExtractor");
            Object extractor = extractorClass.getConstructor(java.io.InputStream.class)
                    .newInstance(Files.newInputStream(Path.of(filePath)));
            String text = (String) extractorClass.getMethod("getText").invoke(extractor);
            extractor.getClass().getMethod("close").invoke(extractor);
            return text;
        } catch (ClassNotFoundException e) {
            log.warn("Apache POI Scratchpad not available, cannot extract DOC");
            throw new IOException("DOC format not supported: Apache POI Scratchpad not available", e);
        } catch (Exception e) {
            log.error("Failed to extract DOC content: {}", filePath, e);
            throw new IOException("Failed to extract DOC: " + e.getMessage(), e);
        }
    }

    /**
     * 提取 XLSX 文件内容（Excel）
     * 需要 Apache POI OOXML 依赖
     */
    private String extractXlsx(String filePath) throws IOException {
        try {
            Class<?> workbookClass = Class.forName("org.apache.poi.xssf.usermodel.XSSFWorkbook");
            Object workbook = workbookClass.getConstructor(java.io.InputStream.class)
                    .newInstance(Files.newInputStream(Path.of(filePath)));

            int sheetCount = (int) workbookClass.getMethod("getNumberOfSheets").invoke(workbook);
            StringBuilder result = new StringBuilder();

            for (int i = 0; i < sheetCount; i++) {
                Object sheet = workbookClass.getMethod("getSheetAt", int.class).invoke(workbook, i);
                if (sheet == null) continue;

                String sheetName = (String) sheet.getClass().getMethod("getSheetName").invoke(sheet);
                result.append("--- ").append(sheetName).append(" ---\n");

                @SuppressWarnings("unchecked")
                java.util.Iterator<Object> rowIter = (java.util.Iterator<Object>) sheet.getClass()
                        .getMethod("iterator").invoke(sheet);
                while (rowIter.hasNext()) {
                    Object row = rowIter.next();
                    @SuppressWarnings("unchecked")
                    java.util.Iterator<Object> cellIter = (java.util.Iterator<Object>) row.getClass()
                            .getMethod("cellIterator").invoke(row);
                    StringBuilder line = new StringBuilder();
                    while (cellIter.hasNext()) {
                        Object cell = cellIter.next();
                        // 尝试获取字符串值，失败则获取数值
                        String cellValue;
                        try {
                            cellValue = (String) cell.getClass().getMethod("getStringCellValue").invoke(cell);
                        } catch (Exception ignored) {
                            double numValue = (double) cell.getClass().getMethod("getNumericCellValue").invoke(cell);
                            cellValue = String.valueOf(numValue);
                        }
                        if (line.length() > 0) line.append("\t");
                        line.append(cellValue != null ? cellValue : "");
                    }
                    if (!line.isEmpty()) {
                        result.append(line.toString()).append("\n");
                    }
                }
                result.append("\n");
            }

            workbook.getClass().getMethod("close").invoke(workbook);
            return result.toString().trim();
        } catch (ClassNotFoundException e) {
            log.warn("Apache POI OOXML not available, cannot extract XLSX");
            throw new IOException("XLSX format not supported: Apache POI OOXML not available", e);
        } catch (Exception e) {
            log.error("Failed to extract XLSX content: {}", filePath, e);
            throw new IOException("Failed to extract XLSX: " + e.getMessage(), e);
        }
    }

    /**
     * 根据文件扩展名检测文件类型
     */
    public String detectFileType(String filePath) {
        String fileName = Path.of(filePath).getFileName().toString().toLowerCase();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex == -1) {
            return "txt";
        }
        return fileName.substring(dotIndex + 1);
    }

    /**
     * 获取文件扩展名
     */
    public static String getFileExtension(String fileName) {
        if (fileName == null) return "";
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex == -1 ? "" : fileName.substring(dotIndex + 1).toLowerCase();
    }

    /**
     * 获取 MIME 类型
     */
    public static String getMimeType(String fileType) {
        return MIME_TYPES.getOrDefault(fileType.toLowerCase(), "application/octet-stream");
    }
}