package com.company.vapt.cmdb.service;

import org.apache.commons.csv.*;
import org.apache.poi.hssf.eventusermodel.*;
import org.apache.poi.hssf.eventusermodel.dummyrecord.*;
import org.apache.poi.hssf.record.*;
import org.apache.poi.openxml4j.opc.*;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.*;
import org.apache.poi.xssf.model.*;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.xml.sax.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

/** Streams CSV/XLS/XLSX rows. Excel worksheet rows are decoded with POI event models. */
final class CmdbWorkbookReader {
    private static final int MAX_COLUMNS=512;
    void read(Path file,String extension,Consumer<RowData> consumer){
        if(extension.equals(".csv")){readCsv(file,consumer);return;}
        if(extension.equals(".xlsx")){readXlsx(file,consumer);return;}
        readXls(file,consumer);
    }
    private void readCsv(Path path,Consumer<RowData> out){
        try(CSVParser parser=CSVFormat.DEFAULT.parse(new InputStreamReader(java.nio.file.Files.newInputStream(path),StandardCharsets.UTF_8))){
            int row=0;for(CSVRecord record:parser){if(record.size()>MAX_COLUMNS)throw new IllegalArgumentException("CSV column limit exceeded");List<String> values=new ArrayList<>(record.size());record.forEach(values::add);out.accept(new RowData(0,"CSV",row++,values));}
        }catch(IOException e){throw new IllegalArgumentException("Could not read CSV file",e);}
    }
    private void readXlsx(Path path,Consumer<RowData> out){
        try(OPCPackage pkg=OPCPackage.open(path.toFile(),PackageAccess.READ)){
            XSSFReader reader=new XSSFReader(pkg);StylesTable styles=reader.getStylesTable();SharedStrings strings=new ReadOnlySharedStringsTable(pkg);DataFormatter formatter=new DataFormatter(Locale.ROOT);
            XSSFReader.SheetIterator sheets=reader.getSheetIterator();int sheetIndex=0;
            while(sheets.hasNext()){try(InputStream sheet=sheets.next()){String name=sheets.getSheetName();out.accept(new RowData(sheetIndex,name,-1,List.of()));XSSFHandler handler=new XSSFHandler(sheetIndex,name,out);XMLReader xml=XMLHelper.newXMLReader();xml.setContentHandler(new XSSFSheetXMLHandler(styles,strings,handler,formatter,false));xml.parse(new InputSource(sheet));}sheetIndex++;}
        }catch(Exception e){throw new IllegalArgumentException("Could not stream XLSX workbook",e);}
    }
    private void readXls(Path path,Consumer<RowData> out){
        try(POIFSFileSystem fs=new POIFSFileSystem(path.toFile(),true)){
            HSSFRows rows=new HSSFRows(out);MissingRecordAwareHSSFListener missing=new MissingRecordAwareHSSFListener(rows);FormatTrackingHSSFListener formatting=new FormatTrackingHSSFListener(missing,Locale.ROOT);rows.formatting=formatting;HSSFRequest request=new HSSFRequest();request.addListenerForAllRecords(formatting);new HSSFEventFactory().processWorkbookEvents(request,fs);rows.finish();
        }catch(Exception e){throw new IllegalArgumentException("Could not stream XLS workbook",e);}
    }
    record RowData(int sheetIndex,String sheetName,int rowNumber,List<String> values){}
    private static final class XSSFHandler implements XSSFSheetXMLHandler.SheetContentsHandler{
        private final int sheet;private final String name;private final Consumer<RowData> out;private final List<String> row=new ArrayList<>();
        XSSFHandler(int sheet,String name,Consumer<RowData> out){this.sheet=sheet;this.name=name;this.out=out;}
        public void startRow(int rowNum){row.clear();}
        public void cell(String reference,String value,XSSFComment comment){int col=reference==null?row.size():new CellReference(reference).getCol();if(col<0||col>=MAX_COLUMNS)throw new IllegalArgumentException("Excel column limit exceeded");while(row.size()<=col)row.add("");row.set(col,value==null?"":value);}
        public void endRow(int rowNum){out.accept(new RowData(sheet,name,rowNum,List.copyOf(row)));}
    }
    private static final class HSSFRows implements HSSFListener{
        private final Consumer<RowData> out;private final DataFormatter formatter=new DataFormatter(Locale.ROOT);private final List<String> sheetNames=new ArrayList<>();private FormatTrackingHSSFListener formatting;private SSTRecord strings;private int sheet=-1,rowNum=-1;private String sheetName;private List<String> row=new ArrayList<>();private int formulaRow=-1,formulaColumn=-1;
        HSSFRows(Consumer<RowData> out){this.out=out;}
        public void processRecord(org.apache.poi.hssf.record.Record rec){
            if(rec instanceof BoundSheetRecord b){sheetNames.add(b.getSheetname());return;}if(rec instanceof SSTRecord s){strings=s;return;}if(rec instanceof BOFRecord b&&b.getType()==BOFRecord.TYPE_WORKSHEET){finish();sheet++;sheetName=sheet<sheetNames.size()?sheetNames.get(sheet):"Sheet "+(sheet+1);out.accept(new RowData(sheet,sheetName,-1,List.of()));return;}
            if(rec instanceof LastCellOfRowDummyRecord last){if(last.getRow()==rowNum)finish();return;}if(rec instanceof MissingCellDummyRecord miss){put(miss.getRow(),miss.getColumn(),"");return;}
            if(rec instanceof LabelSSTRecord x){put(x.getRow(),x.getColumn(),strings==null?"":strings.getString(x.getSSTIndex()).getString());}
            else if(rec instanceof LabelRecord x)put(x.getRow(),x.getColumn(),x.getValue());
            else if(rec instanceof NumberRecord x)put(x.getRow(),x.getColumn(),formatting.formatNumberDateCell(x));
            else if(rec instanceof RKRecord x)put(x.getRow(),x.getColumn(),formatting.formatNumberDateCell(x));
            else if(rec instanceof BoolErrRecord x)put(x.getRow(),x.getColumn(),x.isBoolean()?Boolean.toString(x.getBooleanValue()):FormulaError.forInt(x.getErrorValue()).getString());
            else if(rec instanceof FormulaRecord x){switch(x.getCachedResultTypeEnum()){case STRING->{formulaRow=x.getRow();formulaColumn=x.getColumn();}case BOOLEAN->put(x.getRow(),x.getColumn(),Boolean.toString(x.getCachedBooleanValue()));case ERROR->put(x.getRow(),x.getColumn(),FormulaError.forInt(x.getCachedErrorValue()).getString());case BLANK->put(x.getRow(),x.getColumn(),"");default->put(x.getRow(),x.getColumn(),formatting.formatNumberDateCell(x));}}
            else if(rec instanceof StringRecord s&&formulaRow>=0){put(formulaRow,formulaColumn,s.getString());formulaRow=-1;formulaColumn=-1;}
            else if(rec instanceof MulRKRecord x){for(int i=0;i<x.getNumColumns();i++){int xf=x.getXFAt(i);put(x.getRow(),x.getFirstColumn()+i,formatter.formatRawCellContents(x.getRKNumberAt(i),xf,formatting.getFormatString(xf)));}}
            else if(rec instanceof MulBlankRecord x){for(int i=0;i<x.getNumColumns();i++)put(x.getRow(),x.getFirstColumn()+i,"");}
            else if(rec instanceof EOFRecord)finish();
        }
        private void put(int r,int c,String value){if(c<0||c>=MAX_COLUMNS)throw new IllegalArgumentException("Excel column limit exceeded");if(rowNum>=0&&r!=rowNum)finish();rowNum=r;while(row.size()<=c)row.add("");row.set(c,value==null?"":value);}
        void finish(){if(rowNum>=0){out.accept(new RowData(sheet,sheetName,rowNum,List.copyOf(row)));rowNum=-1;row=new ArrayList<>();}}
    }
}
