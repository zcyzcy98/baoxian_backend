package com.insurance.agent.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class FileParseService {

    private static final Logger log = LoggerFactory.getLogger(FileParseService.class);

    public String extractText(MultipartFile file) {
        String filename = file.getOriginalFilename();
        String contentType = file.getContentType();

        if (contentType != null) {
            if ("application/pdf".equals(contentType)) {
                return extractPdf(file);
            }
            if ("application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(contentType)
                    || "application/msword".equals(contentType)) {
                return extractDocx(file);
            }
        }

        if (filename != null) {
            String lower = filename.toLowerCase();
            if (lower.endsWith(".pdf")) {
                return extractPdf(file);
            }
            if (lower.endsWith(".docx")) {
                return extractDocx(file);
            }
        }

        throw new IllegalArgumentException("不支持的文件格式，仅支持 PDF 和 DOCX");
    }

    private String extractPdf(MultipartFile file) {
        try (var in = file.getInputStream();
             PDDocument doc = Loader.loadPDF(in.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            if (text == null || text.isBlank()) {
                throw new RuntimeException("PDF 文件内容为空或无法提取文字");
            }
            log.info("[FileParse] PDF extracted: {} chars", text.length());
            return text;
        } catch (IOException e) {
            throw new RuntimeException("读取 PDF 文件失败: " + e.getMessage(), e);
        }
    }

    private String extractDocx(MultipartFile file) {
        try (var in = file.getInputStream();
             XWPFDocument doc = new XWPFDocument(in);
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            String text = extractor.getText();
            if (text == null || text.isBlank()) {
                throw new RuntimeException("DOCX 文件内容为空或无法提取文字");
            }
            log.info("[FileParse] DOCX extracted: {} chars", text.length());
            return text;
        } catch (IOException e) {
            throw new RuntimeException("读取 DOCX 文件失败: " + e.getMessage(), e);
        }
    }
}
