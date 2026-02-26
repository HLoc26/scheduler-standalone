package application.controllers;

import application.models.*;
import application.repository.RepositoryOrchestrator;
import application.utils.ExcelExporter;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;

import java.io.File;
import java.text.Collator;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

public class ScheduleResultController {

    private final RepositoryOrchestrator repo;
    private Runnable onReGenerateRequest;

    // --- FXML Fields for Sidebar ---
    @FXML
    private ToggleButton btnTabTeacher;
    @FXML
    private ToggleButton btnTabClass;
    @FXML
    private TextField txtSearch;
    
    // Updated Sidebar components
    @FXML private TreeView<Object> treeViewTeachers;
    @FXML private ListView<Clazz> listViewClasses;

    // --- FXML Fields for Main View ---
    @FXML
    private ScrollPane scrollPaneSchedule; // To hide/show the schedule
    @FXML
    private VBox placeholderView;          // To show when no item is selected
    @FXML
    private GridPane scheduleGrid;
    @FXML
    private HBox bottomButtonContainer; // Container for buttons at the bottom

    public ScheduleResultController(RepositoryOrchestrator repo) {
        this.repo = repo;
    }

    public void initialize() {
        initGridStructure();
        setupSidebar();
        setupBottomButtons();
    }

    private void setupBottomButtons() {
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT); // Align buttons to the left
        // buttonBox.setPadding(new Insets(10)); // Padding handled by parent container

        // Removed Back Button

        Button btnReSchedule = new Button("Xếp lại lịch");
        btnReSchedule.setStyle("-fx-background-color: #f1c40f; -fx-text-fill: black; -fx-font-weight: bold; -fx-cursor: hand;");
        btnReSchedule.setOnAction(e -> handleReGenerate());

        Button btnExportExcel = new Button("Xuất Excel");
        btnExportExcel.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        btnExportExcel.setOnAction(e -> handleExportExcel());

        buttonBox.getChildren().addAll(btnReSchedule, btnExportExcel);

        // Add buttonBox to the bottom container defined in FXML
        if (bottomButtonContainer != null) {
            // Add as first child to be on the left
            bottomButtonContainer.getChildren().addFirst(buttonBox);
            // Ensure spacing between buttons and legend
            HBox.setHgrow(buttonBox, Priority.ALWAYS);
        }
    }

    /**
     * Sets up the Sidebar logic: CellFactory, Selection Listener, and Search Listener.
     */
    private void setupSidebar() {
        // --- 1. Setup Teacher TreeView ---
        treeViewTeachers.setCellFactory(tv -> new TreeCell<>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else if (item instanceof Department) {
                    setText(((Department) item).getName());
                    setStyle("-fx-font-weight: bold; -fx-text-fill: #34495e;");
                } else if (item instanceof Teacher) {
                    setText(((Teacher) item).getName());
                    setStyle("-fx-font-weight: normal; -fx-text-fill: #2c3e50;");
                } else {
                    setText(item.toString());
                }
            }
        });

        treeViewTeachers.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.getValue() instanceof Teacher) {
                handleSelection(newVal.getValue());
            }
        });

        // --- 2. Setup Class ListView ---
        listViewClasses.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Clazz item, boolean empty) {
                super.updateItem(item, empty);
                setText((empty || item == null) ? null : item.getClassName());
            }
        });

        listViewClasses.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) handleSelection(newVal);
        });

        // --- 3. Search Logic ---
        txtSearch.textProperty().addListener((obs, oldVal, newVal) -> {
            if (btnTabTeacher.isSelected()) {
                refreshTeacherTree(newVal);
            } else {
                refreshClassList(newVal);
            }
        });

        loadSidebarData();
    }

    private void handleSelection(Object entity) {
        placeholderView.setVisible(false);
        scrollPaneSchedule.setVisible(true);
        renderSchedule(entity);
    }

    /**
     * Triggered when Teacher or Class tab is clicked.
     */
    @FXML
    public void onTabChanged() {
        txtSearch.clear();
        boolean isTeacherMode = btnTabTeacher.isSelected();
        
        treeViewTeachers.setVisible(isTeacherMode);
        listViewClasses.setVisible(!isTeacherMode);
        
        loadSidebarData();
    }

    private void loadSidebarData() {
        if (btnTabTeacher.isSelected()) {
            refreshTeacherTree(txtSearch.getText());
        } else {
            refreshClassList(txtSearch.getText());
        }
    }

    private void refreshTeacherTree(String filter) {
        TreeItem<Object> root = new TreeItem<>("Root");
        String query = (filter == null) ? "" : filter.toLowerCase();

        List<Department> departments = repo.getDepartmentRepository().getAll();
        List<Teacher> allTeachers = repo.getTeacherRepository().getAll();
        
        // Sorting
        Collator collator = Collator.getInstance(new Locale("vi", "VN"));
        allTeachers.sort(Comparator.comparing(Teacher::getName, collator));

        Map<String, TreeItem<Object>> deptNodes = new HashMap<>();
        for (Department d : departments) {
            TreeItem<Object> dNode = new TreeItem<>(d);
            dNode.setExpanded(true);
            deptNodes.put(d.getId(), dNode);
        }
        
        TreeItem<Object> noDeptNode = new TreeItem<>("Chưa phân tổ");
        noDeptNode.setExpanded(true);

        for (Teacher t : allTeachers) {
            if (!query.isEmpty() && !t.getName().toLowerCase().contains(query)) continue;

            TreeItem<Object> tNode = new TreeItem<>(t);
            if (t.getDepartment() != null && deptNodes.containsKey(t.getDepartment().getId())) {
                deptNodes.get(t.getDepartment().getId()).getChildren().add(tNode);
            } else {
                noDeptNode.getChildren().add(tNode);
            }
        }

        // Add non-empty departments to root
        deptNodes.values().stream()
            .filter(node -> !node.getChildren().isEmpty())
            .forEach(root.getChildren()::add);
        
        if (!noDeptNode.getChildren().isEmpty()) {
            root.getChildren().add(noDeptNode);
        }

        treeViewTeachers.setRoot(root);
    }

    private void refreshClassList(String filter) {
        List<Clazz> classes = repo.getClassRepository().getAll();
        String query = (filter == null) ? "" : filter.toLowerCase();
        
        List<Clazz> filtered = classes.stream()
                .filter(c -> query.isEmpty() || c.getClassName().toLowerCase().contains(query))
                .toList();
        
        listViewClasses.setItems(FXCollections.observableArrayList(filtered));
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
            Subject s = repo.getSubjectRepository().getById(item.subjectId());
            if (s != null) subjectName = s.getName();

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

            drawLessonCell(dayInt, item.period(), subjectName, teacherName, className, isDouble, session);
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
                scheduleGrid.add(emptyCell, col, row);
            }
        }
    }

    /**
     * Draws a specific lesson cell.
     */
    private void drawLessonCell(int day, int period, String subject, String teacher, String className, boolean isDouble, ESession session) {

        int colIndex = day - 1; // Col 1 -> Monday
        int rowIndex;
        if (session == ESession.MORNING) {
            rowIndex = period;
        } else {
            rowIndex = period + 6;
        }

        VBox cell = new VBox(2);
        cell.setAlignment(Pos.CENTER);

        Label lblSub = new Label(subject);
        lblSub.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #2c3e50;");

        Label lblInfo = new Label();
        // Determine what to show based on the active tab
        boolean viewingByClass = btnTabClass.isSelected(); // If "Class" tab is active, we show Teacher name in cell
        lblInfo.setText(viewingByClass ? teacher : className);
        lblInfo.setStyle("-fx-font-size: 13px; -fx-text-fill: #7f8c8d;");

        cell.getChildren().addAll(lblSub, lblInfo);

        // Determine Background Color
        String bgStyle = "-fx-background-color: #f5f5f5;";
        if (isDouble) bgStyle = "-fx-background-color: #d4efdf;"; // Light mint
        if ("Sinh hoạt lớp".equalsIgnoreCase(subject) || "Chào cờ".equalsIgnoreCase(subject))
            bgStyle = "-fx-background-color: #fadbd8;"; // Light red

        // Apply style (Background ONLY, no border)
        cell.setStyle(bgStyle);

        // Force cell to fill the grid slot
        cell.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        // Add to grid (This will sit on top of the empty white slot created in fillEmptySlots)
        scheduleGrid.add(cell, colIndex, rowIndex);
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
        // Simply trigger the callback to open the config screen
        // The validation logic is now moved to ScheduleConfigController
        if (onReGenerateRequest != null) {
            onReGenerateRequest.run();
        }
    }

    public void setOnReGenerateRequest(Runnable onReGenerateRequest) {
        this.onReGenerateRequest = onReGenerateRequest;
    }

    @FXML
    public void handleExportExcel() {
        // 1. Ask for Start Date and Subject Labels
        Dialog<LocalDate> dialog = new Dialog<>();
        dialog.setTitle("Xuất Excel");
        dialog.setHeaderText("Cấu hình xuất Excel");

        ButtonType exportButtonType = new ButtonType("Xuất File", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(exportButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 20, 10, 10));

        DatePicker datePicker = new DatePicker(LocalDate.now());
        grid.add(new Label("Ngày bắt đầu:"), 0, 0);
        grid.add(datePicker, 1, 0);

        // Subject Labels Section
        Label lblSubjects = new Label("Tên viết tắt môn học:");
        lblSubjects.setStyle("-fx-font-weight: bold; -fx-padding: 10 0 5 0;");
        grid.add(lblSubjects, 0, 1, 2, 1);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setPrefHeight(300);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-padding: 5 5 5 5");

        // Use TilePane for 3 columns layout
        TilePane tilePane = new TilePane();
        tilePane.setPrefColumns(3);
        tilePane.setHgap(15);
        tilePane.setVgap(10);
        tilePane.setTileAlignment(Pos.TOP_LEFT);

        List<Subject> allSubjects = repo.getSubjectRepository().getAll();
        Map<String, TextField> labelInputs = new HashMap<>();

        for (Subject s : allSubjects) {
            HBox cell = new HBox(5);
            cell.setAlignment(Pos.CENTER_LEFT);

            cell.setPrefWidth(120);
            cell.setPadding(new Insets(2, 5, 2, 5));

            // Subject name label (original)
            Label nameLabel = new Label(s.getName());
            nameLabel.setStyle("-fx-text-fill: #333;");
            nameLabel.setWrapText(true);

            nameLabel.setMinWidth(130);
            nameLabel.setMaxWidth(130);
            nameLabel.setAlignment(Pos.CENTER_RIGHT);
            nameLabel.setTextAlignment(TextAlignment.RIGHT);

            // Text box for short name
            TextField labelField = new TextField(s.getLabel());
            labelField.setPromptText("Tên tắt");
            labelField.setPrefWidth(75);
            labelField.setMinWidth(75);

            HBox.setHgrow(labelField, Priority.NEVER);

            labelInputs.put(s.getId(), labelField);

            cell.getChildren().addAll(nameLabel, labelField);
            tilePane.getChildren().add(cell);
        }
        scrollPane.setContent(tilePane);
        grid.add(scrollPane, 0, 2, 2, 1);

        // Adjust dialog size
        dialog.getDialogPane().setPrefWidth(500);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == exportButtonType) {
                // Save labels
                for (Subject s : allSubjects) {
                    TextField tf = labelInputs.get(s.getId());
                    if (tf != null) {
                        String newLabel = tf.getText().trim();
                        if (!newLabel.isEmpty() && !newLabel.equals(s.getLabel())) {
                            s.setLabel(newLabel);
                            repo.getSubjectRepository().update(s);
                        }
                    }
                }
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
}