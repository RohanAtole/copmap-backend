package in.copmap.service.impl;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import in.copmap.entity.Assignment;
import in.copmap.entity.Checkpoint;
import in.copmap.entity.Operation;
import in.copmap.exception.CopMapException;
import in.copmap.repository.AssignmentRepository;
import in.copmap.repository.OperationRepository;
import in.copmap.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportServiceImpl implements ReportService {

    private static final DeviceRgb HEADER_COLOR = new DeviceRgb(0x1a, 0x56, 0x76);  // police blue
    private static final DeviceRgb ROW_ALT_COLOR = new DeviceRgb(0xf0, 0xf4, 0xf8);
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm z");
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final OperationRepository operationRepository;
    private final AssignmentRepository assignmentRepository;

    @Override
    @Transactional(readOnly = true)
    public byte[] generateOperationReport(UUID operationId) {
        Operation op = operationRepository.findById(operationId)
                .orElseThrow(() -> new CopMapException("OPERATION_NOT_FOUND",
                        "Operation not found: " + operationId));

        List<Assignment> assignments = assignmentRepository.findByOperationId(operationId);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc);
            document.setMargins(40, 40, 40, 40);

            addHeader(document, op);
            addOperationDetails(document, op);

            if (op.getCheckpoints() != null && !op.getCheckpoints().isEmpty()) {
                addCheckpoints(document, op.getCheckpoints());
            }

            addAssignmentsTable(document, assignments);
            addFooter(document);

            document.close();
            log.info("PDF report generated for operation {}", operationId);
            return baos.toByteArray();

        } catch (IOException e) {
            throw new CopMapException("PDF_ERROR", "Failed to generate PDF: " + e.getMessage());
        }
    }

    private void addHeader(Document doc, Operation op) throws IOException {
        // CopMap Logo / Header Banner
        Paragraph header = new Paragraph("CopMap — Operation Report")
                .setBold()
                .setFontSize(20)
                .setFontColor(HEADER_COLOR)
                .setTextAlignment(TextAlignment.CENTER);
        doc.add(header);

        Paragraph subHeader = new Paragraph(op.getTitle())
                .setFontSize(14)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(10);
        doc.add(subHeader);

        doc.add(new LineSeparator(new com.itextpdf.kernel.pdf.canvas.draw.SolidLine(1f))
                .setMarginBottom(15));
    }

    private void addOperationDetails(Document doc, Operation op) {
        doc.add(sectionTitle("Operation Details"));

        Table table = new Table(new float[]{2, 3}).setWidth(UnitValue.createPercentValue(100));

        addRow(table, "Operation ID", op.getId().toString(), false);
        addRow(table, "Type", op.getType().name(), true);
        addRow(table, "Status", op.getStatus().name(), false);
        addRow(table, "Station", op.getStationName(), true);
        addRow(table, "Location", op.getLocationName(), false);
        addRow(table, "Planned Start", formatInstant(op.getPlannedStart()), true);
        addRow(table, "Planned End", formatInstant(op.getPlannedEnd()), false);
        if (op.getActualStart() != null)
            addRow(table, "Actual Start", formatInstant(op.getActualStart()), true);
        if (op.getActualEnd() != null)
            addRow(table, "Actual End", formatInstant(op.getActualEnd()), false);
        if (op.getShiftType() != null)
            addRow(table, "Shift", op.getShiftType().name(), true);
        addRow(table, "Created By",
                op.getCreatedBy().getFullName() + " (" + op.getCreatedBy().getBadgeNumber() + ")", false);
        if (op.getClosureNotes() != null)
            addRow(table, "Closure Notes", op.getClosureNotes(), true);

        doc.add(table.setMarginBottom(15));

        if (op.getDescription() != null) {
            doc.add(new Paragraph("Description: " + op.getDescription())
                    .setFontSize(10)
                    .setItalic()
                    .setMarginBottom(15));
        }
    }

    private void addCheckpoints(Document doc, List<Checkpoint> checkpoints) {
        doc.add(sectionTitle("Checkpoints / Naka Points"));

        Table table = new Table(new float[]{0.5f, 2, 1.5f, 1.5f, 1})
                .setWidth(UnitValue.createPercentValue(100));

        // Header row
        for (String h : new String[]{"#", "Name", "Latitude", "Longitude", "Radius (m)"}) {
            table.addHeaderCell(new Cell().add(new Paragraph(h).setBold())
                    .setBackgroundColor(HEADER_COLOR)
                    .setFontColor(ColorConstants.WHITE));
        }

        for (int i = 0; i < checkpoints.size(); i++) {
            Checkpoint cp = checkpoints.get(i);
            boolean alt = i % 2 == 1;
            table.addCell(styledCell(String.valueOf(i + 1), alt));
            table.addCell(styledCell(cp.getName(), alt));
            table.addCell(styledCell(String.format("%.6f", cp.getLatitude()), alt));
            table.addCell(styledCell(String.format("%.6f", cp.getLongitude()), alt));
            table.addCell(styledCell(String.valueOf(cp.getRadiusMeters()), alt));
        }

        doc.add(table.setMarginBottom(15));
    }

    private void addAssignmentsTable(Document doc, List<Assignment> assignments) {
        doc.add(sectionTitle("Officer Assignments (" + assignments.size() + " total)"));

        Table table = new Table(new float[]{2, 2, 1.5f, 2, 2, 2})
                .setWidth(UnitValue.createPercentValue(100));

        for (String h : new String[]{"Badge", "Name", "Rank", "Duty Role", "Status", "Check-In"}) {
            table.addHeaderCell(new Cell().add(new Paragraph(h).setBold())
                    .setBackgroundColor(HEADER_COLOR)
                    .setFontColor(ColorConstants.WHITE));
        }

        for (int i = 0; i < assignments.size(); i++) {
            Assignment a = assignments.get(i);
            boolean alt = i % 2 == 1;
            table.addCell(styledCell(a.getOfficer().getBadgeNumber(), alt));
            table.addCell(styledCell(a.getOfficer().getFullName(), alt));
            table.addCell(styledCell(a.getOfficer().getRank() != null ? a.getOfficer().getRank() : "-", alt));
            table.addCell(styledCell(a.getDutyRole() != null ? a.getDutyRole() : "-", alt));
            table.addCell(styledCell(a.getStatus().name(), alt));
            table.addCell(styledCell(a.getCheckedInAt() != null ? formatInstant(a.getCheckedInAt()) : "-", alt));
        }

        doc.add(table.setMarginBottom(20));
    }

    private void addFooter(Document doc) {
        doc.add(new LineSeparator(new com.itextpdf.kernel.pdf.canvas.draw.SolidLine(1f)));
        doc.add(new Paragraph("Generated by CopMap | " + formatInstant(java.time.Instant.now()))
                .setFontSize(8)
                .setItalic()
                .setFontColor(ColorConstants.GRAY)
                .setTextAlignment(TextAlignment.CENTER));
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private Paragraph sectionTitle(String text) {
        return new Paragraph(text)
                .setBold()
                .setFontSize(13)
                .setFontColor(HEADER_COLOR)
                .setMarginTop(10)
                .setMarginBottom(5);
    }

    private void addRow(Table table, String label, String value, boolean altRow) {
        table.addCell(new Cell().add(new Paragraph(label).setBold().setFontSize(9))
                .setBackgroundColor(altRow ? ROW_ALT_COLOR : ColorConstants.WHITE));
        table.addCell(new Cell().add(new Paragraph(value).setFontSize(9))
                .setBackgroundColor(altRow ? ROW_ALT_COLOR : ColorConstants.WHITE));
    }

    private Cell styledCell(String value, boolean alt) {
        return new Cell().add(new Paragraph(value).setFontSize(9))
                .setBackgroundColor(alt ? ROW_ALT_COLOR : ColorConstants.WHITE);
    }

    private String formatInstant(java.time.Instant instant) {
        if (instant == null) return "-";
        return ZonedDateTime.ofInstant(instant, IST).format(FORMATTER);
    }
}
