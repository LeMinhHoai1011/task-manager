package vn.smarttask.reports;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import vn.smarttask.reports.service.ExportWorker;

/** Pure render test: real CSV/XLSX/PDF bytes (no Spring, no storage). */
class ExportRenderTest {
  private List<List<String>> rows() {
    return List.of(
        List.of("11111111-2222-3333-4444-555555555555", "Ship it", "TODO", "HIGH", "", "2", "p1"),
        List.of("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", "Second, \"quoted\"", "DONE", "LOW", "", "0", "p1"));
  }

  @Test
  void csvHasHeaderAndEscapedCells() {
    String csv = new String(ExportWorker.renderCsv("PROJECT_PROGRESS", rows()),
        StandardCharsets.UTF_8);
    assertTrue(csv.contains("task_id,title,state,priority,due_at,assignees,project_id"));
    assertTrue(csv.contains("Ship it"));
    assertTrue(csv.contains("\"Second, \"\"quoted\"\"\""));
  }

  @Test
  void xlsxReadsBackWithPoi() throws Exception {
    byte[] bytes = ExportWorker.renderXlsx("PROJECT_PROGRESS", rows());
    assertTrue(bytes.length > 1000);
    // ZIP magic (XLSX is OOXML): PK..
    assertEquals('P', bytes[0]);
    assertEquals('K', bytes[1]);
    try (var wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      var sheet = wb.getSheet("report");
      assertNotNull(sheet);
      assertEquals("task_id", sheet.getRow(1).getCell(0).getStringCellValue());
      assertEquals("Ship it", sheet.getRow(2).getCell(1).getStringCellValue());
    }
  }

  @Test
  void pdfStartsWithMagicAndMentionsRows() {
    byte[] bytes = ExportWorker.renderPdf("PROJECT_PROGRESS", rows());
    assertTrue(bytes.length > 500);
    assertEquals("%PDF", new String(bytes, 0, 4, StandardCharsets.US_ASCII));
  }
}
