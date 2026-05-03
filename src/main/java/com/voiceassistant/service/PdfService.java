package com.voiceassistant.service;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.events.Event;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.font.PdfFontFactory.EmbeddingStrategy;
import org.springframework.core.io.ClassPathResource;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
/**
 * Service for generating modern, comprehensive PDF interview reports.
 * Includes overall score, detailed answer analysis, weak questions,
 * learning resources, anti-cheat report, and personalized recommendations.
 */
@Slf4j
@Service
public class PdfService {
    private static final DeviceRgb PRIMARY_COLOR    = new DeviceRgb(99, 102, 241);
    private static final DeviceRgb PRIMARY_DARK     = new DeviceRgb(79, 70, 229);
    private static final DeviceRgb SUCCESS_COLOR    = new DeviceRgb(34, 197, 94);
    private static final DeviceRgb WARNING_COLOR    = new DeviceRgb(234, 179, 8);
    private static final DeviceRgb DANGER_COLOR     = new DeviceRgb(239, 68, 68);
    private static final DeviceRgb INFO_COLOR       = new DeviceRgb(14, 165, 233);
    private static final DeviceRgb TEXT_PRIMARY     = new DeviceRgb(17, 24, 39);
    private static final DeviceRgb TEXT_SECONDARY   = new DeviceRgb(75, 85, 99);
    private static final DeviceRgb TEXT_MUTED       = new DeviceRgb(156, 163, 175);
    private static final DeviceRgb BG_LIGHT         = new DeviceRgb(249, 250, 251);
    private static final DeviceRgb BG_CARD          = new DeviceRgb(243, 244, 246);
    private static final DeviceRgb BORDER_COLOR     = new DeviceRgb(229, 231, 235);
    private static final int    DEFAULT_SCORE        = 75;
    private static final int    HIGH_SCORE_THRESHOLD = 70;
    private static final int    MID_SCORE_THRESHOLD  = 50;
    private static final float  PAGE_MARGIN_TOP      = 50f;
    private static final float  PAGE_MARGIN_BOTTOM   = 50f;
    private static final float  PAGE_MARGIN_SIDES    = 45f;
    private static final String DATE_PATTERN = "dd MMMM yyyy, HH:mm";

    /**
     * Generates a comprehensive PDF interview report.
     *
     * @param feedback map containing all feedback data
     * @param settings map containing interview metadata
     * @return byte array of the generated PDF document
     */
    public byte[] generateInterviewReport(Map<String, Object> feedback, Map<String, Object> settings) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (PdfDocument pdf = new PdfDocument(new PdfWriter(outputStream))) {
                pdf.setDefaultPageSize(PageSize.A4);
                pdf.addEventHandler(PdfDocumentEvent.END_PAGE, new FooterEventHandler());
                try (Document document = new Document(pdf)) {
                    document.setMargins(PAGE_MARGIN_TOP, PAGE_MARGIN_SIDES, PAGE_MARGIN_BOTTOM, PAGE_MARGIN_SIDES);
                    PdfFont font     = getRegularFont();
                    PdfFont boldFont = getBoldFont();
                    PdfFont italicFont = getItalicFont();
                    addCoverPage(document, boldFont, font, settings, feedback);
                    document.add(new AreaBreak());
                    addSectionTitle(document, boldFont, "Executive Summary", "01");
                    addExecutiveSummary(document, boldFont, font, italicFont, feedback);
                    if (hasCategories(feedback)) {
                        addSectionTitle(document, boldFont, "Performance by Category", "02");
                        addCategoriesSection(document, boldFont, font, feedback);
                    }
                    addSectionTitle(document, boldFont, "Strengths & Areas to Improve", "03");
                    addStrengthsAndImprovements(document, boldFont, font, feedback);
                    if (hasDetailedAnswers(feedback)) {
                        document.add(new AreaBreak());
                        addSectionTitle(document, boldFont, "Question-by-Question Analysis", "04");
                        addDetailedAnswers(document, boldFont, font, italicFont, feedback);
                    }
                    if (hasTopicsNotCovered(feedback)) {
                        addSectionTitle(document, boldFont, "Topics to Study", "05");
                        addTopicsNotCovered(document, boldFont, font, feedback);
                    }
                    if (hasResources(feedback)) {
                        addSectionTitle(document, boldFont, "Recommended Learning Resources", "06");
                        addResources(document, boldFont, font, italicFont, feedback);
                    }
                    if (hasAntiCheatReport(feedback)) {
                        addSectionTitle(document, boldFont, "Integrity Report", "07");
                        addAntiCheatReport(document, boldFont, font, feedback);
                    }
                    addSectionTitle(document, boldFont, "Next Steps", "08");
                    addNextSteps(document, boldFont, font, italicFont, feedback);
                }
            }
            log.info("PDF report generated successfully ({} bytes)", outputStream.size());
            return outputStream.toByteArray();
        } catch (Exception e) {
            log.error("Error generating PDF report: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate PDF report", e);
        }
    }
    private void addCoverPage(Document document, PdfFont boldFont, PdfFont font,
                              Map<String, Object> settings, Map<String, Object> feedback) {
        document.add(new Paragraph("\n").setFontSize(6));
        document.add(new Paragraph("AI INTERVIEW ASSISTANT")
                .setFont(boldFont)
                .setFontSize(11)
                .setFontColor(PRIMARY_COLOR)
                .setCharacterSpacing(3)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(40));
        document.add(new Paragraph("Technical Interview")
                .setFont(font)
                .setFontSize(32)
                .setFontColor(TEXT_PRIMARY)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(0));
        document.add(new Paragraph("Performance Report")
                .setFont(boldFont)
                .setFontSize(32)
                .setFontColor(PRIMARY_COLOR)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(35));
        int score = extractScore(feedback);
        addScoreBadge(document, boldFont, font, score);
        String verdict = (String) feedback.getOrDefault("verdict", "Assessment completed");
        document.add(new Paragraph(verdict)
                .setFont(font)
                .setFontSize(12)
                .setFontColor(TEXT_SECONDARY)
                .setTextAlignment(TextAlignment.CENTER)
                .setItalic()
                .setMarginTop(15)
                .setMarginBottom(25)
                .setPaddingLeft(40)
                .setPaddingRight(40));
        addMetadataCard(document, boldFont, font, settings);
    }
    private void addScoreBadge(Document document, PdfFont boldFont, PdfFont font, int score) {
        DeviceRgb scoreColor = resolveScoreColor(score);
        String scoreLabel = getScoreLabel(score);
        Table scoreTable = new Table(1)
                .setWidth(UnitValue.createPointValue(160))
                .setHorizontalAlignment(HorizontalAlignment.CENTER);
        Cell scoreCell = new Cell()
                .setBorder(new SolidBorder(scoreColor, 3))
                .setBackgroundColor(BG_LIGHT)
                .setPadding(20)
                .setTextAlignment(TextAlignment.CENTER);
        scoreCell.add(new Paragraph("OVERALL SCORE")
                .setFont(boldFont)
                .setFontSize(9)
                .setFontColor(TEXT_MUTED)
                .setCharacterSpacing(1.5f)
                .setMarginBottom(6));
        scoreCell.add(new Paragraph(score + "%")
                .setFont(boldFont)
                .setFontSize(42)
                .setFontColor(scoreColor)
                .setMarginBottom(4));
        scoreCell.add(new Paragraph(scoreLabel)
                .setFont(boldFont)
                .setFontSize(11)
                .setFontColor(scoreColor));
        scoreTable.addCell(scoreCell);
        document.add(scoreTable);
    }
    private void addMetadataCard(Document document, PdfFont boldFont, PdfFont font,
                                 Map<String, Object> settings) {
        Table metaTable = new Table(UnitValue.createPercentArray(2))
                .useAllAvailableWidth()
                .setMarginTop(25);
        String date = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern(DATE_PATTERN, java.util.Locale.ENGLISH));
        String language = (String) settings.getOrDefault("programmingLanguage", "N/A");
        String position = (String) settings.getOrDefault("position", "N/A");
        String interviewLang = (String) settings.getOrDefault("interviewLanguage", "N/A");
        metaTable.addCell(createMetaCardCell("DATE", date, boldFont, font));
        metaTable.addCell(createMetaCardCell("POSITION", position, boldFont, font));
        metaTable.addCell(createMetaCardCell("LANGUAGE", language, boldFont, font));
        metaTable.addCell(createMetaCardCell("INTERVIEW LANGUAGE", interviewLang, boldFont, font));
        document.add(metaTable);
    }
    private Cell createMetaCardCell(String label, String value, PdfFont boldFont, PdfFont font) {
        Cell cell = new Cell()
                .setBorder(Border.NO_BORDER)
                .setPadding(12)
                .setBackgroundColor(BG_LIGHT)
                .setMargin(4);
        cell.add(new Paragraph(label)
                .setFont(boldFont)
                .setFontSize(8)
                .setFontColor(TEXT_MUTED)
                .setCharacterSpacing(1)
                .setMarginBottom(3));
        cell.add(new Paragraph(value)
                .setFont(boldFont)
                .setFontSize(12)
                .setFontColor(TEXT_PRIMARY));
        return cell;
    }
    private void addSectionTitle(Document document, PdfFont boldFont, String title, String number) {
        Table titleTable = new Table(UnitValue.createPercentArray(new float[]{8, 92}))
                .useAllAvailableWidth()
                .setMarginTop(20)
                .setMarginBottom(20);
        Cell numberCell = new Cell()
                .setBorder(Border.NO_BORDER)
                .setBorderRight(new SolidBorder(PRIMARY_COLOR, 3))
                .setPaddingRight(12)
                .setPaddingTop(2)
                .setPaddingBottom(2)
                .add(new Paragraph(number)
                        .setFont(boldFont)
                        .setFontSize(20)
                        .setFontColor(PRIMARY_COLOR));
        Cell titleCell = new Cell()
                .setBorder(Border.NO_BORDER)
                .setPaddingLeft(15)
                .setVerticalAlignment(VerticalAlignment.MIDDLE)
                .add(new Paragraph(title)
                        .setFont(boldFont)
                        .setFontSize(20)
                        .setFontColor(TEXT_PRIMARY));
        titleTable.addCell(numberCell);
        titleTable.addCell(titleCell);
        document.add(titleTable);
    }
    private void addExecutiveSummary(Document document, PdfFont boldFont, PdfFont font,
                                     PdfFont italicFont, Map<String, Object> feedback) {
        int score = extractScore(feedback);
        String verdict = (String) feedback.getOrDefault("verdict", "Assessment completed");
        Table summaryTable = new Table(1).useAllAvailableWidth();
        Cell summaryCell = new Cell()
                .setBorder(new SolidBorder(BORDER_COLOR, 1))
                .setPadding(20)
                .setBackgroundColor(BG_LIGHT);
        summaryCell.add(new Paragraph("Overall Assessment")
                .setFont(boldFont)
                .setFontSize(11)
                .setFontColor(PRIMARY_COLOR)
                .setMarginBottom(8));
        summaryCell.add(new Paragraph(verdict)
                .setFont(font)
                .setFontSize(12)
                .setFontColor(TEXT_PRIMARY)
                .setMarginBottom(0));
        summaryTable.addCell(summaryCell);
        document.add(summaryTable);
        document.add(new Paragraph("\n").setFontSize(5));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> categories = (List<Map<String, Object>>) feedback.get("categories");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> answers = (List<Map<String, Object>>) feedback.get("detailedAnswers");
        @SuppressWarnings("unchecked")
        List<String> strengths = (List<String>) feedback.get("strengths");
        @SuppressWarnings("unchecked")
        List<String> improvements = (List<String>) feedback.get("improvements");
        Table metricsTable = new Table(UnitValue.createPercentArray(4)).useAllAvailableWidth();
        metricsTable.addCell(createMetricCell("Score", score + "%", resolveScoreColor(score), boldFont, font));
        metricsTable.addCell(createMetricCell("Topics",
                String.valueOf(categories != null ? categories.size() : 0), INFO_COLOR, boldFont, font));
        metricsTable.addCell(createMetricCell("Questions",
                String.valueOf(answers != null ? answers.size() : 0), PRIMARY_COLOR, boldFont, font));
        metricsTable.addCell(createMetricCell("Strengths",
                String.valueOf(strengths != null ? strengths.size() : 0), SUCCESS_COLOR, boldFont, font));
        document.add(metricsTable);
    }
    private Cell createMetricCell(String label, String value, DeviceRgb color, PdfFont boldFont, PdfFont font) {
        Cell cell = new Cell()
                .setBorder(Border.NO_BORDER)
                .setPadding(15)
                .setMargin(3)
                .setBackgroundColor(BG_LIGHT)
                .setBorderTop(new SolidBorder(color, 3))
                .setTextAlignment(TextAlignment.CENTER);
        cell.add(new Paragraph(value)
                .setFont(boldFont)
                .setFontSize(22)
                .setFontColor(color)
                .setMarginBottom(2));
        cell.add(new Paragraph(label)
                .setFont(font)
                .setFontSize(9)
                .setFontColor(TEXT_MUTED));
        return cell;
    }
    private void addCategoriesSection(Document document, PdfFont boldFont, PdfFont font,
                                      Map<String, Object> feedback) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> categories = (List<Map<String, Object>>) feedback.get("categories");
        if (categories == null || categories.isEmpty()) return;
        for (Map<String, Object> category : categories) {
            if (!(category instanceof Map<?, ?>)) continue;
            String name = (String) category.getOrDefault("name", "Unknown");
            int catScore = category.get("score") instanceof Number
                    ? ((Number) category.get("score")).intValue() : 0;
            DeviceRgb scoreColor = resolveScoreColor(catScore);
            Table catTable = new Table(UnitValue.createPercentArray(new float[]{60, 30, 10}))
                    .useAllAvailableWidth()
                    .setMarginBottom(12);
            Cell nameCell = new Cell()
                    .setBorder(Border.NO_BORDER)
                    .setPaddingTop(6)
                    .setPaddingBottom(6)
                    .add(new Paragraph(name)
                            .setFont(boldFont)
                            .setFontSize(11)
                            .setFontColor(TEXT_PRIMARY));
            Cell progressCell = new Cell()
                    .setBorder(Border.NO_BORDER)
                    .setPaddingTop(10)
                    .setPaddingBottom(6);
            progressCell.add(createProgressBar(catScore, scoreColor));
            Cell scoreCell = new Cell()
                    .setBorder(Border.NO_BORDER)
                    .setPaddingTop(6)
                    .setPaddingBottom(6)
                    .setTextAlignment(TextAlignment.RIGHT)
                    .add(new Paragraph(catScore + "%")
                            .setFont(boldFont)
                            .setFontSize(12)
                            .setFontColor(scoreColor));
            catTable.addCell(nameCell);
            catTable.addCell(progressCell);
            catTable.addCell(scoreCell);
            document.add(catTable);
        }
    }
    private Table createProgressBar(int score, DeviceRgb color) {
        Table bar = new Table(UnitValue.createPercentArray(new float[]{Math.max(score, 1), Math.max(100 - score, 1)}))
                .useAllAvailableWidth()
                .setHeight(8);
        Cell filled = new Cell()
                .setBorder(Border.NO_BORDER)
                .setBackgroundColor(color)
                .setPadding(0);
        Cell empty = new Cell()
                .setBorder(Border.NO_BORDER)
                .setBackgroundColor(BG_CARD)
                .setPadding(0);
        bar.addCell(filled);
        bar.addCell(empty);
        return bar;
    }
    private void addStrengthsAndImprovements(Document document, PdfFont boldFont, PdfFont font,
                                             Map<String, Object> feedback) {
        @SuppressWarnings("unchecked")
        List<String> strengths = (List<String>) feedback.get("strengths");
        @SuppressWarnings("unchecked")
        List<String> improvements = (List<String>) feedback.get("improvements");
        Table grid = new Table(UnitValue.createPercentArray(2)).useAllAvailableWidth();
        Cell strengthsCell = new Cell()
                .setBorder(Border.NO_BORDER)
                .setPadding(15)
                .setMargin(5)
                .setBackgroundColor(new DeviceRgb(240, 253, 244))
                .setBorderLeft(new SolidBorder(SUCCESS_COLOR, 4));
        strengthsCell.add(new Paragraph("STRENGTHS")
                .setFont(boldFont)
                .setFontSize(10)
                .setFontColor(SUCCESS_COLOR)
                .setCharacterSpacing(1)
                .setMarginBottom(10));
        if (strengths != null && !strengths.isEmpty()) {
            for (String item : strengths) {
                if (item != null && !item.isBlank()) {
                    strengthsCell.add(createBulletItem(item, SUCCESS_COLOR, font));
                }
            }
        } else {
            strengthsCell.add(new Paragraph("No specific strengths identified.")
                    .setFont(font)
                    .setFontSize(10)
                    .setFontColor(TEXT_MUTED)
                    .setItalic());
        }
        Cell improvementsCell = new Cell()
                .setBorder(Border.NO_BORDER)
                .setPadding(15)
                .setMargin(5)
                .setBackgroundColor(new DeviceRgb(254, 252, 232))
                .setBorderLeft(new SolidBorder(WARNING_COLOR, 4));
        improvementsCell.add(new Paragraph("AREAS TO IMPROVE")
                .setFont(boldFont)
                .setFontSize(10)
                .setFontColor(new DeviceRgb(161, 98, 7))
                .setCharacterSpacing(1)
                .setMarginBottom(10));
        if (improvements != null && !improvements.isEmpty()) {
            for (String item : improvements) {
                if (item != null && !item.isBlank()) {
                    improvementsCell.add(createBulletItem(item, WARNING_COLOR, font));
                }
            }
        } else {
            improvementsCell.add(new Paragraph("No specific improvements suggested.")
                    .setFont(font)
                    .setFontSize(10)
                    .setFontColor(TEXT_MUTED)
                    .setItalic());
        }
        grid.addCell(strengthsCell);
        grid.addCell(improvementsCell);
        document.add(grid);
    }
    private Paragraph createBulletItem(String text, DeviceRgb bulletColor, PdfFont font) {
        return new Paragraph()
                .add(new Text("• ").setFont(font).setFontSize(11).setFontColor(bulletColor).setBold())
                .add(new Text(text).setFont(font).setFontSize(10).setFontColor(TEXT_PRIMARY))
                .setMarginBottom(6)
                .setMultipliedLeading(1.4f);
    }
    private void addDetailedAnswers(Document document, PdfFont boldFont, PdfFont font,
                                    PdfFont italicFont, Map<String, Object> feedback) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> answers = (List<Map<String, Object>>) feedback.get("detailedAnswers");
        if (answers == null || answers.isEmpty()) return;
        for (int i = 0; i < answers.size(); i++) {
            Map<String, Object> answer = answers.get(i);
            int qNum = answer.get("questionNumber") instanceof Number
                    ? ((Number) answer.get("questionNumber")).intValue() : (i + 1);
            String question = (String) answer.getOrDefault("question", "");
            String candidateAnswer = (String) answer.getOrDefault("candidateAnswer", "");
            String feedbackText = (String) answer.getOrDefault("feedback", "");
            String topic = (String) answer.getOrDefault("topic", "");
            int accuracy = answer.get("accuracy") instanceof Number
                    ? ((Number) answer.get("accuracy")).intValue() : 0;
            DeviceRgb accColor = resolveScoreColor(accuracy);
            Table qTable = new Table(1).useAllAvailableWidth().setMarginBottom(15);
            Cell qCell = new Cell()
                    .setBorder(new SolidBorder(BORDER_COLOR, 1))
                    .setBorderLeft(new SolidBorder(accColor, 4))
                    .setPadding(15);
            Table qHeader = new Table(UnitValue.createPercentArray(new float[]{60, 25, 15}))
                    .useAllAvailableWidth()
                    .setMarginBottom(10);
            qHeader.addCell(new Cell()
                    .setBorder(Border.NO_BORDER)
                    .add(new Paragraph("Question " + qNum)
                            .setFont(boldFont)
                            .setFontSize(13)
                            .setFontColor(PRIMARY_COLOR)));
            qHeader.addCell(new Cell()
                    .setBorder(Border.NO_BORDER)
                    .setTextAlignment(TextAlignment.CENTER)
                    .add(topic.isBlank() ? new Paragraph("") :
                            new Paragraph(topic)
                            .setFont(font)
                            .setFontSize(9)
                            .setFontColor(TEXT_MUTED)
                            .setItalic()));
            qHeader.addCell(new Cell()
                    .setBorder(Border.NO_BORDER)
                    .setTextAlignment(TextAlignment.RIGHT)
                    .add(new Paragraph(accuracy + "%")
                            .setFont(boldFont)
                            .setFontSize(13)
                            .setFontColor(accColor)));
            qCell.add(qHeader);
            qCell.add(new Paragraph(question)
                    .setFont(font)
                    .setFontSize(11)
                    .setFontColor(TEXT_PRIMARY)
                    .setMarginBottom(12)
                    .setMultipliedLeading(1.4f));
            qCell.add(new Paragraph("YOUR ANSWER")
                    .setFont(boldFont)
                    .setFontSize(8)
                    .setFontColor(TEXT_MUTED)
                    .setCharacterSpacing(1)
                    .setMarginBottom(4));
            String displayAnswer = (candidateAnswer == null || candidateAnswer.isBlank()) ? "(no answer provided)" : candidateAnswer;
            qCell.add(new Paragraph(displayAnswer)
                    .setFont((candidateAnswer == null || candidateAnswer.isBlank()) ? italicFont : font)
                    .setFontSize(10)
                    .setFontColor((candidateAnswer == null || candidateAnswer.isBlank()) ? TEXT_MUTED : TEXT_SECONDARY)
                    .setMarginBottom(12)
                    .setMultipliedLeading(1.4f)
                    .setBackgroundColor(BG_LIGHT)
                    .setPadding(8));
            if (!feedbackText.isBlank()) {
                qCell.add(new Paragraph("FEEDBACK")
                        .setFont(boldFont)
                        .setFontSize(8)
                        .setFontColor(TEXT_MUTED)
                        .setCharacterSpacing(1)
                        .setMarginBottom(4));
                qCell.add(new Paragraph(feedbackText)
                        .setFont(font)
                        .setFontSize(10)
                        .setFontColor(TEXT_PRIMARY)
                        .setMultipliedLeading(1.4f));
            }
            qTable.addCell(qCell);
            document.add(qTable);
        }
    }
    private void addTopicsNotCovered(Document document, PdfFont boldFont, PdfFont font,
                                     Map<String, Object> feedback) {
        @SuppressWarnings("unchecked")
        List<String> topics = (List<String>) feedback.get("topicsNotCovered");
        if (topics == null || topics.isEmpty()) return;
        document.add(new Paragraph("Important topics for this position that weren't covered in this interview:")
                .setFont(font)
                .setFontSize(10)
                .setFontColor(TEXT_SECONDARY)
                .setItalic()
                .setMarginBottom(12));
        Table tagsTable = new Table(UnitValue.createPercentArray(3)).useAllAvailableWidth();
        for (String topic : topics) {
            if (topic == null || topic.isBlank()) continue;
            Cell tagCell = new Cell()
                    .setBorder(Border.NO_BORDER)
                    .setPadding(10)
                    .setMargin(4)
                    .setBackgroundColor(new DeviceRgb(239, 246, 255))
                    .setBorderLeft(new SolidBorder(INFO_COLOR, 3))
                    .add(new Paragraph(topic)
                            .setFont(boldFont)
                            .setFontSize(10)
                            .setFontColor(new DeviceRgb(30, 64, 175)));
            tagsTable.addCell(tagCell);
        }
        int remainder = topics.size() % 3;
        if (remainder != 0) {
            for (int i = 0; i < (3 - remainder); i++) {
                tagsTable.addCell(new Cell().setBorder(Border.NO_BORDER));
            }
        }
        document.add(tagsTable);
    }
    private void addResources(Document document, PdfFont boldFont, PdfFont font,
                              PdfFont italicFont, Map<String, Object> feedback) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resources = (List<Map<String, Object>>) feedback.get("resources");
        if (resources == null || resources.isEmpty()) return;
        document.add(new Paragraph("Curated resources to help you improve in your weak areas:")
                .setFont(font)
                .setFontSize(10)
                .setFontColor(TEXT_SECONDARY)
                .setItalic()
                .setMarginBottom(15));
        for (Map<String, Object> resourceGroup : resources) {
            String topic = (String) resourceGroup.getOrDefault("topic", "General");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> links = (List<Map<String, Object>>) resourceGroup.get("links");
            if (links == null || links.isEmpty()) continue;
            Table topicTable = new Table(1).useAllAvailableWidth().setMarginBottom(10);
            Cell topicCell = new Cell()
                    .setBorder(Border.NO_BORDER)
                    .setBorderLeft(new SolidBorder(PRIMARY_COLOR, 3))
                    .setPaddingLeft(10)
                    .setPaddingTop(4)
                    .setPaddingBottom(4)
                    .add(new Paragraph(topic)
                            .setFont(boldFont)
                            .setFontSize(12)
                            .setFontColor(PRIMARY_DARK));
            topicTable.addCell(topicCell);
            document.add(topicTable);
            for (Map<String, Object> link : links) {
                String title = (String) link.getOrDefault("title", "Resource");
                String url = (String) link.getOrDefault("url", "");
                if (url.isBlank()) continue;
                Paragraph linkPara = new Paragraph()
                        .add(new Text("→  ").setFont(boldFont).setFontColor(PRIMARY_COLOR))
                        .add(new Text(title)
                                .setFont(boldFont)
                                .setFontSize(10)
                                .setFontColor(TEXT_PRIMARY))
                        .setMarginLeft(15)
                        .setMarginBottom(2);
                document.add(linkPara);
                document.add(new Paragraph(url)
                        .setFont(italicFont)
                        .setFontSize(8)
                        .setFontColor(INFO_COLOR)
                        .setMarginLeft(30)
                        .setMarginBottom(8));
            }
        }
    }
    @SuppressWarnings("unchecked")
    private void addAntiCheatReport(Document document, PdfFont boldFont, PdfFont font,
                                    Map<String, Object> feedback) {
        Map<String, Object> report = (Map<String, Object>) feedback.get("antiCheatReport");
        if (report == null) return;
        int totalViolations = report.get("totalViolations") instanceof Number
                ? ((Number) report.get("totalViolations")).intValue() : 0;
        int fullscreenExits = report.get("fullscreenExits") instanceof Number
                ? ((Number) report.get("fullscreenExits")).intValue() : 0;
        int copyAttempts = report.get("copyAttempts") instanceof Number
                ? ((Number) report.get("copyAttempts")).intValue() : 0;
        int pasteAttempts = report.get("pasteAttempts") instanceof Number
                ? ((Number) report.get("pasteAttempts")).intValue() : 0;
        DeviceRgb statusColor = totalViolations > 0 ? DANGER_COLOR : SUCCESS_COLOR;
        String statusText = totalViolations > 0 ? "VIOLATIONS DETECTED" : "CLEAN SESSION";
        Table banner = new Table(1).useAllAvailableWidth().setMarginBottom(15);
        Cell bannerCell = new Cell()
                .setBorder(Border.NO_BORDER)
                .setBackgroundColor(totalViolations > 0
                        ? new DeviceRgb(254, 242, 242)
                        : new DeviceRgb(240, 253, 244))
                .setBorderLeft(new SolidBorder(statusColor, 4))
                .setPadding(15)
                .add(new Paragraph(statusText)
                        .setFont(boldFont)
                        .setFontSize(12)
                        .setFontColor(statusColor)
                        .setCharacterSpacing(1));
        banner.addCell(bannerCell);
        document.add(banner);
        Table statsTable = new Table(UnitValue.createPercentArray(4)).useAllAvailableWidth();
        statsTable.addCell(createMetricCell("Total Violations",
                String.valueOf(totalViolations), totalViolations > 0 ? DANGER_COLOR : SUCCESS_COLOR, boldFont, font));
        statsTable.addCell(createMetricCell("Fullscreen Exits",
                String.valueOf(fullscreenExits), fullscreenExits > 0 ? WARNING_COLOR : SUCCESS_COLOR, boldFont, font));
        statsTable.addCell(createMetricCell("Copy Attempts",
                String.valueOf(copyAttempts), copyAttempts > 0 ? WARNING_COLOR : SUCCESS_COLOR, boldFont, font));
        statsTable.addCell(createMetricCell("Paste Attempts",
                String.valueOf(pasteAttempts), pasteAttempts > 0 ? WARNING_COLOR : SUCCESS_COLOR, boldFont, font));
        document.add(statsTable);
        List<Map<String, Object>> violations = (List<Map<String, Object>>) report.get("violations");
        if (violations != null && !violations.isEmpty()) {
            document.add(new Paragraph("\nViolation Timeline")
                    .setFont(boldFont)
                    .setFontSize(11)
                    .setFontColor(TEXT_PRIMARY)
                    .setMarginTop(15)
                    .setMarginBottom(8));
            for (int i = 0; i < Math.min(violations.size(), 20); i++) {
                Map<String, Object> v = violations.get(i);
                String type = (String) v.getOrDefault("type", "UNKNOWN");
                String details = (String) v.getOrDefault("details", "");
                String timestamp = (String) v.getOrDefault("timestamp", "");
                Paragraph violationLine = new Paragraph()
                        .add(new Text("#" + (i + 1) + "  ")
                                .setFont(boldFont).setFontSize(9).setFontColor(DANGER_COLOR))
                        .add(new Text(type + " — ")
                                .setFont(boldFont).setFontSize(9).setFontColor(TEXT_PRIMARY))
                        .add(new Text(details)
                                .setFont(font).setFontSize(9).setFontColor(TEXT_SECONDARY))
                        .setMarginBottom(4)
                        .setMultipliedLeading(1.3f);
                document.add(violationLine);
            }
            if (violations.size() > 20) {
                document.add(new Paragraph("... and " + (violations.size() - 20) + " more violations")
                        .setFont(font).setFontSize(9).setFontColor(TEXT_MUTED).setItalic());
            }
        }
    }
    private void addNextSteps(Document document, PdfFont boldFont, PdfFont font,
                              PdfFont italicFont, Map<String, Object> feedback) {
        int score = extractScore(feedback);
        String advice = getActionableAdvice(score);
        Table card = new Table(1).useAllAvailableWidth();
        Cell cell = new Cell()
                .setBorder(Border.NO_BORDER)
                .setBackgroundColor(PRIMARY_COLOR)
                .setPadding(25);
        cell.add(new Paragraph("Recommended Actions")
                .setFont(boldFont)
                .setFontSize(14)
                .setFontColor(new DeviceRgb(255, 255, 255))
                .setMarginBottom(12));
        cell.add(new Paragraph(advice)
                .setFont(font)
                .setFontSize(11)
                .setFontColor(new DeviceRgb(238, 242, 255))
                .setMultipliedLeading(1.5f));
        card.addCell(cell);
        document.add(card);
        document.add(new Paragraph("\n\"Practice makes progress. Keep going.\"")
                .setFont(italicFont)
                .setFontSize(11)
                .setFontColor(TEXT_MUTED)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(20));
    }
    private static class FooterEventHandler implements IEventHandler {
        @Override
        public void handleEvent(Event event) {
            if (!(event instanceof PdfDocumentEvent docEvent)) return;
            PdfDocument pdf = docEvent.getDocument();
            PdfPage page = docEvent.getPage();
            int pageNumber = pdf.getPageNumber(page);
            int totalPages = pdf.getNumberOfPages();
            if (pageNumber == 1) return;
            try {
                PdfFont font;
                try {
                    try (var is = new ClassPathResource("fonts/DejaVuSans.ttf").getInputStream()) {
                        byte[] fontBytes = is.readAllBytes();
                        font = PdfFontFactory.createFont(
                                fontBytes,
                                PdfEncodings.IDENTITY_H,
                                EmbeddingStrategy.PREFER_EMBEDDED
                        );
                    }
                } catch (Exception e) {
                    font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
                }
                Rectangle pageSize = page.getPageSize();
                PdfCanvas pdfCanvas = new PdfCanvas(page);
                try (Canvas layoutCanvas = new Canvas(pdfCanvas, pageSize)) {
                    layoutCanvas.setFont(font).setFontSize(8).setFontColor(TEXT_MUTED);
                    layoutCanvas.showTextAligned(
                            "AI Interview Assistant",
                            PAGE_MARGIN_SIDES,
                            25,
                            TextAlignment.LEFT
                    );
                    layoutCanvas.showTextAligned(
                            String.format("Page %d of %d", pageNumber, totalPages),
                            pageSize.getWidth() - PAGE_MARGIN_SIDES,
                            25,
                            TextAlignment.RIGHT
                    );
                }
            } catch (Exception e) {
                log.warn("Failed to add footer: {}", e.getMessage());
            }
        }
    }
    private int extractScore(Map<String, Object> feedback) {
        Object scoreObj = feedback.get("overallScore");
        return scoreObj instanceof Number ? ((Number) scoreObj).intValue() : DEFAULT_SCORE;
    }
    private DeviceRgb resolveScoreColor(int score) {
        if (score >= HIGH_SCORE_THRESHOLD) return SUCCESS_COLOR;
        if (score >= MID_SCORE_THRESHOLD)  return WARNING_COLOR;
        return DANGER_COLOR;
    }
    private String getScoreLabel(int score) {
        if (score >= 90) return "EXCELLENT";
        if (score >= 75) return "STRONG";
        if (score >= 60) return "GOOD";
        if (score >= 40) return "FAIR";
        return "NEEDS WORK";
    }
    private String getActionableAdvice(int score) {
        if (score >= 85) {
            return "Outstanding performance! You demonstrated strong technical knowledge across all areas. " +
                    "Continue practicing to maintain this level. Consider tackling more challenging system design " +
                    "questions and contributing to open-source projects to deepen your expertise.";
        } else if (score >= 70) {
            return "Solid performance overall. Focus on the improvement areas highlighted above. " +
                    "Review the recommended resources, practice 2-3 mock interviews per week, and build " +
                    "small projects that involve the topics you found challenging.";
        } else if (score >= 50) {
            return "You have a foundation to build on. Prioritize the weakest topics first — review the " +
                    "recommended resources thoroughly. Practice coding daily on platforms like LeetCode, and " +
                    "schedule another mock interview in 2-3 weeks to track progress.";
        } else {
            return "This session revealed significant knowledge gaps. Don't be discouraged — every expert was " +
                    "once a beginner. Start with fundamentals: review core concepts using the recommended resources, " +
                    "build small projects, and practice consistently. Schedule another interview in 4-6 weeks.";
        }
    }
    private boolean hasCategories(Map<String, Object> feedback) {
        Object obj = feedback.get("categories");
        return obj instanceof List<?> && !((List<?>) obj).isEmpty();
    }
    private boolean hasDetailedAnswers(Map<String, Object> feedback) {
        Object obj = feedback.get("detailedAnswers");
        return obj instanceof List<?> && !((List<?>) obj).isEmpty();
    }
    private boolean hasTopicsNotCovered(Map<String, Object> feedback) {
        Object obj = feedback.get("topicsNotCovered");
        return obj instanceof List<?> && !((List<?>) obj).isEmpty();
    }
    private boolean hasResources(Map<String, Object> feedback) {
        Object obj = feedback.get("resources");
        return obj instanceof List<?> && !((List<?>) obj).isEmpty();
    }
    private boolean hasAntiCheatReport(Map<String, Object> feedback) {
        return feedback.get("antiCheatReport") instanceof Map<?, ?>;
    }
    private PdfFont loadUnicodeFont(String resourcePath) throws Exception {
        try (var is = new ClassPathResource(resourcePath).getInputStream()) {
            byte[] fontBytes = is.readAllBytes();
            return PdfFontFactory.createFont(
                    fontBytes,
                    PdfEncodings.IDENTITY_H,
                    EmbeddingStrategy.PREFER_EMBEDDED
            );
        }
    }
    private PdfFont getRegularFont() {
        try {
            PdfFont font = loadUnicodeFont("fonts/DejaVuSans.ttf");
            log.debug("✅ Loaded DejaVuSans (Unicode support)");
            return font;
        } catch (Exception e) {
            log.warn("⚠️ DejaVuSans not found, falling back to Helvetica (no Cyrillic support): {}", e.getMessage());
            try {
                return PdfFontFactory.createFont(StandardFonts.HELVETICA);
            } catch (Exception ex) {
                throw new RuntimeException("Failed to create any font", ex);
            }
        }
    }

    private PdfFont getBoldFont() {
        try {
            return loadUnicodeFont("fonts/DejaVuSans-Bold.ttf");
        } catch (Exception e) {
            log.warn("⚠️ DejaVuSans-Bold not found, falling back to Helvetica-Bold");
            try {
                return PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            } catch (Exception ex) {
                throw new RuntimeException("Failed to create bold font", ex);
            }
        }
    }

    private PdfFont getItalicFont() {
        try {
            return loadUnicodeFont("fonts/DejaVuSans-Oblique.ttf");
        } catch (Exception e) {
            log.warn("⚠️ DejaVuSans-Oblique not found, falling back to Helvetica-Oblique");
            try {
                return PdfFontFactory.createFont(StandardFonts.HELVETICA_OBLIQUE);
            } catch (Exception ex) {
                throw new RuntimeException("Failed to create italic font", ex);
            }
        }
    }
}