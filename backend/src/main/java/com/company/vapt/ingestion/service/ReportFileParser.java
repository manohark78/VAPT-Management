package com.company.vapt.ingestion.service;

import com.company.vapt.finding.entity.Finding;

import com.company.vapt.ingestion.storage.FileStorageService;
import com.company.vapt.report.domain.ReportSectionId;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.hssf.eventusermodel.FormatTrackingHSSFListener;
import org.apache.poi.hssf.eventusermodel.HSSFEventFactory;
import org.apache.poi.hssf.eventusermodel.HSSFListener;
import org.apache.poi.hssf.eventusermodel.HSSFRequest;
import org.apache.poi.hssf.eventusermodel.MissingRecordAwareHSSFListener;
import org.apache.poi.hssf.eventusermodel.dummyrecord.LastCellOfRowDummyRecord;
import org.apache.poi.hssf.eventusermodel.dummyrecord.MissingCellDummyRecord;
import org.apache.poi.hssf.record.*;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.springframework.stereotype.Service;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

/** Streaming CSV and bounded, section-aware Excel source parser. */
@Service
public class ReportFileParser {

    private static final int HEADER_SCAN_LIMIT = 50;
    private static final int MAX_COLUMNS = 512;
    private static final Map<String, String> GENERAL_FIELDS = Map.of(
            "type", "Type", "port", "Port", "risk score", "Risk Score", "risk rating", "Risk Rating",
            "compliance status", "Compliance Status", "severity", "Severity", "results", "Results",
            "uid", "UID", "targets", "Targets", "display name", "Display Name");
    private static final Map<String, String> APPSEC_FIELDS = Map.of(
            "record id", "Record Id", "finding id", "Finding ID", "primary owner", "Primary Owner",
            "title", "Title", "severity", "Severity", "detailed finding description", "Detailed Finding Description");

    private final FileStorageService fileStorageService;

    public ReportFileParser(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    public void parse(ReportSectionId sectionId, String storagePath, String extension,
                      Consumer<Header> headerConsumer, Runnable rowStarted,
                      Consumer<ReportRecordPersistenceService.SourceRow> rowConsumer) {
        Path path = fileStorageService.resolvePath(storagePath);
        String detected = detectFormat(path);
        String expected = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        if (!detected.equals(expected)) {
            throw new ImportProcessingException("VALIDATING", "FORMAT_MISMATCH",
                    "File content does not match the uploaded file format.");
        }
        if (".csv".equals(detected)) {
            parseCsv(sectionId, path, headerConsumer, rowStarted, rowConsumer);
        } else if (".xlsx".equals(detected)) {
            parseXlsx(sectionId, path, headerConsumer, rowStarted, rowConsumer);
        } else {
            parseXls(sectionId, path, headerConsumer, rowStarted, rowConsumer);
        }
    }

    private String detectFormat(Path path) {
        try (InputStream in = new BufferedInputStream(java.nio.file.Files.newInputStream(path))) {
            in.mark(8);
            byte[] prefix = in.readNBytes(8);
            if (prefix.length >= 4 && prefix[0] == 'P' && prefix[1] == 'K' && prefix[2] == 3 && prefix[3] == 4) return ".xlsx";
            if (prefix.length >= 8 && (prefix[0] & 0xff) == 0xd0 && (prefix[1] & 0xff) == 0xcf
                    && (prefix[2] & 0xff) == 0x11 && (prefix[3] & 0xff) == 0xe0) return ".xls";
            if (prefix.length > 0 && Arrays.stream(toUnsignedInts(prefix)).noneMatch(b -> b == 0)) return ".csv";
            throw new ImportProcessingException("VALIDATING", "UNSUPPORTED_FORMAT", "The uploaded content is not CSV, XLS, or XLSX.");
        } catch (IOException e) {
            throw new ImportProcessingException("VALIDATING", "FILE_READ_FAILED", "The uploaded file could not be read.", e);
        }
    }

    private int[] toUnsignedInts(byte[] bytes) {
        int[] values = new int[bytes.length];
        for (int i = 0; i < bytes.length; i++) values[i] = bytes[i] & 0xff;
        return values;
    }

    private void parseCsv(ReportSectionId sectionId, Path path, Consumer<Header> headerConsumer, Runnable rowStarted,
                          Consumer<ReportRecordPersistenceService.SourceRow> rowConsumer) {
        try (InputStream in = java.nio.file.Files.newInputStream(path);
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8.newDecoder()
                     .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT));
             CSVParser parser = CSVFormat.DEFAULT.builder().setIgnoreEmptyLines(false).build().parse(reader)) {
            Iterator<CSVRecord> iterator = parser.iterator();
            List<List<String>> candidates = new ArrayList<>();
            int scanned = 0;
            int headerIndex = -1;
            List<String> rawHeaders = null;
            while (iterator.hasNext() && scanned < HEADER_SCAN_LIMIT) {
                CSVRecord record = iterator.next();
                List<String> values = values(record);
                candidates.add(values);
                if (containsIdentityHeader(sectionId, values)) {
                    if (headerIndex >= 0) throw headerAmbiguous("CSV");
                    headerIndex = scanned;
                    rawHeaders = values;
                }
                scanned++;
            }
            if (headerIndex < 0) throw missingIdentityHeader(sectionId);
            Header header = makeHeader(sectionId, rawHeaders, headerIndex + 1);
            headerConsumer.accept(header);
            for (int i = headerIndex + 1; i < candidates.size(); i++) emitCsvRow(candidates.get(i), i + 1, header, rowStarted, rowConsumer);
            while (iterator.hasNext()) {
                CSVRecord record = iterator.next();
                emitCsvRow(values(record), (int) record.getRecordNumber(), header, rowStarted, rowConsumer);
            }
        } catch (ImportProcessingException e) {
            throw e;
        } catch (Exception e) {
            throw new ImportProcessingException("STAGING", "CSV_PARSE_FAILED", "CSV parsing failed. Check its encoding and row structure.", e);
        }
    }

    /** Reads XLS with POI's event model; rows are emitted as cell records arrive. */
    private void parseXls(ReportSectionId sectionId, Path path, Consumer<Header> headerConsumer, Runnable rowStarted,
                          Consumer<ReportRecordPersistenceService.SourceRow> rowConsumer) {
        try {
            List<SheetHeader> candidates;
            try (POIFSFileSystem fileSystem = new POIFSFileSystem(path.toFile(), true)) {
                XlsEventCollector scan = new XlsEventCollector(sectionId, -1, -1, null, null, null);
                processXls(fileSystem, scan);
                scan.finish();
                candidates = scan.headers;
            }
            if (candidates.isEmpty()) throw missingIdentityHeader(sectionId);
            if (candidates.size() > 1) {
                throw new ImportProcessingException("VALIDATING", "AMBIGUOUS_WORKSHEET",
                        "More than one worksheet or header row contains the section identity column.");
            }
            SheetHeader candidate = candidates.get(0);
            Header header = makeHeader(sectionId, candidate.headers(), candidate.headerRow() + 1);
            headerConsumer.accept(header);
            try (POIFSFileSystem fileSystem = new POIFSFileSystem(path.toFile(), true)) {
                XlsEventCollector data = new XlsEventCollector(sectionId, candidate.sheetIndex(), candidate.headerRow(),
                        header, rowStarted, rowConsumer);
                processXls(fileSystem, data);
                data.finish();
            }
        } catch (ImportProcessingException e) {
            throw e;
        } catch (Exception e) {
            throw new ImportProcessingException("STAGING", "WORKBOOK_PARSE_FAILED",
                    "Excel workbook parsing failed. Check that the workbook is readable and unencrypted.", e);
        }
    }

    private void processXls(POIFSFileSystem fileSystem, XlsEventCollector collector) throws IOException {
        MissingRecordAwareHSSFListener missingCells = new MissingRecordAwareHSSFListener(collector);
        FormatTrackingHSSFListener formatting = new FormatTrackingHSSFListener(missingCells, Locale.ROOT);
        collector.setFormatting(formatting);
        HSSFRequest request = new HSSFRequest();
        request.addListenerForAllRecords(formatting);
        new HSSFEventFactory().processWorkbookEvents(request, fileSystem);
    }

    /** Reads XLSX through POI's SAX event model so worksheet rows are not materialised in memory. */
    private void parseXlsx(ReportSectionId sectionId, Path path, Consumer<Header> headerConsumer, Runnable rowStarted,
                          Consumer<ReportRecordPersistenceService.SourceRow> rowConsumer) {
        try (OPCPackage pkg = OPCPackage.open(path.toFile(), PackageAccess.READ)) {
            XSSFReader workbookReader = new XSSFReader(pkg);
            StylesTable styles = workbookReader.getStylesTable();
            SharedStrings sharedStrings = new ReadOnlySharedStringsTable(pkg);
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            List<StreamingSheetHeader> candidates = new ArrayList<>();
            XSSFReader.SheetIterator scanSheets = workbookReader.getSheetIterator();
            int sheetIndex = 0;
            while (scanSheets.hasNext()) {
                InputStream sheet = scanSheets.next();
                String sheetName = scanSheets.getSheetName();
                try (sheet) {
                    ScanHandler handler = new ScanHandler(sectionId, sheetIndex, sheetName);
                    try {
                        parseSheetXml(sheet, styles, sharedStrings, formatter, handler);
                    } catch (StopSheetScan ignored) {
                        // Header discovery is limited to the first 50 physical rows of each worksheet.
                    }
                    candidates.addAll(handler.headers);
                    sheetIndex++;
                }
            }
            if (candidates.isEmpty()) throw missingIdentityHeader(sectionId);
            if (candidates.size() > 1) {
                throw new ImportProcessingException("VALIDATING", "AMBIGUOUS_WORKSHEET",
                        "More than one worksheet or header row contains the section identity column.");
            }
            StreamingSheetHeader candidate = candidates.get(0);
            Header header = makeHeader(sectionId, candidate.headers(), candidate.headerRow() + 1);
            headerConsumer.accept(header);
            XSSFReader.SheetIterator dataSheets = workbookReader.getSheetIterator();
            for (int index = 0; dataSheets.hasNext(); index++) {
                InputStream sheet = dataSheets.next();
                try (sheet) {
                    if (index == candidate.sheetIndex()) {
                        parseSheetXml(sheet, styles, sharedStrings, formatter,
                                new DataHandler(candidate.headerRow(), header, rowStarted, rowConsumer));
                        return;
                    }
                }
            }
            throw new ImportProcessingException("VALIDATING", "WORKSHEET_NOT_FOUND", "The identified worksheet could not be read.");
        } catch (ImportProcessingException e) {
            throw e;
        } catch (Exception e) {
            throw new ImportProcessingException("STAGING", "WORKBOOK_PARSE_FAILED",
                    "Excel workbook parsing failed. Check that the workbook is readable and unencrypted.", e);
        }
    }

    private void parseSheetXml(InputStream sheet, StylesTable styles, SharedStrings strings,
                               DataFormatter formatter, XSSFSheetXMLHandler.SheetContentsHandler handler) throws Exception {
        XMLReader xmlReader = XMLHelper.newXMLReader();
        xmlReader.setContentHandler(new XSSFSheetXMLHandler(styles, strings, handler, formatter, false));
        xmlReader.parse(new InputSource(sheet));
    }

    private List<String> values(CSVRecord record) {
        if (record.size() > MAX_COLUMNS) {
            throw new ImportProcessingException("STAGING", "TOO_MANY_COLUMNS", "A CSV row contains too many columns.");
        }
        List<String> values = new ArrayList<>(record.size());
        for (int i = 0; i < record.size(); i++) values.add(record.get(i));
        return values;
    }

    private void emitCsvRow(List<String> values, int rowNumber, Header header, Runnable rowStarted,
                            Consumer<ReportRecordPersistenceService.SourceRow> consumer) {
        emitRow(values, rowNumber, header, rowStarted, consumer);
    }

    private void emitRow(List<String> values, int rowNumber, Header header, Runnable rowStarted,
                         Consumer<ReportRecordPersistenceService.SourceRow> consumer) {
        if (values.stream().allMatch(value -> value == null || value.isBlank())) return;
        rowStarted.run();
        if (values.size() > header.columns().size()) {
            for (int i = header.columns().size(); i < values.size(); i++) {
                if (!values.get(i).isBlank()) throw rowError(rowNumber, "an unnamed column contains data");
            }
        }
        String identity = valueAt(values, header.identityColumn()).trim();
        if (identity.isEmpty() && "Finding ID".equals(header.identityName())) {
            throw rowError(rowNumber, header.identityName() + " is required");
        }
        if (identity.length() > 1000) throw rowError(rowNumber, header.identityName() + " exceeds the supported length");
        Map<String, String> fields = new LinkedHashMap<>();
        for (int i = 0; i < header.columns().size(); i++) {
            String canonical = header.columns().get(i);
            if (canonical != null) fields.put(canonical, valueAt(values, i));
        }
        String displayName = fields.get("Display Name");
        if (displayName != null && !displayName.isBlank()
                && header.identityName().equals("UID")) {
            String[] words = displayName.trim().split("\\s+");
            fields.put("AppTag", String.join(" ", Arrays.copyOf(words, Math.min(3, words.length))));
        }
        consumer.accept(new ReportRecordPersistenceService.SourceRow(rowNumber, identity, fields));
    }

    private String valueAt(List<String> values, int index) {
        return index < values.size() && values.get(index) != null ? values.get(index) : "";
    }

    private Header makeHeader(ReportSectionId sectionId, List<String> raw, int rowNumber) {
        if (raw == null || raw.isEmpty() || raw.size() > MAX_COLUMNS) {
            throw new ImportProcessingException("VALIDATING", "INVALID_HEADER", "The report header is empty or contains too many columns.");
        }
        Map<String, Integer> seen = new HashMap<>();
        List<String> columns = new ArrayList<>(raw.size());
        int identityColumn = -1;
        String identityName = sectionId == ReportSectionId.APPSEC_FINDINGS_PT ? "Finding ID" : "UID";
        for (int i = 0; i < raw.size(); i++) {
            String original = raw.get(i) == null ? "" : raw.get(i).replace("\uFEFF", "").trim();
            if (original.isEmpty()) throw new ImportProcessingException("VALIDATING", "BLANK_HEADER", "The report header contains a blank column name.");
            String key = normalizeHeader(original);
            if (seen.putIfAbsent(key, i) != null) throw new ImportProcessingException("VALIDATING", "DUPLICATE_HEADER", "The report header contains duplicate column names.");
            String canonical = canonicalField(sectionId, key);
            columns.add(canonical);
            if (normalizeHeader(identityName).equals(key)) identityColumn = i;
        }
        if (identityColumn < 0) throw missingIdentityHeader(sectionId);
        return new Header(columns, identityColumn, identityName, rowNumber);
    }

    private boolean containsIdentityHeader(ReportSectionId sectionId, List<String> values) {
        String identity = normalizeHeader(sectionId == ReportSectionId.APPSEC_FINDINGS_PT ? "Finding ID" : "UID");
        return values.stream().filter(Objects::nonNull).anyMatch(value -> normalizeHeader(value).equals(identity));
    }

    private String canonicalField(ReportSectionId sectionId, String normalized) {
        Map<String, String> recognized = sectionId == ReportSectionId.APPSEC_FINDINGS_PT ? APPSEC_FIELDS : GENERAL_FIELDS;
        return recognized.entrySet().stream()
                .filter(entry -> normalizeHeader(entry.getKey()).equals(normalized))
                .map(Map.Entry::getValue)
                .findFirst().orElse(null);
    }

    private String normalizeHeader(String value) {
        return value == null ? "" : value.replace("\uFEFF", "").trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private ImportProcessingException missingIdentityHeader(ReportSectionId sectionId) {
        String identity = sectionId == ReportSectionId.APPSEC_FINDINGS_PT ? "Finding ID" : "UID";
        return new ImportProcessingException("VALIDATING", "IDENTITY_HEADER_MISSING",
                "Could not confidently identify the report header. A " + identity + " column is required.");
    }

    private ImportProcessingException headerAmbiguous(String format) {
        return new ImportProcessingException("VALIDATING", "AMBIGUOUS_HEADER",
                "More than one possible header row was found in the " + format + " file.");
    }

    private ImportProcessingException rowError(int row, String detail) {
        return new ImportProcessingException("STAGING", "INVALID_ROW", "Row " + row + ": " + detail + ".");
    }

    private record SheetHeader(int sheetIndex, int headerRow, List<String> headers) { }
    private record StreamingSheetHeader(int sheetIndex, String sheetName, int headerRow, List<String> headers) { }
    public record Header(List<String> columns, int identityColumn, String identityName, int rowNumber) { }

    private final class ScanHandler implements XSSFSheetXMLHandler.SheetContentsHandler {
        private final ReportSectionId sectionId;
        private final int sheetIndex;
        private final String sheetName;
        private final List<StreamingSheetHeader> headers = new ArrayList<>();
        private final List<String> row = new ArrayList<>();

        private ScanHandler(ReportSectionId sectionId, int sheetIndex, String sheetName) {
            this.sectionId = sectionId;
            this.sheetIndex = sheetIndex;
            this.sheetName = sheetName;
        }

        @Override public void startRow(int rowNum) { row.clear(); }

        @Override public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            int column = cellReference == null ? row.size() : new CellReference(cellReference).getCol();
            if (column >= MAX_COLUMNS) throw new ImportProcessingException("VALIDATING", "TOO_MANY_COLUMNS", "A worksheet row contains too many columns.");
            while (row.size() <= column) row.add("");
            row.set(column, formattedValue == null ? "" : formattedValue);
        }

        @Override public void endRow(int rowNum) {
            if (rowNum < HEADER_SCAN_LIMIT && containsIdentityHeader(sectionId, row)) {
                headers.add(new StreamingSheetHeader(sheetIndex, sheetName, rowNum, List.copyOf(row)));
            }
            if (rowNum + 1 >= HEADER_SCAN_LIMIT) throw new StopSheetScan();
        }
    }

    private final class DataHandler implements XSSFSheetXMLHandler.SheetContentsHandler {
        private final int headerRow;
        private final Header header;
        private final Runnable rowStarted;
        private final Consumer<ReportRecordPersistenceService.SourceRow> rowConsumer;
        private final List<String> row = new ArrayList<>();

        private DataHandler(int headerRow, Header header, Runnable rowStarted,
                            Consumer<ReportRecordPersistenceService.SourceRow> rowConsumer) {
            this.headerRow = headerRow;
            this.header = header;
            this.rowStarted = rowStarted;
            this.rowConsumer = rowConsumer;
        }

        @Override public void startRow(int rowNum) { row.clear(); }

        @Override public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            int column = cellReference == null ? row.size() : new CellReference(cellReference).getCol();
            if (column >= MAX_COLUMNS) throw new ImportProcessingException("STAGING", "TOO_MANY_COLUMNS", "A worksheet row contains too many columns.");
            while (row.size() <= column) row.add("");
            row.set(column, formattedValue == null ? "" : formattedValue);
        }

        @Override public void endRow(int rowNum) {
            if (rowNum > headerRow) emitRow(row, rowNum + 1, header, rowStarted, rowConsumer);
        }
    }

    private static final class StopSheetScan extends RuntimeException { }

    private final class XlsEventCollector implements HSSFListener {
        private final ReportSectionId sectionId;
        private final int targetSheet;
        private final int headerRow;
        private final Header header;
        private final Runnable rowStarted;
        private final Consumer<ReportRecordPersistenceService.SourceRow> rowConsumer;
        private final List<SheetHeader> headers = new ArrayList<>();
        private final DataFormatter formatter = new DataFormatter(Locale.ROOT);
        private FormatTrackingHSSFListener formatting;
        private SSTRecord sharedStrings;
        private int sheetIndex = -1;
        private int currentRowNumber = -1;
        private List<String> currentRow = new ArrayList<>();
        private int pendingFormulaRow = -1;
        private int pendingFormulaColumn = -1;

        private XlsEventCollector(ReportSectionId sectionId, int targetSheet, int headerRow, Header header,
                                  Runnable rowStarted, Consumer<ReportRecordPersistenceService.SourceRow> rowConsumer) {
            this.sectionId = sectionId;
            this.targetSheet = targetSheet;
            this.headerRow = headerRow;
            this.header = header;
            this.rowStarted = rowStarted;
            this.rowConsumer = rowConsumer;
        }

        private void setFormatting(FormatTrackingHSSFListener formatting) { this.formatting = formatting; }

        @Override
        public void processRecord(org.apache.poi.hssf.record.Record record) {
            if (record instanceof SSTRecord sst) {
                sharedStrings = sst;
                return;
            }
            if (record instanceof BOFRecord bof && bof.getType() == BOFRecord.TYPE_WORKSHEET) {
                finishRow();
                sheetIndex++;
                return;
            }
            if (record instanceof LastCellOfRowDummyRecord last) {
                if (last.getRow() == currentRowNumber) finishRow();
                return;
            }
            if (record instanceof MissingCellDummyRecord missing) {
                ensureCell(missing.getRow(), missing.getColumn(), "");
                return;
            }
            if (record instanceof LabelSSTRecord labelSst) {
                put(labelSst, sharedStrings == null ? "" : sharedStrings.getString(labelSst.getSSTIndex()).getString());
            } else if (record instanceof LabelRecord label) {
                put(label.getRow(), label.getColumn(), label.getValue());
            } else if (record instanceof NumberRecord number) {
                put(number, formatting.formatNumberDateCell(number));
            } else if (record instanceof RKRecord rk) {
                put(rk, formatting.formatNumberDateCell(rk));
            } else if (record instanceof BoolErrRecord boolErr) {
                String value = boolErr.isBoolean() ? Boolean.toString(boolErr.getBooleanValue())
                        : FormulaError.forInt(boolErr.getErrorValue()).getString();
                put(boolErr.getRow(), boolErr.getColumn(), value);
            } else if (record instanceof FormulaRecord formula) {
                switch (formula.getCachedResultTypeEnum()) {
                    case STRING -> { pendingFormulaRow = formula.getRow(); pendingFormulaColumn = formula.getColumn(); }
                    case BOOLEAN -> put(formula.getRow(), formula.getColumn(), Boolean.toString(formula.getCachedBooleanValue()));
                    case ERROR -> put(formula.getRow(), formula.getColumn(), FormulaError.forInt(formula.getCachedErrorValue()).getString());
                    case BLANK -> put(formula.getRow(), formula.getColumn(), "");
                    default -> put(formula, formatting.formatNumberDateCell(formula));
                }
            } else if (record instanceof StringRecord string && pendingFormulaRow >= 0) {
                put(pendingFormulaRow, pendingFormulaColumn, string.getString());
                pendingFormulaRow = -1;
                pendingFormulaColumn = -1;
            } else if (record instanceof MulRKRecord multiple) {
                for (int index = 0; index < multiple.getNumColumns(); index++) {
                    int formatIndex = multiple.getXFAt(index);
                    put(multiple.getRow(), multiple.getFirstColumn() + index,
                            formatter.formatRawCellContents(multiple.getRKNumberAt(index), formatIndex,
                                    formatting.getFormatString(formatIndex)));
                }
            } else if (record instanceof MulBlankRecord multipleBlank) {
                for (int index = 0; index < multipleBlank.getNumColumns(); index++) {
                    put(multipleBlank.getRow(), multipleBlank.getFirstColumn() + index, "");
                }
            } else if (record instanceof EOFRecord) {
                finishRow();
            }
        }

        private void put(CellValueRecordInterface cell, String value) {
            put(cell.getRow(), cell.getColumn(), value);
        }

        private void put(int row, int column, String value) { ensureCell(row, column, value); }

        private void ensureCell(int row, int column, String value) {
            if (column < 0 || column >= MAX_COLUMNS) {
                throw new ImportProcessingException("STAGING", "TOO_MANY_COLUMNS", "A worksheet row contains too many columns.");
            }
            if (currentRowNumber >= 0 && currentRowNumber != row) finishRow();
            currentRowNumber = row;
            while (currentRow.size() <= column) currentRow.add("");
            currentRow.set(column, value == null ? "" : value);
        }

        private void finishRow() {
            if (currentRowNumber < 0) return;
            if (!currentRow.isEmpty()) {
                if (targetSheet < 0) {
                    if (currentRowNumber < HEADER_SCAN_LIMIT && containsIdentityHeader(sectionId, currentRow)) {
                        headers.add(new SheetHeader(sheetIndex, currentRowNumber, List.copyOf(currentRow)));
                    }
                } else if (sheetIndex == targetSheet && currentRowNumber > headerRow) {
                    emitRow(currentRow, currentRowNumber + 1, header, rowStarted, rowConsumer);
                }
            }
            currentRowNumber = -1;
            currentRow = new ArrayList<>();
        }

        private void finish() { finishRow(); }
    }
}
