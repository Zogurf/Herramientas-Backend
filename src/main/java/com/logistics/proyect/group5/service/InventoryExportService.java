package com.logistics.proyect.group5.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.logistics.proyect.group5.model.Product;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryExportService {

    private static final String[] HEADERS = {
            "SKU", "Nombre", "Categoría", "Precio", "Stock", "Estado stock", "Activo", "Actualizado"
    };

    private final ProductService productService;

    public byte[] exportExcel(String search, Integer categoryId, Boolean active, String stockStatus) {
        List<Product> products = findProducts(search, categoryId, active, stockStatus);
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Inventario");
            Row header = sheet.createRow(0);
            for (int index = 0; index < HEADERS.length; index++) {
                header.createCell(index).setCellValue(HEADERS[index]);
            }
            for (int rowIndex = 0; rowIndex < products.size(); rowIndex++) {
                Product product = products.get(rowIndex);
                Row row = sheet.createRow(rowIndex + 1);
                setCell(row, 0, product.getSku());
                setCell(row, 1, product.getName());
                setCell(row, 2, product.getCategory() == null ? "" : product.getCategory().getName());
                setCell(row, 3, product.getPrice());
                setCell(row, 4, product.getStock());
                setCell(row, 5, productService.stockStatus(product.getStock()));
                setCell(row, 6, product.isActive() ? "Sí" : "No");
                setCell(row, 7, product.getUpdatedAt() == null ? "" : product.getUpdatedAt().toString());
            }
            for (int index = 0; index < HEADERS.length; index++) {
                sheet.autoSizeColumn(index);
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo generar el archivo Excel.", exception);
        }
    }

    public byte[] exportPdf(String search, Integer categoryId, Boolean active, String stockStatus) {
        List<Product> products = findProducts(search, categoryId, active, stockStatus);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate(), 24, 24, 24, 24);
            PdfWriter.getInstance(document, output);
            document.open();
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            document.add(new Paragraph("Reporte de inventario", titleFont));
            document.add(new Paragraph("Productos exportados: " + products.size()));

            PdfPTable table = new PdfPTable(8);
            table.setWidthPercentage(100);
            table.setHeaderRows(1);
            for (String header : HEADERS) {
                PdfPCell cell = new PdfPCell(new Phrase(header));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }
            for (Product product : products) {
                table.addCell(value(product.getSku()));
                table.addCell(value(product.getName()));
                table.addCell(value(product.getCategory() == null ? "" : product.getCategory().getName()));
                table.addCell(value(product.getPrice()));
                table.addCell(value(product.getStock()));
                table.addCell(value(productService.stockStatus(product.getStock())));
                table.addCell(product.isActive() ? "Sí" : "No");
                table.addCell(value(product.getUpdatedAt()));
            }
            document.add(table);
            document.close();
            return output.toByteArray();
        } catch (DocumentException exception) {
            throw new IllegalStateException("No se pudo generar el archivo PDF.", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo cerrar el archivo PDF.", exception);
        }
    }

    private List<Product> findProducts(
            String search, Integer categoryId, Boolean active, String stockStatus) {
        return productService.searchInventory(
                        search, categoryId, active, stockStatus, Pageable.unpaged())
                .getContent();
    }

    private void setCell(Row row, int index, String value) {
        row.createCell(index).setCellValue(value);
    }

    private void setCell(Row row, int index, BigDecimal value) {
        Cell cell = row.createCell(index);
        if (value == null) {
            cell.setBlank();
        } else {
            cell.setCellValue(value.doubleValue());
        }
    }

    private void setCell(Row row, int index, Integer value) {
        Cell cell = row.createCell(index);
        if (value == null) {
            cell.setBlank();
        } else {
            cell.setCellValue(value);
        }
    }

    private String value(Object value) {
        return value == null ? "" : value.toString();
    }
}
