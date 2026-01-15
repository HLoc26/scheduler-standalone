package application.utils;

import application.models.*;
import application.repository.RepositoryOrchestrator;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

public class ExcelExporter {

    private final RepositoryOrchestrator repo;
    private final List<Clazz> classes;
    private final Map<String, Clazz> classMap;
    private final Map<String, Grade> gradeMap;
    private final Map<String, List<ScheduleItem>> classScheduleMap;
    private final Map<String, Subject> subjectMap;

    private final List<Teacher> teachers;
    private final Map<String, List<ScheduleItem>> teacherScheduleMap;

    // Styling
    private CellStyle metadataStyle;
    private CellStyle tableHeaderStyle;
    private CellStyle tableBodyStyle;
    private CellStyle dateStyle;


    public ExcelExporter(RepositoryOrchestrator repo) {
        this.repo = repo;
        this.classes = new ArrayList<>();
        this.classMap = new HashMap<>();
        this.gradeMap = new HashMap<>();
        this.classScheduleMap = new HashMap<>();
        this.subjectMap = new HashMap<>();
        this.teachers = new ArrayList<>();
        this.teacherScheduleMap = new HashMap<>();
    }

    public void export(String filePath, Date startDate) throws IOException{
        try (Workbook workbook = new XSSFWorkbook()){
            initializeStyles(workbook);
            Sheet classSheet = workbook.createSheet("TKB theo lớp");

            classSheet.setColumnWidth(0, 3 * 256); // set width to 3 chars
            for(int i = 1; i <= 6; i++){
                classSheet.setColumnWidth(i, 15 * 256);
            }

            int rowOffset = 6;
            for(Clazz clazz : classes){
                Grade grade = gradeMap.get(clazz.getGradeId());
                fillMetadata(classSheet, startDate, clazz, grade.getSession(), rowOffset);
                createTable(classSheet, rowOffset + 3); // move down 2 ro
                fillClassTable(classSheet, clazz, rowOffset + 4); // move down 1 row for header


                rowOffset += 10;
            }

            // Sheet 2: TKB giáo viên
            Sheet teacherSheet = workbook.createSheet("TKB giáo viên");
            teacherSheet.setColumnWidth(0, 3 * 256);
            for(int i = 1; i <= 6; i++){
                teacherSheet.setColumnWidth(i, 20 * 256);
            }

            rowOffset = 6;
            for(Teacher teacher : teachers) {
                // Morning
                fillTeacherMetadata(teacherSheet, startDate, teacher, rowOffset);
                createTable(teacherSheet, rowOffset + 3);
                fillTeacherTable(teacherSheet, teacher, ESession.MORNING, rowOffset + 4);

                rowOffset += 7;

                // Afternoon
                createTable(teacherSheet, rowOffset + 3);
                fillTeacherTable(teacherSheet, teacher, ESession.AFTERNOON, rowOffset + 4);

                rowOffset += 12;
            }

            try(FileOutputStream fileOut = new FileOutputStream(filePath)){
                workbook.write(fileOut);
            }
        }
    }

    public void prepareData(){
        List<Grade> grades = repo.getGradeRepository().getAll();
        classes.addAll(repo.getClassRepository().getAll());

        grades.forEach(g ->
            gradeMap.putIfAbsent(g.getId(), g)
        );

        classes.forEach(c -> {
            classMap.put(c.getId(), c);
            List<ScheduleItem> scheduleItems = repo.getScheduleRepository().getByClassId(c.getId());
            classScheduleMap.put(c.getId(), scheduleItems);
        });

        List<Subject> subjects = repo.getSubjectRepository().getAll();
        subjects.forEach(s -> subjectMap.putIfAbsent(s.getId(), s));

        teachers.addAll(repo.getTeacherRepository().getAll());
        teachers.forEach(t -> {
            List<ScheduleItem> items = repo.getScheduleRepository().getByTeacherId(t.getId());
            teacherScheduleMap.put(t.getId(), items);
        });
    }

    private void initializeStyles(Workbook workbook){
        tableHeaderStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setFontHeightInPoints((short) 13);
        headerFont.setBold(true);
        headerFont.setFontName("Times New Roman");
        tableHeaderStyle.setFont(headerFont);
        tableHeaderStyle.setFillForegroundColor(IndexedColors.AQUA.getIndex());
        tableHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        tableHeaderStyle.setBorderTop(BorderStyle.THIN);
        tableHeaderStyle.setBorderRight(BorderStyle.THIN);
        tableHeaderStyle.setBorderBottom(BorderStyle.THIN);
        tableHeaderStyle.setBorderLeft(BorderStyle.THIN);
        tableHeaderStyle.setAlignment(HorizontalAlignment.CENTER);
        tableHeaderStyle.setWrapText(false);
        tableHeaderStyle.setShrinkToFit(true);

        tableBodyStyle = workbook.createCellStyle();
        Font tableBodyFont = workbook.createFont();
        tableBodyFont.setFontName("Times New Roman");
        tableBodyFont.setBold(false);
        tableBodyFont.setFontHeightInPoints((short) 12);
        tableBodyStyle.setFont(tableBodyFont);
        tableBodyStyle.setBorderTop(BorderStyle.THIN);
        tableBodyStyle.setBorderRight(BorderStyle.THIN);
        tableBodyStyle.setBorderBottom(BorderStyle.THIN);
        tableBodyStyle.setBorderLeft(BorderStyle.THIN);
        tableBodyStyle.setAlignment(HorizontalAlignment.CENTER);

        metadataStyle = workbook.createCellStyle();
        Font metadataFont = workbook.createFont();
        metadataFont.setFontHeightInPoints((short) 12);
        metadataFont.setBold(true);
        metadataFont.setFontName("Times New Roman");
        metadataStyle.setFont(metadataFont);

        dateStyle = workbook.createCellStyle();
        dateStyle.cloneStyleFrom(metadataStyle);
        dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("dd/MM/yyyy"));

    }

    private void fillMetadata(Sheet sheet, Date startDate, Clazz clazz, Session session, int startRow){
        int classLabelCol = 2;
        int classNameCol = 3;
        int startDateLabelCol = 2; // span 2
        int startDateCol = 4;

        Row classLabelRow = sheet.getRow(startRow);
        if(classLabelRow == null) classLabelRow = sheet.createRow(startRow);

        Cell classLabelCell = classLabelRow.getCell(classLabelCol);
        if(classLabelCell == null) classLabelCell = classLabelRow.createCell(classLabelCol);
        classLabelCell.setCellValue("Lớp");
        classLabelCell.setCellStyle(metadataStyle);

        Cell classNameCell = classLabelRow.getCell(classNameCol);
        if(classNameCell == null) classNameCell = classLabelRow.createCell(classNameCol);
        classNameCell.setCellValue(clazz.getClassName());
        classNameCell.setCellStyle(metadataStyle);

        Row startDateRow = sheet.getRow(startRow + 1);
        if(startDateRow == null) startDateRow = sheet.createRow(startRow + 1);

        Cell startDateLabelCell = startDateRow.getCell(startDateLabelCol);
        if(startDateLabelCell == null) startDateLabelCell = startDateRow.createCell(startDateLabelCol);
        startDateLabelCell.setCellValue("Có hiệu lực từ ngày");
        startDateLabelCell.setCellStyle(metadataStyle);

        sheet.addMergedRegion(new CellRangeAddress(startRow + 1, startRow + 1, startDateLabelCol, startDateLabelCol  + 1));

        Cell startDateCell = startDateRow.getCell(startDateCol);
        if(startDateCell == null) startDateCell = startDateRow.createCell(startDateCol);
        startDateCell.setCellValue(startDate);
        startDateCell.setCellStyle(dateStyle);
    }

    private void fillTeacherMetadata(Sheet sheet, Date startDate, Teacher teacher, int startRow){
        int labelCol = 2;
        int nameCol = 3;
        int startDateLabelCol = 2; // span 2
        int startDateCol = 4;

        Row labelRow = sheet.getRow(startRow);
        if(labelRow == null) labelRow = sheet.createRow(startRow);

        Cell labelCell = labelRow.getCell(labelCol);
        if(labelCell == null) labelCell = labelRow.createCell(labelCol);
        labelCell.setCellValue("Giáo viên");
        labelCell.setCellStyle(metadataStyle);

        Cell nameCell = labelRow.getCell(nameCol);
        if(nameCell == null) nameCell = labelRow.createCell(nameCol);
        nameCell.setCellValue(teacher.getName());
        nameCell.setCellStyle(metadataStyle);

        Row startDateRow = sheet.getRow(startRow + 1);
        if(startDateRow == null) startDateRow = sheet.createRow(startRow + 1);

        Cell startDateLabelCell = startDateRow.getCell(startDateLabelCol);
        if(startDateLabelCell == null) startDateLabelCell = startDateRow.createCell(startDateLabelCol);
        startDateLabelCell.setCellValue("Có hiệu lực từ ngày");
        startDateLabelCell.setCellStyle(metadataStyle);

        sheet.addMergedRegion(new CellRangeAddress(startRow + 1, startRow + 1, startDateLabelCol, startDateLabelCol  + 1));

        Cell startDateCell = startDateRow.getCell(startDateCol);
        if(startDateCell == null) startDateCell = startDateRow.createCell(startDateCol);
        startDateCell.setCellValue(startDate);
        startDateCell.setCellStyle(dateStyle);
    }

    private void createTable(Sheet sheet, int headerRowPos){
        // Header
        final String[] headers = {"", "Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7"};

        Row headerRow = sheet.getRow(headerRowPos);
        if(headerRow == null) headerRow = sheet.createRow(headerRowPos);

        for(int i = 0; i < headers.length; i++){
            Cell cell = headerRow.getCell(i);
            if(cell == null) cell = headerRow.createCell(i);
            cell.setCellStyle(tableHeaderStyle);
            cell.setCellValue(headers[i]);
        }

        // Body
        for(int i = 1; i <= 5; i++){
            Row bodyRow = sheet.getRow(headerRowPos + i);
            if(bodyRow == null) bodyRow = sheet.createRow(headerRowPos + i);
            for(int j = 0; j < headers.length; j++){
                Cell cell = bodyRow.getCell(j);
                if(cell == null) cell = bodyRow.createCell(j);
                if(j == 0){
                    cell.setCellValue(i);
                    cell.setCellStyle(tableHeaderStyle);
                } else {
                    cell.setCellStyle(tableBodyStyle);
                }
            }
        }

    }

    private void fillClassTable(Sheet sheet, Clazz clazz, int firstRow){
        // fill session
        Session session = gradeMap.get(clazz.getGradeId()).getSession();
        fillSessionCell(sheet, session.getSessionName(),firstRow - 2);

        List<ScheduleItem> scheduleItems = classScheduleMap.get(clazz.getId());

        scheduleItems.sort(Comparator.comparingInt(ScheduleItem::period).thenComparing(ScheduleItem::day));

        for(ScheduleItem item : scheduleItems){
            int row = firstRow + item.period() - 1;
            int col = dayToIntMap(item.day());

            Row r = sheet.getRow(row);
            if(r == null) r = sheet.createRow(row);
            Cell c = r.getCell(col);
            if(c == null) c = r.createCell(col);
            Subject s = subjectMap.get(item.subjectId());
            c.setCellValue(s.toString().length() <= 10 ? s.toString() : s.getId());
        }

    }

    private void fillTeacherTable(Sheet sheet, Teacher teacher, ESession session, int firstRow){
        fillSessionCell(sheet, session, firstRow - 2); // 2 cells above, skip header

        List<ScheduleItem> scheduleItems = teacherScheduleMap.getOrDefault(teacher.getId(), Collections.emptyList());

        List<ScheduleItem> sessionItems = scheduleItems.stream()
                .filter(item -> item.session() == session)
                .sorted(Comparator.comparingInt(ScheduleItem::period).thenComparing(ScheduleItem::day))
                .toList();

        for(ScheduleItem item : sessionItems){
            int row = firstRow + item.period() - 1;
            int col = dayToIntMap(item.day());

            Row r = sheet.getRow(row);
            if(r == null) r = sheet.createRow(row);
            Cell c = r.getCell(col);
            if(c == null) c = r.createCell(col);

            Clazz clazz = classMap.get(item.classId());
            if(clazz != null) {
                Subject s = subjectMap.get(item.subjectId());
                String label = s.toString().length() <= 10 ? s.toString() : s.getId();
                c.setCellValue(clazz.getClassName() + " (" + label +")");
            }
        }
    }

    private void fillSessionCell(Sheet sheet, ESession session, int row){
        Row sessionRow = sheet.getRow(row);
        if(sessionRow == null) sessionRow = sheet.createRow(row);
        Cell sessionLabel = sessionRow.getCell(1);
        if(sessionLabel == null) sessionLabel = sessionRow.createCell(1);
        sessionLabel.setCellValue(session == ESession.MORNING ? "Buổi sáng" : "Buổi chiều");
        sessionLabel.setCellStyle(metadataStyle);
    }

    private int dayToIntMap(EWeekDay day){
        switch (day){
            case MONDAY -> {
                return 1;
            }
            case TUESDAY -> {
                return 2;
            }
            case WEDNESDAY -> {
                return 3;
            }
            case THURSDAY -> {
                return 4;
            }
            case FRIDAY -> {
                return 5;
            }
            case SATURDAY -> {
                return 6;
            }
            default -> throw new IllegalStateException("Unexpected value: " + day);
        }
    }
}
