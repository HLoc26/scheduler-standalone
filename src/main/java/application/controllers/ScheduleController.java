package application.controllers;

import application.models.*;
import application.repository.RepositoryOrchestrator;
import application.utils.ExcelExporter;
import application.utils.SwapperDataPreparer;
import application.services.SwapEngineService;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import scheduler.common.models.Slot;
import scheduler.common.models.EWeekDay;
import scheduler.common.models.ESession;
import scheduler.common.models.SwapEngineInput;
import scheduler.common.models.SwapEngineOutput;

import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

public class ScheduleController {

    private final RepositoryOrchestrator repo;
    private Runnable onReGenerateRequest;

    // --- FXML Fields for Sidebar ---
    @FXML
    private ToggleButton btnTabTeacher;
    @FXML
    private ToggleButton btnTabClass;
    @FXML
    private TextField txtSearch;
    @FXML
    private ListView<Object> listViewItems;

    // --- FXML Fields for Main View ---
    @FXML
    private ScrollPane scrollPaneSchedule; // To hide/show the schedule
    @FXML
    private VBox placeholderView;          // To show when no item is selected
    @FXML
    private GridPane scheduleGrid;

    // Data for filtering
    private FilteredList<Object> filteredData;

    public ScheduleController(RepositoryOrchestrator repo) {
        this.repo = repo;
    }

    public void initialize() {
        initGridStructure();
        setupSidebar();
        
        // Ensure Grid is interactive
        scheduleGrid.setPickOnBounds(true);
        scheduleGrid.setMouseTransparent(false);
    }

    /**
     * Sets up the Sidebar logic: CellFactory, Selection Listener, and Search Listener.
     */
    private void setupSidebar() {
        // 1. Custom CellFactory to display proper names for Teacher or Class objects
        listViewItems.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    if (item instanceof Clazz) {
                        setText(((Clazz) item).getClassName());
                    } else if (item instanceof Teacher) {
                        setText(((Teacher) item).getName());
                    } else {
                        setText(item.toString());
                    }
                }
            }
        });

        // 2. Handle Item Selection
        listViewItems.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                // Show Schedule, Hide Placeholder
                placeholderView.setVisible(false);
                scrollPaneSchedule.setVisible(true);
                renderSchedule(newVal);
            } else {
                // Hide Schedule, Show Placeholder
                placeholderView.setVisible(true);
                scrollPaneSchedule.setVisible(false);
            }
        });

        // 3. Handle Search/Filtering
        txtSearch.textProperty().addListener((obs, oldVal, newVal) -> {
            if (filteredData != null) {
                filteredData.setPredicate(item -> {
                    // If filter text is empty, display all
                    if (newVal == null || newVal.isEmpty()) return true;

                    String lowerCaseFilter = newVal.toLowerCase();
                    String content = "";

                    if (item instanceof Clazz) content = ((Clazz) item).getClassName();
                    else if (item instanceof Teacher) content = ((Teacher) item).getName();

                    return content.toLowerCase().contains(lowerCaseFilter);
                });
            }
        });

        // 4. Initial Load (Default to Teacher tab)
        loadSidebarData("teacher");
    }

    /**
     * Triggered when Teacher or Class tab is clicked.
     */
    @FXML
    public void onTabChanged() {
        txtSearch.clear(); // Clear search when switching tabs
        if (btnTabTeacher.isSelected()) {
            loadSidebarData("teacher");
        } else {
            loadSidebarData("class");
        }
    }

    /**
     * Loads data into the Sidebar ListView based on the selected mode.
     */
    @SuppressWarnings("unchecked")
    private void loadSidebarData(String type) {
        List<Object> data;
        if ("teacher".equals(type)) {
            data = (List<Object>) (List<?>) repo.getTeacherRepository().getAll();
        } else {
            data = (List<Object>) (List<?>) repo.getClassRepository().getAll();
        }

        // Wrap data in FilteredList for search functionality
        filteredData = new FilteredList<>(FXCollections.observableArrayList(data), p -> true);
        listViewItems.setItems(filteredData);

        // Auto-select the first item if data exists (UI UX improvement)
        if (!filteredData.isEmpty()) {
            listViewItems.getSelectionModel().selectFirst();
        } else {
            listViewItems.getSelectionModel().clearSelection();
        }
    }

    /**
     * Draws the static grid headers (Days of week, Periods, Lunch break).
     */
    private void initGridStructure() {
        String[] days = {"Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7"};

        // Draw Column Headers (Days)
        for (int i = 0; i < days.length; i++) {
            Label lbl = new Label(days[i]);
            lbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #2c3e50;");
            StackPane cell = new StackPane(lbl);
            cell.setStyle("-fx-background-color: #ecf0f1;");
            scheduleGrid.add(cell, i + 1, 0);
        }

        // Draw Row Headers (Periods)
        for (int i = 1; i <= 10; i++) {
            Label lbl = new Label("Tiết " + i);
            lbl.setStyle("-fx-text-fill: #7f8c8d;");
            StackPane cell = new StackPane(lbl);
            cell.setStyle("-fx-background-color: #fff;");
            int rowIndex = (i <= 5) ? i : i + 1; // Skip row 6 (Lunch)
            scheduleGrid.add(cell, 0, rowIndex);
        }

        // Draw Lunch Break Row
        Label lblBreak = new Label("NGHỈ TRƯA");
        lblBreak.setStyle("-fx-font-weight: bold; -fx-text-fill: white; -fx-font-size: 15px; -fx-letter-spacing: 2px;");
        StackPane breakCell = new StackPane(lblBreak);
        breakCell.setStyle("-fx-background-color: #bdc3c7;");
        scheduleGrid.add(breakCell, 0, 6, 7, 1);
    }

    /**
     * Renders the schedule for the selected Teacher or Class.
     */
    private void renderSchedule(Object filterEntity) {
        // 1. Clear old data (Keep headers: Row 0, Col 0, and Lunch Row 6)
        scheduleGrid.getChildren().removeIf(node -> {
            Integer r = GridPane.getRowIndex(node);
            Integer c = GridPane.getColumnIndex(node);
            if (r == null || c == null) return false;
            boolean isHeader = (r == 0) || (c == 0) || (r == 6);
            return !isHeader;
        });

        // 2. Fill empty slots with white background (Crucial for "Gap Technique" borders)
        fillEmptySlots();

        // 3. Fetch lessons
        List<ScheduleItem> lessons;
        if (filterEntity instanceof Clazz) {
            lessons = repo.getScheduleRepository().getByClassId(((Clazz) filterEntity).getId());
        } else if (filterEntity instanceof Teacher) {
            lessons = repo.getScheduleRepository().getByTeacherId(((Teacher) filterEntity).getId());
        } else {
            return;
        }

        // 4. Sort by day, then by period
        lessons.sort(Comparator.comparing(ScheduleItem::day)
                .thenComparingInt(ScheduleItem::period));

        // 5. Draw each lesson
        for (int i = 0; i < lessons.size(); i++) {
            ScheduleItem item = lessons.get(i);

            Clazz c = repo.getClassRepository().getById(item.classId());
            String className = (c != null) ? c.getClassName() : "Unknown";

            ESession session = ESession.MORNING; // Default
            if (c != null) {
                Grade g = repo.getGradeRepository().getById(c.getGradeId());
                if (g != null) {
                    session = g.getSession().getSessionName();
                }
            }
            int dayInt = item.day().ordinal() + 2;

            // Get Subject Name
            String subjectName = item.subjectId();
            Subject subject = repo.getSubjectRepository().getById(item.subjectId());

            // Get Teacher Name
            String teacherName = "";
            Teacher t = repo.getTeacherRepository().getById(item.teacherId());
            if (t != null) teacherName = t.getName();

            // Detect double period (consecutive)
            boolean isDouble = false;
            if (i > 0) {
                ScheduleItem prev = lessons.get(i - 1);
                if (isConsecutive(prev, item)) isDouble = true;
            }
            if (i < lessons.size() - 1) {
                ScheduleItem next = lessons.get(i + 1);
                if (isConsecutive(item, next)) isDouble = true;
            }

            drawLessonCell(dayInt, item.period(), subject, teacherName, className, isDouble, session, item);
        }
    }

    /**
     * Fills all valid slots with a white Pane to create the "grid" effect using gaps.
     */
    private void fillEmptySlots() {
        int days = 6; // Mon-Sat
        // Periods: Morning (1-5) and Afternoon (7-11). Row 6 is excluded (Lunch).
        int[] periods = {1, 2, 3, 4, 5, 7, 8, 9, 10, 11};

        for (int col = 1; col <= days; col++) {
            for (int row : periods) {
                Pane emptyCell = new Pane();
                // Just white background. No borders.
                emptyCell.setStyle("-fx-background-color: white;");
                emptyCell.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
                emptyCell.setUserData("empty"); // Mark as empty slot
                
                makeDroppable(emptyCell);
                scheduleGrid.add(emptyCell, col, row);
            }
        }
    }

    /**
     * Draws a specific lesson cell.
     */
    private void drawLessonCell(int day, int period, Subject subject, String teacher, String className, boolean isDouble, ESession session, ScheduleItem item) {

        int colIndex = day - 1; // Col 1 -> Monday
        int rowIndex;
        if (session == ESession.MORNING) {
            rowIndex = period;
        } else {
            rowIndex = period + 6;
        }
        
        // Remove any empty slot pane at this position to avoid z-order issues
        removePaneAt(colIndex, rowIndex);

        VBox cell = new VBox(2);
        cell.setAlignment(Pos.CENTER);
        cell.setUserData(new CellData(item, subject.getName())); // Store full item and subject name

        Label lblSub = new Label(subject.getName());
        lblSub.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #2c3e50;");
        lblSub.setMouseTransparent(true);

        Label lblInfo = new Label();
        // Determine what to show based on the active tab
        boolean viewingByClass = btnTabClass.isSelected(); // If "Class" tab is active, we show Teacher name in cell
        lblInfo.setText(viewingByClass ? teacher : className);
        lblInfo.setStyle("-fx-font-size: 13px; -fx-text-fill: #7f8c8d;");
        lblInfo.setMouseTransparent(true);

        cell.getChildren().addAll(lblSub, lblInfo);

        // Determine Background Color
        String bgStyle = "-fx-background-color: #f5f5f5;";
        if (isDouble) bgStyle = "-fx-background-color: #d4efdf;"; // Light mint
        if (Constants.SPECIAL_SUBJECTS.contains(subject.getId()))
            bgStyle = "-fx-background-color: #fadbd8;"; // Light red

        // Apply style (Background ONLY, no border)
        cell.setStyle(bgStyle);

        // Force cell to fill the grid slot
        cell.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        cell.setPickOnBounds(true); // Ensure clicks are registered
        cell.setMouseTransparent(false);

        makeDraggable(cell, subject);
        makeDroppable(cell);

        // Add to grid
        scheduleGrid.add(cell, colIndex, rowIndex);
        cell.toFront(); // Ensure it's on top
    }
    
    private void removePaneAt(int col, int row) {
        scheduleGrid.getChildren().removeIf(node -> {
            Integer r = GridPane.getRowIndex(node);
            Integer c = GridPane.getColumnIndex(node);
            // Remove if it matches coordinates AND is marked as "empty"
            return r != null && c != null && r == row && c == col && "empty".equals(node.getUserData());
        });
    }

    // Helper to check if two items are consecutive
    private boolean isConsecutive(ScheduleItem a, ScheduleItem b) {
        return a.day() == b.day()
                && Math.abs(a.period() - b.period()) == 1
                && Objects.equals(a.subjectId(), b.subjectId())
                && Objects.equals(a.classId(), b.classId());
    }

    @FXML
    public void handleReGenerate() {
        if (onReGenerateRequest != null) {
            onReGenerateRequest.run();
        }
    }

    public void setOnReGenerateRequest(Runnable onReGenerateRequest) {
        this.onReGenerateRequest = onReGenerateRequest;
    }

    @FXML
    public void handleExportExcel() {
        // 1. Ask for Start Date
        Dialog<LocalDate> dialog = new Dialog<>();
        dialog.setTitle("Chọn ngày bắt đầu");
        dialog.setHeaderText("Vui lòng chọn ngày bắt đầu áp dụng thời khóa biểu");

        ButtonType loginButtonType = new ButtonType("Tiếp tục", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        DatePicker datePicker = new DatePicker(LocalDate.now());
        grid.add(new Label("Ngày bắt đầu:"), 0, 0);
        grid.add(datePicker, 1, 0);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == loginButtonType) {
                return datePicker.getValue();
            }
            return null;
        });

        Optional<LocalDate> result = dialog.showAndWait();

        result.ifPresent(localDate -> {
            // 2. Choose File
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Lưu file Excel");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
            fileChooser.setInitialFileName("ThoiKhoaBieu.xlsx");

            File file = fileChooser.showSaveDialog(scheduleGrid.getScene().getWindow());
            if (file != null) {
                try {
                    ExcelExporter exporter = new ExcelExporter(repo);
                    exporter.prepareData();
                    Date date = Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
                    exporter.export(file.getAbsolutePath(), date);

                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Thành công");
                    alert.setHeaderText(null);
                    alert.setContentText("Xuất file Excel thành công!");
                    alert.showAndWait();
                } catch (Exception e) {
                    e.printStackTrace();
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Lỗi");
                    alert.setHeaderText("Không thể xuất file");
                    alert.setContentText(e.getMessage());
                    alert.showAndWait();
                }
            }
        });
    }

    private void makeDraggable(Node node, Subject subject) {
        node.setOnMousePressed(event -> {
            event.consume(); // Prevent ScrollPane from stealing the event
        });

        node.setOnDragDetected(event -> {
            if ( Constants.SPECIAL_SUBJECTS.contains(subject.getId())) {
                showRestrictionAlert(subject.getName());
                event.consume();
                return;
            }

            Dragboard db = node.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString("lesson");
            db.setContent(content);
            event.consume();
        });

        node.setOnDragDone(event -> {
            event.consume();
        });
    }

    private void makeDroppable(Node target) {
        target.setOnDragOver(event -> {
            if (event.getGestureSource() != target && event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });

        target.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasString()) {
                // Check if target is a restricted lesson
                if (isRestrictedTarget(target)) {
                    showRestrictionAlert("tiết cố định (Chào cờ / SH Lớp)");
                    event.setDropCompleted(false);
                    event.consume();
                    return;
                }

                Node source = (Node) event.getGestureSource();
                
                // Get Source Item
                CellData sourceData = (CellData) source.getUserData();
                ScheduleItem sourceItem = sourceData.item();
                
                // Get Target Slot Info
                Integer targetRow = GridPane.getRowIndex(target);
                Integer targetCol = GridPane.getColumnIndex(target);
                
                if (targetRow != null && targetCol != null) {
                    // Convert grid coords to Slot info
                    EWeekDay targetDay = EWeekDay.values()[targetCol - 1];
                    ESession targetSession = (targetRow <= 5) ? ESession.MORNING : ESession.AFTERNOON;
                    int targetPeriod = (targetRow <= 5) ? targetRow : targetRow - 6;
                    Slot targetSlot = new Slot(targetDay, targetSession, targetPeriod);
                    
                    // Handle Logic
                    handleManualScheduleUpdate(sourceItem, targetSlot);
                    success = true;
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void handleManualScheduleUpdate(ScheduleItem source, Slot targetSlot) {
        // 1. Validate Teacher Availability (Static & External)
        Teacher t = repo.getTeacherRepository().getById(source.teacherId());
        if (t != null) {
            int pIndex = (targetSlot.session() == ESession.MORNING) ? (targetSlot.period() - 1) : (targetSlot.period() + 5 - 1);
            if (t.getBusyMatrix()[targetSlot.day().ordinal()][pIndex]) {
                 showAlert("Giáo viên bận", "Giáo viên " + t.getName() + " bận (Lịch cá nhân) vào thời gian này.");
                 return;
            }
        }

        if (repo.getScheduleRepository().isTeacherBusyExceptClass(source.teacherId(), source.classId(), targetSlot.day(), targetSlot.session(), targetSlot.period())) {
             showAlert("Giáo viên bận", "Giáo viên " + (t!=null?t.getName():source.teacherId()) + " đang dạy lớp khác vào thời gian này.");
             return;
        }

        // 2. Run Swap Engine Service
        SwapperDataPreparer solver = new SwapperDataPreparer(repo);
        SwapEngineInput input = solver.prepareInput(source.classId(), source, targetSlot);

        SwapEngineService service = new SwapEngineService();
        service.setInputData(input);

        Alert loadingAlert = new Alert(Alert.AlertType.INFORMATION);
        loadingAlert.setTitle("Đang xử lý");
        loadingAlert.setHeaderText("Đang tìm phương án hoán đổi...");
        loadingAlert.setContentText("Vui lòng đợi...");
        loadingAlert.getDialogPane().lookupButton(ButtonType.OK).setVisible(false);
        loadingAlert.show();

        service.setOnSucceeded(event -> {
            loadingAlert.close();
            SwapEngineOutput output = service.getValue();
            if (output != null && output.success()) {
                showConfirmationDialog(output.changes());
            } else {
                showAlert("Không thể di chuyển", "Không tìm được phương án hoán đổi hợp lệ (Ràng buộc giáo viên hoặc môn học).");
            }
        });

        service.setOnFailed(event -> {
            loadingAlert.close();
            Throwable ex = service.getException();
            ex.printStackTrace();
            showAlert("Lỗi", "Lỗi khi chạy engine: " + ex.getMessage());
        });

        service.start();
    }

    private void showConfirmationDialog(Map<Integer, Slot> changes) {
        // 3. Show Confirmation with Chain Reaction Details
        StringBuilder msg = new StringBuilder();
        msg.append("Xác nhận thay đổi lịch (Tìm thấy phương án tối ưu):\n\n");
        
        for (Map.Entry<Integer, Slot> entry : changes.entrySet()) {
            // Find item name (Need to query DB or pass map, but for now let's just show count or simple info)
            // Ideally we should map ID back to Subject Name
            // For simplicity in this prompt context, we just list the moves.
            ScheduleItem item = repo.getScheduleRepository().getById(entry.getKey());
            String subjectName = "Unknown";
            if (item != null) {
                Subject s = repo.getSubjectRepository().getById(item.subjectId());
                if (s != null) {
                    subjectName = s.getName();
                } else {
                    subjectName = item.subjectId();
                }
            }
            
            msg.append("- [").append(subjectName).append("] sẽ chuyển sang ").append(entry.getValue().day()).append(" - Tiết ").append(entry.getValue().period()).append("\n");
        }
        
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận thay đổi");
        confirm.setHeaderText("Phát hiện thay đổi lịch học (Chuỗi hoán đổi)");
        confirm.setContentText(msg.toString());
        
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            // 4. Update DB
            for (Map.Entry<Integer, Slot> entry : changes.entrySet()) {
                repo.getScheduleRepository().updateSlot(entry.getKey(), entry.getValue().day(), entry.getValue().session(), entry.getValue().period());
            }
            
            // 5. Refresh UI
            Object selected = listViewItems.getSelectionModel().getSelectedItem();
            if (selected != null) {
                renderSchedule(selected);
            }
        }
    }
    
    private boolean isRestrictedTarget(Node target) {
        if (target instanceof VBox) {
            Object data = target.getUserData();
            if (data instanceof CellData) {
                String s = ((CellData) data).item.subjectId();
                return Constants.SPECIAL_SUBJECTS.contains(s);
            }
        }
        return false;
    }

    private void showRestrictionAlert(String subjectName) {
        showAlert("Hạn chế", "Không thể di chuyển " + subjectName);
    }
    
    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.show();
    }
    
    // Inner record to store cell data
    record CellData(ScheduleItem item, String subjectName) {}
}
