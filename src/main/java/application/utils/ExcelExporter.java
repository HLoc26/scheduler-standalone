package application.utils;

import application.models.*;
import application.repository.RepositoryOrchestrator;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.RegionUtil;
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
    private final Map<String, Teacher> teacherMap;
    private final Map<String, List<ScheduleItem>> teacherScheduleMap;

    // Styling
    private CellStyle metadataStyle;
    private CellStyle tableHeaderStyle;
    private CellStyle tableBodyStyle;
    private CellStyle dateStyle;

    private CellStyle oddDayStyle;
    private CellStyle oddDaySeparatorStyle;
    private CellStyle evenDayStyle;
    private CellStyle evenDaySeparatorStyle;


    public ExcelExporter(RepositoryOrchestrator repo) {
        this.repo = repo;
        this.classes = new ArrayList<>();
        this.classMap = new HashMap<>();
        this.gradeMap = new HashMap<>();
        this.classScheduleMap = new HashMap<>();
        this.subjectMap = new HashMap<>();
        this.teachers = new ArrayList<>();
        this.teacherMap = new HashMap<>();
        this.teacherScheduleMap = new HashMap<>();
    }

    public void export(String filePath, Date startDate) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            initializeStyles(workbook);
            Sheet classSheet = workbook.createSheet("TKB theo lớp");

            classSheet.setColumnWidth(0, 3 * 256); // set width to 3 chars
            for (int i = 1; i <= 6; i++) {
                classSheet.setColumnWidth(i, 15 * 256);
            }

            int rowOffset = 6;
            for (Clazz clazz : classes) {
                Grade grade = gradeMap.get(clazz.getGradeId());
                fillMetadata(classSheet, startDate, clazz, grade.getSession(), rowOffset);
                createTable(classSheet, rowOffset + 3); // move down 2 ro
                fillClassTable(classSheet, clazz, rowOffset + 4); // move down 1 row for header


                rowOffset += 10;
            }

            // Sheet 2: TKB giáo viên
            Sheet teacherSheet = workbook.createSheet("TKB giáo viên");
            teacherSheet.setColumnWidth(0, 3 * 256);
            for (int i = 1; i <= 6; i++) {
                teacherSheet.setColumnWidth(i, 20 * 256);
            }

            rowOffset = 6;
            for (Teacher teacher : teachers) {
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

            // Sheet per Grade
            List<Grade> grades = new ArrayList<>(gradeMap.values());
            grades.sort(Comparator.comparingInt(Grade::getLevel));

            for (Grade grade : grades) {
                Sheet gradeSheet = workbook.createSheet(grade.getName());
                fillGradeSheet(gradeSheet, grade);
            }

            try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
                workbook.write(fileOut);
            }
        }
    }

    public void prepareData() {
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
            teacherMap.put(t.getId(), t);
            List<ScheduleItem> items = repo.getScheduleRepository().getByTeacherId(t.getId());
            teacherScheduleMap.put(t.getId(), items);
        });
    }

    private void initializeStyles(Workbook workbook) {
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
        tableBodyStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        tableBodyStyle.setWrapText(true);

        metadataStyle = workbook.createCellStyle();
        Font metadataFont = workbook.createFont();
        metadataFont.setFontHeightInPoints((short) 12);
        metadataFont.setBold(true);
        metadataFont.setFontName("Times New Roman");
        metadataStyle.setFont(metadataFont);

        dateStyle = workbook.createCellStyle();
        dateStyle.cloneStyleFrom(metadataStyle);
        dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("dd/MM/yyyy"));

        // Odd Day Styles (White)
        oddDayStyle = workbook.createCellStyle();
        oddDayStyle.cloneStyleFrom(tableBodyStyle);
        oddDayStyle.setFillForegroundColor(IndexedColors.LEMON_CHIFFON.getIndex());
        oddDayStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        oddDaySeparatorStyle = workbook.createCellStyle();
        oddDaySeparatorStyle.cloneStyleFrom(oddDayStyle);
        oddDaySeparatorStyle.setBorderBottom(BorderStyle.MEDIUM);

        // Even Day Styles (Light Yellow)
        evenDayStyle = workbook.createCellStyle();
        evenDayStyle.cloneStyleFrom(tableBodyStyle);
        evenDayStyle.setFillForegroundColor(IndexedColors.LIGHT_TURQUOISE.getIndex());
        evenDayStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        evenDaySeparatorStyle = workbook.createCellStyle();
        evenDaySeparatorStyle.cloneStyleFrom(evenDayStyle);
        evenDaySeparatorStyle.setBorderBottom(BorderStyle.MEDIUM);
    }

    private void fillMetadata(Sheet sheet, Date startDate, Clazz clazz, Session session, int startRow) {
        int classLabelCol = 2;
        int classNameCol = 3;
        int startDateLabelCol = 2; // span 2
        int startDateCol = 4;

        Row classLabelRow = sheet.getRow(startRow);
        if (classLabelRow == null) classLabelRow = sheet.createRow(startRow);

        Cell classLabelCell = classLabelRow.getCell(classLabelCol);
        if (classLabelCell == null) classLabelCell = classLabelRow.createCell(classLabelCol);
        classLabelCell.setCellValue("Lớp");
        classLabelCell.setCellStyle(metadataStyle);

        Cell classNameCell = classLabelRow.getCell(classNameCol);
        if (classNameCell == null) classNameCell = classLabelRow.createCell(classNameCol);
        classNameCell.setCellValue(clazz.getClassName());
        classNameCell.setCellStyle(metadataStyle);

        Row startDateRow = sheet.getRow(startRow + 1);
        if (startDateRow == null) startDateRow = sheet.createRow(startRow + 1);

        Cell startDateLabelCell = startDateRow.getCell(startDateLabelCol);
        if (startDateLabelCell == null) startDateLabelCell = startDateRow.createCell(startDateLabelCol);
        startDateLabelCell.setCellValue("Có hiệu lực từ ngày");
        startDateLabelCell.setCellStyle(metadataStyle);

        sheet.addMergedRegion(new CellRangeAddress(startRow + 1, startRow + 1, startDateLabelCol, startDateLabelCol + 1));

        Cell startDateCell = startDateRow.getCell(startDateCol);
        if (startDateCell == null) startDateCell = startDateRow.createCell(startDateCol);
        startDateCell.setCellValue(startDate);
        startDateCell.setCellStyle(dateStyle);
    }

    private void fillTeacherMetadata(Sheet sheet, Date startDate, Teacher teacher, int startRow) {
        int labelCol = 2;
        int nameCol = 3;
        int startDateLabelCol = 2; // span 2
        int startDateCol = 4;

        Row labelRow = sheet.getRow(startRow);
        if (labelRow == null) labelRow = sheet.createRow(startRow);

        Cell labelCell = labelRow.getCell(labelCol);
        if (labelCell == null) labelCell = labelRow.createCell(labelCol);
        labelCell.setCellValue("Giáo viên");
        labelCell.setCellStyle(metadataStyle);

        Cell nameCell = labelRow.getCell(nameCol);
        if (nameCell == null) nameCell = labelRow.createCell(nameCol);
        nameCell.setCellValue(teacher.getName());
        nameCell.setCellStyle(metadataStyle);

        Row startDateRow = sheet.getRow(startRow + 1);
        if (startDateRow == null) startDateRow = sheet.createRow(startRow + 1);

        Cell startDateLabelCell = startDateRow.getCell(startDateLabelCol);
        if (startDateLabelCell == null) startDateLabelCell = startDateRow.createCell(startDateLabelCol);
        startDateLabelCell.setCellValue("Có hiệu lực từ ngày");
        startDateLabelCell.setCellStyle(metadataStyle);

        sheet.addMergedRegion(new CellRangeAddress(startRow + 1, startRow + 1, startDateLabelCol, startDateLabelCol + 1));

        Cell startDateCell = startDateRow.getCell(startDateCol);
        if (startDateCell == null) startDateCell = startDateRow.createCell(startDateCol);
        startDateCell.setCellValue(startDate);
        startDateCell.setCellStyle(dateStyle);
    }

    private void createTable(Sheet sheet, int headerRowPos) {
        // Header
        final String[] headers = {"", "Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7"};

        Row headerRow = sheet.getRow(headerRowPos);
        if (headerRow == null) headerRow = sheet.createRow(headerRowPos);

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.getCell(i);
            if (cell == null) cell = headerRow.createCell(i);
            cell.setCellStyle(tableHeaderStyle);
            cell.setCellValue(headers[i]);
        }

        // Body
        for (int i = 1; i <= 5; i++) {
            Row bodyRow = sheet.getRow(headerRowPos + i);
            if (bodyRow == null) bodyRow = sheet.createRow(headerRowPos + i);
            for (int j = 0; j < headers.length; j++) {
                Cell cell = bodyRow.getCell(j);
                if (cell == null) cell = bodyRow.createCell(j);
                if (j == 0) {
                    cell.setCellValue(i);
                    cell.setCellStyle(tableHeaderStyle);
                } else {
                    cell.setCellStyle(tableBodyStyle);
                }
            }
        }

    }

    private void fillClassTable(Sheet sheet, Clazz clazz, int firstRow) {
        // fill session
        Session session = gradeMap.get(clazz.getGradeId()).getSession();
        fillSessionCell(sheet, session.getSessionName(), firstRow - 2);

        List<ScheduleItem> scheduleItems = classScheduleMap.get(clazz.getId());

        scheduleItems.sort(Comparator.comparingInt(ScheduleItem::period).thenComparing(ScheduleItem::day));

        for (ScheduleItem item : scheduleItems) {
            int row = firstRow + item.period() - 1;
            int col = dayToIntMap(item.day());

            Row r = sheet.getRow(row);
            if (r == null) r = sheet.createRow(row);
            Cell c = r.getCell(col);
            if (c == null) c = r.createCell(col);
            Subject s = subjectMap.get(item.subjectId());
            c.setCellValue(s.toString().length() <= 10 ? s.toString() : s.getId());
        }

    }

    private void fillTeacherTable(Sheet sheet, Teacher teacher, ESession session, int firstRow) {
        fillSessionCell(sheet, session, firstRow - 2); // 2 cells above, skip header

        List<ScheduleItem> scheduleItems = teacherScheduleMap.getOrDefault(teacher.getId(), Collections.emptyList());

        List<ScheduleItem> sessionItems = scheduleItems.stream()
                .filter(item -> item.session() == session)
                .sorted(Comparator.comparingInt(ScheduleItem::period).thenComparing(ScheduleItem::day))
                .toList();

        for (ScheduleItem item : sessionItems) {
            int row = firstRow + item.period() - 1;
            int col = dayToIntMap(item.day());

            Row r = sheet.getRow(row);
            if (r == null) r = sheet.createRow(row);
            Cell c = r.getCell(col);
            if (c == null) c = r.createCell(col);

            Clazz clazz = classMap.get(item.classId());
            if (clazz != null) {
                Subject s = subjectMap.get(item.subjectId());
                String label = s.toString().length() <= 10 ? s.toString() : s.getId();
                c.setCellValue(clazz.getClassName() + " (" + label + ")");
            }
        }
    }

    private void fillSessionCell(Sheet sheet, ESession session, int row) {
        Row sessionRow = sheet.getRow(row);
        if (sessionRow == null) sessionRow = sheet.createRow(row);
        Cell sessionLabel = sessionRow.getCell(1);
        if (sessionLabel == null) sessionLabel = sessionRow.createCell(1);
        sessionLabel.setCellValue(session == ESession.MORNING ? "Buổi sáng" : "Buổi chiều");
        sessionLabel.setCellStyle(metadataStyle);
    }

    private void fillGradeSheet(Sheet sheet, Grade grade) {
        List<Clazz> gradeClasses = classes.stream()
                .filter(c -> c.getGradeId().equals(grade.getId()))
                .sorted(Comparator.comparing(Clazz::getClassName))
                .toList();

        if (gradeClasses.isEmpty()) return;

        // Column widths
        sheet.setColumnWidth(0, 10 * 256); // Day
        sheet.setColumnWidth(1, 5 * 256);  // Period
        for (int i = 0; i < gradeClasses.size(); i++) {
            sheet.setColumnWidth(i + 2, 20 * 256);
        }

        // Header
        Row headerRow = sheet.createRow(0);
        Cell dayHeader = headerRow.createCell(0);
        dayHeader.setCellValue("Thứ");
        dayHeader.setCellStyle(tableHeaderStyle);

        Cell periodHeader = headerRow.createCell(1);
        periodHeader.setCellValue("Tiết");
        periodHeader.setCellStyle(tableHeaderStyle);

        for (int i = 0; i < gradeClasses.size(); i++) {
            Cell classHeader = headerRow.createCell(i + 2);
            classHeader.setCellValue(gradeClasses.get(i).getClassName());
            classHeader.setCellStyle(tableHeaderStyle);
        }

        // Create grid
        int startRow = 1;
        EWeekDay[] days = EWeekDay.values();
        int currentRow = startRow;
        int dayCounter = 0;

        for (EWeekDay day : days) {
            int dayStartRow = currentRow;
            boolean isEvenDay = (dayCounter % 2 != 0);
            dayCounter++;

            for (int p = 1; p <= 5; p++) {
                Row row = sheet.createRow(currentRow);

                // Determine style for this row
                CellStyle currentStyle;
                if (isEvenDay) {
                    currentStyle = (p == 5) ? evenDaySeparatorStyle : evenDayStyle;
                } else {
                    currentStyle = (p == 5) ? oddDaySeparatorStyle : oddDayStyle;
                }

                // Day cell
                Cell dayCell = row.createCell(0);
                if (p == 1) {
                    dayCell.setCellValue(getDayName(day));
                }
                dayCell.setCellStyle(currentStyle);

                // Period cell
                Cell periodCell = row.createCell(1);
                periodCell.setCellValue(p);
                periodCell.setCellStyle(currentStyle);

                // Initialize class cells
                for (int i = 0; i < gradeClasses.size(); i++) {
                    Cell cell = row.createCell(i + 2);
                    cell.setCellStyle(currentStyle);
                }
                currentRow++;
            }
            // Merge day cells
            sheet.addMergedRegion(new CellRangeAddress(dayStartRow, currentRow - 1, 0, 0));
        }

        // Outer Border
        CellRangeAddress region = new CellRangeAddress(0, currentRow - 1, 0, gradeClasses.size() + 1);
        RegionUtil.setBorderTop(BorderStyle.THICK, region, sheet);
        RegionUtil.setBorderBottom(BorderStyle.THICK, region, sheet);
        RegionUtil.setBorderLeft(BorderStyle.THICK, region, sheet);
        RegionUtil.setBorderRight(BorderStyle.THICK, region, sheet);

        // Fill data
        ESession gradeSession = grade.getSession().getSessionName();
        for (int i = 0; i < gradeClasses.size(); i++) {
            Clazz clazz = gradeClasses.get(i);
            List<ScheduleItem> items = classScheduleMap.get(clazz.getId());
            if (items == null) continue;

            for (ScheduleItem item : items) {
                if (item.session() != gradeSession) continue;

                int dayIndex = item.day().ordinal();
                int period = item.period();
                if (period < 1 || period > 5) continue;

                int rowIndex = startRow + (dayIndex * 5) + (period - 1);
                int colIndex = i + 2;

                Row row = sheet.getRow(rowIndex);
                if (row != null) {
                    Cell cell = row.getCell(colIndex);
                    if (cell != null) {
                        Subject s = subjectMap.get(item.subjectId());
                        Teacher t = teacherMap.get(item.teacherId());
                        if (s != null) {
                            String subjectName = s.toString().length() <= 10 ? s.toString() : s.getId();
                            String teacherName = t != null ? t.getName() : "";
                            String cellValue = subjectName;
                            if (!teacherName.isEmpty()) {
                                cellValue += " - " + teacherName;
                            }
                            cell.setCellValue(cellValue);
                        }
                    }
                }
            }
        }
    }

    private String getDayName(EWeekDay day) {
        switch (day) {
            case MONDAY:
                return "Thứ 2";
            case TUESDAY:
                return "Thứ 3";
            case WEDNESDAY:
                return "Thứ 4";
            case THURSDAY:
                return "Thứ 5";
            case FRIDAY:
                return "Thứ 6";
            case SATURDAY:
                return "Thứ 7";
            default:
                return "";
        }
    }

    private int dayToIntMap(EWeekDay day) {
        switch (day) {
            case MONDAY:
                return 1;
            case TUESDAY:
                return 2;
            case WEDNESDAY:
                return 3;
            case THURSDAY:
                return 4;
            case FRIDAY:
                return 5;
            case SATURDAY:
                return 6;
            default:
                throw new IllegalStateException("Unexpected value: " + day);
        }
    }
}
