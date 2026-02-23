package application.controllers;

import application.models.*;
import application.repository.RepositoryOrchestrator;
import application.utils.ScheduleValidator;
import application.views.TimeGridSelector;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Callback;
import scheduler.common.constants.SubjectConstants;

import java.text.Collator;
import java.util.*;

public class TeacherController {

    private final RepositoryOrchestrator repositoryOrchestrator;
    private final ScheduleValidator scheduleValidator;
    private final ObservableList<Teacher> teacherList = FXCollections.observableArrayList();
    // Temporary assignment before save
    private final ObservableList<Assignment> currentAssignments = FXCollections.observableArrayList();
    // List of assignments to be deleted
    private final List<Assignment> assignmentsToDelete = new ArrayList<>();
    // --- UI Controls ---
    @FXML
    public SplitPane root;
    private Teacher selectedTeacher = null;
    @FXML
    private TreeView<Object> teacherTreeView;
    @FXML
    private TextField searchField;
    @FXML
    private Button btnAdd, btnDelete, btnSave;
    @FXML
    private TextField nameField;
    @FXML
    private TextField codeField;
    @FXML
    private StackPane timeGridContainer;
    // --- UI Controls for class assignments---
    @FXML
    private ComboBox<Subject> subjectComboBox; // Use String for subject
    @FXML
    private ListView<Clazz> multiClassListView; // Use String for class name
    @FXML
    private Button btnAddBatch;
    // TableView and columns
    @FXML
    private TableView<Assignment> assignmentTable;
    @FXML
    private TableColumn<Assignment, String> colSubject;
    @FXML
    private TableColumn<Assignment, String> colClass;
    @FXML
    private TableColumn<Assignment, Integer> colPeriods;
    @FXML
    private TableColumn<Assignment, Void> colAction; // Column containing Delete button
    @FXML
    private Label totalPeriodsLabel;
    // --- Homeroom Teacher Controls ---
    @FXML
    private CheckBox chkHomeroom;
    @FXML
    private ComboBox<Clazz> homeroomClassComboBox;
    // --- Department Controls ---
    @FXML
    private ComboBox<Department> departmentComboBox;
    @FXML
    private Button btnManageDepartments;
    // --- Time grid ---
    @FXML
    private Label lblRemainingCapacity;
    @FXML
    private Button btnWarning;

    // --- Data & Logic ---
    private TimeGridSelector timeGridSelector;

    public TeacherController(RepositoryOrchestrator repositoryOrchestrator) {
        this.repositoryOrchestrator = repositoryOrchestrator;
        this.scheduleValidator = new ScheduleValidator(repositoryOrchestrator);
    }

    public void initialize() {
        setupTimeGrid();
        setupTeacherTreeView();
        setupAssignmentForm(); // Setup logic for assignment
        setupButtons();
        setupHomeroomControls();
        setupDepartmentControls();
        setupWarningButton();

        loadData();
        Platform.runLater(() -> root.setDividerPosition(0, 0.2));

        // Check for implicit homeroom assignments on startup
        Platform.runLater(this::checkAllImplicitHomeroomAssignments);
    }

    private void setupWarningButton() {
        btnWarning = new Button("⚠");
        btnWarning.setStyle("-fx-background-color: transparent; -fx-text-fill: red; -fx-font-size: 16px; -fx-font-weight: bold; -fx-cursor: hand;");
        btnWarning.setVisible(false);

        // Add to the HBox containing lblRemainingCapacity
        if (lblRemainingCapacity != null && lblRemainingCapacity.getParent() instanceof HBox) {
            HBox parent = (HBox) lblRemainingCapacity.getParent();
            parent.getChildren().add(btnWarning);
        }
    }

    private void checkAllImplicitHomeroomAssignments() {
        // This method scans all assignments in the DB and ensures homeroom consistency
        List<Assignment> allAssignments = repositoryOrchestrator.getAssignmentRepository().getAll();
        List<Subject> subjects = repositoryOrchestrator.getSubjectRepository().getAll();

        for (Assignment a : allAssignments) {
            Subject s = subjects.stream().filter(sub -> sub.getId().equals(a.getSubjectId())).findFirst().orElse(null);
            if (s == null) continue;

            boolean isSpecial = s.getId().equals(SubjectConstants.FLAG_SALUTE_ID) ||
                    s.getId().equals(SubjectConstants.CLASS_MEETING_ID);

            if (isSpecial) {
                Clazz clazz = repositoryOrchestrator.getClassRepository().getById(a.getClassId());
                if (clazz != null) {
                    // If class has no homeroom teacher, or different one, update it
                    // Special subject assignment implies homeroom duty.
                    if (!a.getTeacherId().equals(clazz.getHomeroomTeacherId())) {
                        clazz.setHomeroomTeacherId(a.getTeacherId());
                        repositoryOrchestrator.getClassRepository().save(clazz);
                        System.out.println("Auto-assigned homeroom teacher " + a.getTeacherId() + " for class " + clazz.getClassName() + " based on subject " + s.getName());
                    }
                }
            }
        }
    }

    private void setupTimeGrid() {
        timeGridSelector = new TimeGridSelector();

        HBox legend = createLegend();
        VBox container = new VBox(10);
        container.getChildren().addAll(timeGridSelector, legend);
        container.setAlignment(Pos.CENTER);

        timeGridContainer.getChildren().add(container);

        // Update capacity when grid changes
        timeGridSelector.setOnGridChanged(this::updateRemainingCapacity);
    }

    private HBox createLegend() {
        HBox legend = new HBox(20);
        legend.setAlignment(Pos.CENTER);
        legend.setPadding(new Insets(10));

        legend.getChildren().addAll(
                createLegendItem("#ef9a9a", "Giáo viên bận"),
                createLegendItem("#bdc3c7", "Học sinh nghỉ"),
                createLegendItem("#f1c40f", "Hoạt động chung")
        );
        return legend;
    }

    private HBox createLegendItem(String color, String text) {
        HBox item = new HBox(5);
        item.setAlignment(Pos.CENTER_LEFT);

        Rectangle rect = new Rectangle(20, 20);
        rect.setStyle("-fx-fill: " + color + "; -fx-stroke: #7f8c8d; -fx-stroke-width: 1;");

        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: black;");

        item.getChildren().addAll(rect, lbl);
        return item;
    }

    private void setupTeacherTreeView() {
        teacherTreeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.getValue() instanceof Teacher) {
                showTeacherDetails((Teacher) newVal.getValue());
            }
        });

        // Setup Drag and Drop
        teacherTreeView.setCellFactory(tv -> {
            TreeCell<Object> cell = new TreeCell<>() {
                @Override
                protected void updateItem(Object item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                    } else {
                        setText(item.toString());
                    }
                }
            };

            // Drag detection (Source)
            cell.setOnDragDetected(event -> {
                if (cell.getItem() instanceof Teacher) {
                    Dragboard db = cell.startDragAndDrop(TransferMode.MOVE);
                    ClipboardContent content = new ClipboardContent();
                    content.putString(((Teacher) cell.getItem()).getId()); // Store Teacher ID
                    db.setContent(content);
                    event.consume();
                }
            });

            // Drag over (Target)
            cell.setOnDragOver(event -> {
                if (event.getGestureSource() != cell && event.getDragboard().hasString()) {
                    // Allow drop only on Department nodes or "No Department" node
                    if (cell.getItem() instanceof Department || (cell.getItem() instanceof String && cell.getItem().equals("Chưa phân tổ"))) {
                        event.acceptTransferModes(TransferMode.MOVE);
                    }
                }
                event.consume();
            });

            // Drag dropped (Target)
            cell.setOnDragDropped(event -> {
                Dragboard db = event.getDragboard();
                boolean success = false;
                if (db.hasString()) {
                    String teacherId = db.getString();
                    // We need to find the teacher in the current list to update the object reference
                    Teacher draggedTeacher = teacherList.stream()
                            .filter(t -> t.getId().equals(teacherId))
                            .findFirst()
                            .orElse(null);

                    if (draggedTeacher != null) {
                        Department targetDept = null;
                        if (cell.getItem() instanceof Department) {
                            targetDept = (Department) cell.getItem();
                        }
                        // Update teacher's department
                        draggedTeacher.setDepartment(targetDept);
                        repositoryOrchestrator.getTeacherRepository().update(draggedTeacher);

                        // Refresh UI
                        refreshTeacherTreeView();
                        if (selectedTeacher != null && selectedTeacher.getId().equals(teacherId)) {
                            showTeacherDetails(draggedTeacher); // Refresh details view
                        }

                        success = true;
                    }
                }
                event.setDropCompleted(success);
                event.consume();
            });

            return cell;
        });
    }

    private void refreshTeacherTreeView() {
        TreeItem<Object> rootItem = new TreeItem<>("Root");
        rootItem.setExpanded(true);

        Map<String, TreeItem<Object>> departmentMap = new HashMap<>();
        TreeItem<Object> noDeptItem = new TreeItem<>("Chưa phân tổ");
        noDeptItem.setExpanded(true);

        // Create nodes for all departments
        List<Department> departments = repositoryOrchestrator.getDepartmentRepository().getAll();
        for (Department dept : departments) {
            TreeItem<Object> deptItem = new TreeItem<>(dept);
            deptItem.setExpanded(true);
            departmentMap.put(dept.getId(), deptItem);
            rootItem.getChildren().add(deptItem);
        }
        rootItem.getChildren().add(noDeptItem);

        // Sort teachers alphabetically
        Collator collator = Collator.getInstance(new Locale("vi", "VN"));
        List<Teacher> sortedTeachers = new ArrayList<>(teacherList);
        sortedTeachers.sort(Comparator.comparing(Teacher::getName, collator));

        // Add teachers to appropriate nodes
        for (Teacher teacher : sortedTeachers) {
            // Filter by search text
            String searchText = searchField.getText().toLowerCase();
            if (!searchText.isEmpty() && !teacher.getName().toLowerCase().contains(searchText)) {
                continue;
            }

            TreeItem<Object> teacherItem = new TreeItem<>(teacher);
            if (teacher.getDepartment() != null && departmentMap.containsKey(teacher.getDepartment().getId())) {
                departmentMap.get(teacher.getDepartment().getId()).getChildren().add(teacherItem);
            } else {
                noDeptItem.getChildren().add(teacherItem);
            }
        }

        // Remove empty "No Department" node if not needed, or keep it.
        if (noDeptItem.getChildren().isEmpty()) {
            rootItem.getChildren().remove(noDeptItem);
        }

        teacherTreeView.setRoot(rootItem);
    }

    private void setupButtons() {
        btnAdd.setOnAction(e -> createNewTeacher());
        btnSave.setOnAction(e -> saveCurrentTeacher());
        btnDelete.setOnAction(e -> deleteTeacher());

        // Batch Add button event
        btnAddBatch.setOnAction(e -> handleBatchAdd());

        // Search listener
        searchField.textProperty().addListener((obs, oldVal, newVal) -> refreshTeacherTreeView());
    }

    private void setupHomeroomControls() {
        chkHomeroom.selectedProperty().addListener((obs, oldVal, newVal) -> {
            homeroomClassComboBox.setDisable(!newVal);
            if (!newVal) {
                homeroomClassComboBox.setValue(null);
            } else {
                // If checked, update busy matrix constraints immediately if a class is already selected
                updateHomeroomConstraints();
            }
        });

        homeroomClassComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (chkHomeroom.isSelected()) {
                updateHomeroomConstraints();
            }
        });
    }

    private void setupDepartmentControls() {
        btnManageDepartments.setOnAction(e -> showDepartmentManagementDialog());

        // When department changes, update the subject list
        departmentComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (selectedTeacher != null) {
                // Just update the UI model, don't save to DB yet
                selectedTeacher.setDepartment(newVal);
                updateSubjectListForTeacher(selectedTeacher);
            }
        });
    }

    private void showDepartmentManagementDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Quản lý Tổ chuyên môn");
        dialog.setHeaderText("Danh sách Tổ chuyên môn");

        ButtonType closeButtonType = new ButtonType("Đóng", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().add(closeButtonType);

        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        content.setPrefWidth(400);
        content.setPrefHeight(300);

        ListView<Department> deptListView = new ListView<>();
        deptListView.setItems(FXCollections.observableArrayList(repositoryOrchestrator.getDepartmentRepository().getAll()));

        HBox actions = new HBox(10);
        TextField deptNameField = new TextField();
        deptNameField.setPromptText("Tên tổ mới...");
        Button btnAddDept = new Button("Thêm");
        Button btnDeleteDept = new Button("Xóa");

        btnAddDept.setOnAction(e -> {
            String name = deptNameField.getText().trim();
            if (!name.isEmpty()) {
                Department newDept = new Department(UUID.randomUUID().toString(), name);
                repositoryOrchestrator.getDepartmentRepository().insert(newDept);
                deptListView.getItems().add(newDept);
                deptNameField.clear();
                // Refresh main combo box and tree view
                loadDepartments();
                refreshTeacherTreeView();
            }
        });

        btnDeleteDept.setOnAction(e -> {
            Department selected = deptListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                repositoryOrchestrator.getDepartmentRepository().delete(selected.getId());
                deptListView.getItems().remove(selected);
                // Refresh main combo box and tree view
                loadDepartments();
                refreshTeacherTreeView();
            }
        });

        // Subject assignment for department
        Button btnAssignSubjects = new Button("Gán môn học");
        btnAssignSubjects.setOnAction(e -> {
            Department selected = deptListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                showSubjectAssignmentDialog(selected);
            }
        });

        actions.getChildren().addAll(deptNameField, btnAddDept, btnDeleteDept, btnAssignSubjects);
        content.getChildren().addAll(deptListView, actions);

        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    private void showSubjectAssignmentDialog(Department department) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Gán môn học cho " + department.getName());

        ButtonType closeButtonType = new ButtonType("Đóng", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().add(closeButtonType);

        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        content.setPrefWidth(300);

        ListView<Subject> subjectListView = new ListView<>();
        List<Subject> allSubjects = repositoryOrchestrator.getSubjectRepository().getAll();
        subjectListView.setItems(FXCollections.observableArrayList(allSubjects));
        subjectListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // Pre-select existing subjects
        // Need to fetch full department details to get subjects
        Department fullDept = repositoryOrchestrator.getDepartmentRepository().getById(department.getId());
        if (fullDept != null && fullDept.getQualifiedSubjects() != null) {
            for (Subject s : fullDept.getQualifiedSubjects()) {
                for (Subject item : subjectListView.getItems()) {
                    if (item.getId().equals(s.getId())) {
                        subjectListView.getSelectionModel().select(item);
                        break;
                    }
                }
            }
        }

        Button btnSaveSubjects = new Button("Lưu thay đổi");
        btnSaveSubjects.setOnAction(e -> {
            List<Subject> selectedSubjects = new ArrayList<>(subjectListView.getSelectionModel().getSelectedItems());
            fullDept.setQualifiedSubjects(selectedSubjects);
            repositoryOrchestrator.getDepartmentRepository().update(fullDept);

            if (selectedTeacher != null && selectedTeacher.getDepartment() != null && selectedTeacher.getDepartment().getId().equals(department.getId())) {
                updateSubjectListForTeacher(selectedTeacher);
            }

            dialog.close();
        });

        content.getChildren().addAll(new Label("Chọn các môn học thuộc tổ này:"), subjectListView, btnSaveSubjects);
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    private void updateHomeroomConstraints() {
        // clear all special and forced constraints first
        for (int d = 0; d < 6; d++) {
            for (int p = 0; p < 10; p++) {
                timeGridSelector.setSpecialCell(d, p, null);
                timeGridSelector.setForcedBusyCell(d, p, null);
            }
        }

        // check both Morning and Afternoon sessions
        Session morningSession = repositoryOrchestrator.getSessionRepository().getByName(ESession.MORNING);
        Session afternoonSession = repositoryOrchestrator.getSessionRepository().getByName(ESession.AFTERNOON);
        boolean[][] currentMatrix = timeGridSelector.getBusyMatrix();

        if (morningSession != null) {
            boolean[][] morningBusy = morningSession.getBusyMatrix();
            for (int d = 0; d < 6; d++) {
                for (int p = 0; p < 5; p++) {
                    if (morningBusy[d][p]) {
                        currentMatrix[d][p] = true; // Busy
                        timeGridSelector.setForcedBusyCell(d, p, "Tiết này học sinh nghỉ (theo cấu hình buổi học)", "#bdc3c7");
                    }
                }
            }
        }

        if (afternoonSession != null) {
            boolean[][] afternoonBusy = afternoonSession.getBusyMatrix();
            for (int d = 0; d < 6; d++) {
                for (int p = 5; p < 10; p++) {
                    if (afternoonBusy[d][p]) {
                        currentMatrix[d][p] = true; // Busy
                        timeGridSelector.setForcedBusyCell(d, p, "Tiết này học sinh nghỉ (theo cấu hình buổi học)", "#bdc3c7");
                    }
                }
            }
        }

        // 3. Apply Fixed Slots for Flag Salute and Class Meeting for ALL teachers
        int morningLastDay = 5;
        int morningLastPeriod = 4; // Default Sat 4
        int afternoonLastDay = 5;
        int afternoonLastPeriod = 9; // Default Sat 9

        // Calculate actual last periods from session config
        if (morningSession != null) {
            boolean[][] mBusy = morningSession.getBusyMatrix();
            // Find last available slot
            outer:
            for (int d = 5; d >= 0; d--) {
                for (int p = 4; p >= 0; p--) {
                    if (!mBusy[d][p]) {
                        morningLastDay = d;
                        morningLastPeriod = p;
                        break outer;
                    }
                }
            }
        }

        if (afternoonSession != null) {
            boolean[][] aBusy = afternoonSession.getBusyMatrix();
            // Find last available slot
            outer:
            for (int d = 5; d >= 0; d--) {
                for (int p = 9; p >= 5; p--) {
                    if (!aBusy[d][p]) {
                        afternoonLastDay = d;
                        afternoonLastPeriod = p;
                        break outer;
                    }
                }
            }
        }

        // Apply Busy to ALL 4 slots initially
        // Morning Flag Salute (Mon 0)
        currentMatrix[0][0] = true;
        timeGridSelector.setForcedBusyCell(0, 0, "Giờ Chào cờ", "#f1c40f");

        // Morning Class Meeting
        currentMatrix[morningLastDay][morningLastPeriod] = true;
        timeGridSelector.setForcedBusyCell(morningLastDay, morningLastPeriod, "Giờ Sinh hoạt lớp", "#f1c40f");

        int afternoonFlagSalutePeriod = 9;
        if (afternoonSession != null) {
            boolean[][] aBusy = afternoonSession.getBusyMatrix();
            for (int p = 9; p >= 5; p--) {
                if (!aBusy[0][p]) {
                    afternoonFlagSalutePeriod = p;
                    break;
                }
            }
        }

        currentMatrix[0][afternoonFlagSalutePeriod] = true;
        timeGridSelector.setForcedBusyCell(0, afternoonFlagSalutePeriod, "Giờ Chào cờ", "#f1c40f");

        // Afternoon Class Meeting
        currentMatrix[afternoonLastDay][afternoonLastPeriod] = true;
        timeGridSelector.setForcedBusyCell(afternoonLastDay, afternoonLastPeriod, "Giờ Sinh hoạt lớp", "#f1c40f");


        // 4. Handle Exceptions for Homeroom Teachers
        if (chkHomeroom.isSelected() && homeroomClassComboBox.getValue() != null) {
            Clazz homeroomClass = homeroomClassComboBox.getValue();
            Grade grade = repositoryOrchestrator.getGradeRepository().getById(homeroomClass.getGradeId());
            if (grade != null && grade.getSession() != null) {
                ESession session = grade.getSession().getSessionName();

                if (session == ESession.MORNING) {
                    // Unmark Morning slots
                    currentMatrix[0][0] = false; // Available for assignment
                    timeGridSelector.setForcedBusyCell(0, 0, null); // Remove forced busy
                    timeGridSelector.setSpecialCell(0, 0, "Tiết này dành cho Chào cờ (GVCN phải tham gia)");

                    currentMatrix[morningLastDay][morningLastPeriod] = false;
                    timeGridSelector.setForcedBusyCell(morningLastDay, morningLastPeriod, null);
                    timeGridSelector.setSpecialCell(morningLastDay, morningLastPeriod, "Tiết này dành cho Sinh hoạt lớp (GVCN phải tham gia)");

                } else if (session == ESession.AFTERNOON) {
                    // Unmark Afternoon slots
                    currentMatrix[0][afternoonFlagSalutePeriod] = false;
                    timeGridSelector.setForcedBusyCell(0, afternoonFlagSalutePeriod, null);
                    timeGridSelector.setSpecialCell(0, afternoonFlagSalutePeriod, "Tiết này dành cho Chào cờ (GVCN phải tham gia)");

                    currentMatrix[afternoonLastDay][afternoonLastPeriod] = false;
                    timeGridSelector.setForcedBusyCell(afternoonLastDay, afternoonLastPeriod, null);
                    timeGridSelector.setSpecialCell(afternoonLastDay, afternoonLastPeriod, "Tiết này dành cho Sinh hoạt lớp (GVCN phải tham gia)");
                }
            }
        }

        timeGridSelector.setBusyMatrix(currentMatrix);
        updateRemainingCapacity();
    }

    private void updateRemainingCapacity() {
        if (timeGridSelector == null) return;

        boolean[][] busyMatrix = timeGridSelector.getBusyMatrix();
        int morningCapacity = calculateSessionCapacity(busyMatrix, 0, 5);
        int afternoonCapacity = calculateSessionCapacity(busyMatrix, 5, 10);

        // Subtract currently assigned periods
        int morningAssigned = 0;
        int afternoonAssigned = 0;

        for (Assignment a : currentAssignments) {
            int periods = getPeriodsForAssignment(a);
            Clazz clazz = repositoryOrchestrator.getClassRepository().getById(a.getClassId());
            if (clazz != null) {
                Grade grade = repositoryOrchestrator.getGradeRepository().getById(clazz.getGradeId());
                if (grade != null && grade.getSession() != null) {
                    if (grade.getSession().getSessionName() == ESession.MORNING) {
                        morningAssigned += periods;
                    } else if (grade.getSession().getSessionName() == ESession.AFTERNOON) {
                        afternoonAssigned += periods;
                    }
                }
            }
        }

        int morningRemaining = morningCapacity - morningAssigned;
        int afternoonRemaining = afternoonCapacity - afternoonAssigned;

        lblRemainingCapacity.setText(String.format("Sức chứa còn lại: Sáng: %d, Chiều: %d", morningRemaining, afternoonRemaining));

        // Warning logic
        if (morningRemaining < 0 || afternoonRemaining < 0) {
            lblRemainingCapacity.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: red;");
            timeGridContainer.setStyle("-fx-background-color: rgba(255, 0, 0, 0.1); -fx-background-radius: 5;");
            btnWarning.setVisible(true);

            StringBuilder warningMsg = new StringBuilder();
            if (morningRemaining < 0) {
                warningMsg.append("Buổi sáng: Cần ").append(morningAssigned).append(" tiết nhưng chỉ có ").append(morningCapacity).append(" tiết.\n");
            }
            if (afternoonRemaining < 0) {
                warningMsg.append("Buổi chiều: Cần ").append(afternoonAssigned).append(" tiết nhưng chỉ có ").append(afternoonCapacity).append(" tiết.\n");
            }

            btnWarning.setOnAction(e -> showAlert(Alert.AlertType.WARNING, "Cảnh báo quá tải", warningMsg.toString()));
        } else {
            lblRemainingCapacity.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #e67e22;");
            timeGridContainer.setStyle("-fx-background-color: transparent;");
            btnWarning.setVisible(false);
        }
    }

    private int calculateSessionCapacity(boolean[][] busyMatrix, int startPeriod, int endPeriod) {
        int totalCapacity = 0;

        for (int d = 0; d < 6; d++) {
            // Extract the session periods for this day
            boolean[] daySession = new boolean[endPeriod - startPeriod];
            for (int p = 0; p < daySession.length; p++) {
                daySession[p] = busyMatrix[d][startPeriod + p]; // true if busy
            }

            int availableSlots = 0;
            for (boolean busy : daySession) {
                if (!busy) availableSlots++;
            }

            if (availableSlots == 5) {
                totalCapacity += 4;
            } else {
                totalCapacity += availableSlots;
            }
        }
        return totalCapacity;
    }

    private void setupAssignmentForm() {
        // ListView conf multiple selection mode
        multiClassListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // Configuration for TableView columns

        colSubject.setCellValueFactory(data -> {
            Subject s = repositoryOrchestrator.getSubjectRepository().getById(data.getValue().getSubjectId());
            return new SimpleStringProperty(s.toString());
        });
        colClass.setCellValueFactory(data -> {
            Clazz c = repositoryOrchestrator.getClassRepository().getById(data.getValue().getClassId());
            return new SimpleStringProperty(c.toString());
        });
        colPeriods.setCellValueFactory(data -> new SimpleObjectProperty<>(getPeriodsForAssignment(data.getValue())));

        // Add delete button into cells
        addButtonToTable();

        // Link data into table
        assignmentTable.setItems(currentAssignments);

        // Listen to changes to re-calculate periods
        currentAssignments.addListener((ListChangeListener<Assignment>) c -> {
            updateTotalPeriods();
            updateRemainingCapacity();
        });
    }

    // --- ASSIGNMENT LOGIC (Batch Add) ---
    private void handleBatchAdd() {

        Subject subject = subjectComboBox.getValue();
        ObservableList<Clazz> selectedClasses = multiClassListView.getSelectionModel().getSelectedItems();


        if (subject == null || selectedClasses.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Thiếu thông tin", "Vui lòng chọn Môn học và ít nhất một Lớp học.");
            return;
        }

        // Check if user is trying to manually assign special subjects
        boolean isSpecialSubject = subject.getId().equals(SubjectConstants.FLAG_SALUTE_ID) ||
                subject.getId().equals(SubjectConstants.CLASS_MEETING_ID);

        // Create Assignment for each selected class
        for (Clazz clazz : selectedClasses) {
            boolean exists = currentAssignments.stream().anyMatch(
                    a ->
                            a.getClassId().equals(clazz.getId()) && a.getSubjectId().equals(subject.getId())
            );
            if (exists) continue;
            Curriculum cur = repositoryOrchestrator.getCurriculumRepository().getByGradeAndSubject(clazz.getGradeId(), subject.getId());
            if (cur == null || cur.getPeriodsPerWeek() == 0) {
                System.out.println("Subject " + subject + " is not in curriculum for class " + clazz);
                continue;
            }

            // Constraint Check for Special Subjects
            if (isSpecialSubject) {
                // Check if class already has a homeroom teacher
                // Need fresh data from DB for accurate check
                Clazz freshClass = repositoryOrchestrator.getClassRepository().getById(clazz.getId());
                if (freshClass.getHomeroomTeacherId() != null && !freshClass.getHomeroomTeacherId().equals(selectedTeacher.getId())) {
                    Teacher existingTeacher = repositoryOrchestrator.getTeacherRepository().getById(freshClass.getHomeroomTeacherId());

                    // Ask user for confirmation to overwrite
                    Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
                    confirmAlert.setTitle("Xác nhận thay đổi");
                    confirmAlert.setHeaderText("Xung đột giáo viên chủ nhiệm");
                    confirmAlert.setContentText("Lớp " + freshClass.getClassName() + " đã có giáo viên chủ nhiệm là " +
                            (existingTeacher != null ? existingTeacher.getName() : "Unknown") +
                            ". Bạn có muốn thay thế bằng giáo viên hiện tại không?");

                    Optional<ButtonType> result = confirmAlert.showAndWait();
                    if (result.isPresent() && result.get() == ButtonType.OK) {
                        // User confirmed overwrite.
                    } else {
                        continue; // Skip this class if user cancels
                    }
                }

                // Check if teacher is already homeroom for another class
                Clazz otherClass = repositoryOrchestrator.getClassRepository().findByHomeroomTeacher(selectedTeacher.getId());
                if (otherClass != null && !otherClass.getId().equals(freshClass.getId())) {
                    showAlert(Alert.AlertType.ERROR, "Xung đột", "Giáo viên này đang là chủ nhiệm của lớp " + otherClass.getClassName() + ".");
                    continue; // Skip this class
                }

                // If checks pass, we will implicitly set this teacher as homeroom teacher upon saving
                if (selectedClasses.size() == 1) {
                    chkHomeroom.setSelected(true);
                    // Find matching item in combobox
                    for (Clazz c : homeroomClassComboBox.getItems()) {
                        if (c.getId().equals(clazz.getId())) {
                            homeroomClassComboBox.setValue(c);
                            break;
                        }
                    }
                }
            }

            Assignment newAssignment = new Assignment(
                    UUID.randomUUID().toString(),
                    selectedTeacher.getId(),
                    subject.getId(),
                    clazz.getId()
            );
            // Don't save immediately. Wait for Save button.
            // Assignment inserted = repositoryOrchestrator.getAssignmentRepository().save(newAssignment);
            currentAssignments.add(newAssignment);
        }

        // Clear class selection to avoid user confusion for the next addition
        multiClassListView.getSelectionModel().clearSelection();
        assignmentTable.refresh();
        updateTotalPeriods();
    }

    private void updateTotalPeriods() {
        int totalMorning = 0;
        int totalAfternoon = 0;
        int total = 0;
        for (Assignment assignment : currentAssignments) {
            int periods = getPeriodsForAssignment(assignment);
            total += periods;

            Clazz clazz = repositoryOrchestrator.getClassRepository().getById(assignment.getClassId());
            if (clazz != null) {
                Grade grade = repositoryOrchestrator.getGradeRepository().getById(clazz.getGradeId());
                if (grade != null && grade.getSession() != null) {
                    if (grade.getSession().getSessionName() == ESession.MORNING) {
                        totalMorning += periods;
                    } else if (grade.getSession().getSessionName() == ESession.AFTERNOON) {
                        totalAfternoon += periods;
                    }
                }
            }
        }
        totalPeriodsLabel.setText(String.format("Tổng số tiết: %d (Sáng: %d, Chiều: %d)", total, totalMorning, totalAfternoon));
    }

    private int getPeriodsForAssignment(Assignment assignment) {
        Clazz clazz = repositoryOrchestrator.getClassRepository().getById(assignment.getClassId());
        if (clazz == null) return 0;

        String gradeId = clazz.getGradeId();

        Curriculum cur = repositoryOrchestrator.getCurriculumRepository().getByGradeAndSubject(gradeId, assignment.getSubjectId());
        if (cur == null) return 0;

        return cur.getPeriodsPerWeek();
    }

    private void showTeacherDetails(Teacher teacher) {
        if (teacher == null) return;

        // Clear delete list when switching teacher
        assignmentsToDelete.clear();

        // Lazy loading teacher's assignments
        List<Assignment> assignments = repositoryOrchestrator.getAssignmentRepository().getByTeacherId(teacher.getId());
        ObservableList<Assignment> observableList = FXCollections.observableArrayList(assignments);
        teacher.setAssignments(observableList);
        selectedTeacher = teacher;

        nameField.setText(teacher.getName());
        codeField.setText(teacher.getId());
        timeGridSelector.setBusyMatrix(teacher.getBusyMatrix());

        // Load this teacher's assignment list into the table
        currentAssignments.setAll(teacher.getAssignments());

        // Load homeroom info
        Clazz homeroomClass = repositoryOrchestrator.getClassRepository().findByHomeroomTeacher(teacher.getId());
        if (homeroomClass != null) {
            chkHomeroom.setSelected(true);
            // Find the matching object in the combobox items to select it correctly
            for (Clazz c : homeroomClassComboBox.getItems()) {
                if (c.getId().equals(homeroomClass.getId())) {
                    homeroomClassComboBox.setValue(c);
                    break;
                }
            }
        } else {
            chkHomeroom.setSelected(false);
            homeroomClassComboBox.setValue(null);
        }

        // Load department info
        if (teacher.getDepartment() != null) {
            // Find matching department in combobox
            for (Department d : departmentComboBox.getItems()) {
                if (d.getId().equals(teacher.getDepartment().getId())) {
                    departmentComboBox.setValue(d);
                    break;
                }
            }
        } else {
            departmentComboBox.setValue(null);
        }

        // Update constraints based on loaded data
        updateHomeroomConstraints();

        // Update subject list based on department
        updateSubjectListForTeacher(teacher);
    }

    private void updateSubjectListForTeacher(Teacher teacher) {
        List<Subject> allSubjects = repositoryOrchestrator.getSubjectRepository().getAll();

        if (teacher.getDepartment() != null) {
            // Fetch full department details to get qualified subjects
            Department dept = repositoryOrchestrator.getDepartmentRepository().getById(teacher.getDepartment().getId());
            if (dept != null && dept.getQualifiedSubjects() != null && !dept.getQualifiedSubjects().isEmpty()) {
                subjectComboBox.setItems(FXCollections.observableArrayList(dept.getQualifiedSubjects()));
            } else {
                // Fallback if department has no subjects or fetch failed
                subjectComboBox.setItems(FXCollections.observableArrayList(allSubjects));
            }
        } else {
            // If no department, show all subjects
            subjectComboBox.setItems(FXCollections.observableArrayList(allSubjects));
        }
    }

    private void createNewTeacher() {
        Dialog<Teacher> dialog = new Dialog<>();
        dialog.setTitle("Thêm Giáo viên mới");
        dialog.setHeaderText("Nhập thông tin giáo viên");

        ButtonType addButtonType = new ButtonType("Thêm", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField nameInput = new TextField();
        nameInput.setPromptText("Tên giáo viên");
        TextField codeInput = new TextField();
        codeInput.setPromptText("Mã giáo viên (tùy chọn)");

        grid.add(new Label("Tên:"), 0, 0);
        grid.add(nameInput, 1, 0);
        grid.add(new Label("Mã:"), 0, 1);
        grid.add(codeInput, 1, 1);

        dialog.getDialogPane().setContent(grid);

        // Enable/Disable Add button depending on whether a name was entered.
        javafx.scene.Node addButton = dialog.getDialogPane().lookupButton(addButtonType);
        addButton.setDisable(true);

        nameInput.textProperty().addListener((observable, oldValue, newValue) -> {
            addButton.setDisable(newValue.trim().isEmpty());
        });

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                String name = nameInput.getText().trim();
                String code = codeInput.getText().trim();
                if (code.isEmpty()) {
                    code = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                }
                return new Teacher(name, code);
            }
            return null;
        });

        Optional<Teacher> result = dialog.showAndWait();

        result.ifPresent(newTeacher -> {
            // Check for duplicate ID if user manually entered one
            if (repositoryOrchestrator.getTeacherRepository().getById(newTeacher.getId()) != null) {
                showAlert(Alert.AlertType.ERROR, "Lỗi", "Mã giáo viên đã tồn tại!");
                return;
            }

            repositoryOrchestrator.getTeacherRepository().insert(newTeacher);
            teacherList.add(newTeacher);

            // Refresh tree view
            refreshTeacherTreeView();

            nameField.requestFocus();
        });
    }

    private void saveCurrentTeacher() {
        // Get selected item from TreeView
        TreeItem<Object> selectedItem = teacherTreeView.getSelectionModel().getSelectedItem();
        if (selectedItem == null || !(selectedItem.getValue() instanceof Teacher)) {
            return;
        }

        Teacher selected = (Teacher) selectedItem.getValue();

        if (selected != null) {
            selected.setName(nameField.getText());
            selected.setId(codeField.getText());
            selected.setDepartment(departmentComboBox.getValue());

            boolean[][] busyMatrix = timeGridSelector.getBusyMatrix();

            // Re-apply constraints to ensure they are saved correctly even if user tried to bypass UI
            Session morningSession = repositoryOrchestrator.getSessionRepository().getByName(ESession.MORNING);
            Session afternoonSession = repositoryOrchestrator.getSessionRepository().getByName(ESession.AFTERNOON);

            if (morningSession != null) {
                boolean[][] morningBusy = morningSession.getBusyMatrix();
                for (int d = 0; d < 6; d++) {
                    for (int p = 0; p < 5; p++) {
                        if (morningBusy[d][p]) {
                            busyMatrix[d][p] = true; // Busy
                        }
                    }
                }
            }

            if (afternoonSession != null) {
                boolean[][] afternoonBusy = afternoonSession.getBusyMatrix();
                for (int d = 0; d < 6; d++) {
                    for (int p = 5; p < 10; p++) {
                        if (afternoonBusy[d][p]) {
                            busyMatrix[d][p] = true; // Busy
                        }
                    }
                }
            }

            if (chkHomeroom.isSelected() && homeroomClassComboBox.getValue() != null) {
                Clazz homeroomClass = homeroomClassComboBox.getValue();
                Grade grade = repositoryOrchestrator.getGradeRepository().getById(homeroomClass.getGradeId());
                if (grade != null && grade.getSession() != null) {
                    // Fetch full session with busy matrix
                    Session fullSession = repositoryOrchestrator.getSessionRepository().getByName(grade.getSession().getSessionName());
                    if (fullSession != null) {
                        ESession session = fullSession.getSessionName();
                        boolean[][] sessionBusyMatrix = fullSession.getBusyMatrix();

                        if (session == ESession.MORNING) {
                            // Constraint 1: Monday Period 0
                            busyMatrix[0][0] = false;

                            // Constraint 2: Last period of the session
                            int lastDay = -1;
                            int lastPeriod = -1;
                            for (int d = 5; d >= 0; d--) {
                                for (int p = 4; p >= 0; p--) {
                                    if (!sessionBusyMatrix[d][p]) {
                                        lastDay = d;
                                        lastPeriod = p;
                                        break;
                                    }
                                }
                                if (lastDay != -1) break;
                            }
                            if (lastDay != -1 && lastPeriod != -1) {
                                busyMatrix[lastDay][lastPeriod] = false;
                            }
                        } else if (session == ESession.AFTERNOON) {
                            // Constraint 1: Last period of Monday
                            int lastMondayPeriod = -1;
                            for (int p = 9; p >= 5; p--) {
                                if (!sessionBusyMatrix[0][p]) {
                                    lastMondayPeriod = p;
                                    break;
                                }
                            }
                            if (lastMondayPeriod != -1) {
                                busyMatrix[0][lastMondayPeriod] = false;
                            }

                            // Constraint 2: Last period of the session
                            int lastDay = -1;
                            int lastPeriod = -1;
                            for (int d = 5; d >= 0; d--) {
                                for (int p = 9; p >= 5; p--) {
                                    if (!sessionBusyMatrix[d][p]) {
                                        lastDay = d;
                                        lastPeriod = p;
                                        break;
                                    }
                                }
                                if (lastDay != -1) break;
                            }
                            if (lastDay != -1 && lastPeriod != -1) {
                                busyMatrix[lastDay][lastPeriod] = false;
                            }
                        }
                    }
                }
            }

            selected.setBusyMatrix(busyMatrix);
            // timeGridSelector.setBusyMatrix(busyMatrix); // No need to reset UI here, it might flicker

            // Save assignment list from table to Teacher Object
            selected.setAssignments(FXCollections.observableArrayList(currentAssignments));

            // Check capacity before saving
            int morningCapacity = calculateSessionCapacity(busyMatrix, 0, 5);
            int afternoonCapacity = calculateSessionCapacity(busyMatrix, 5, 10);
            int morningAssigned = 0;
            int afternoonAssigned = 0;
            for (Assignment a : currentAssignments) {
                int periods = getPeriodsForAssignment(a);
                Clazz clazz = repositoryOrchestrator.getClassRepository().getById(a.getClassId());
                if (clazz != null) {
                    Grade grade = repositoryOrchestrator.getGradeRepository().getById(clazz.getGradeId());
                    if (grade != null && grade.getSession() != null) {
                        if (grade.getSession().getSessionName() == ESession.MORNING) {
                            morningAssigned += periods;
                        } else if (grade.getSession().getSessionName() == ESession.AFTERNOON) {
                            afternoonAssigned += periods;
                        }
                    }
                }
            }

            if (morningAssigned > morningCapacity || afternoonAssigned > afternoonCapacity) {
                StringBuilder warningMsg = new StringBuilder();
                if (morningAssigned > morningCapacity) {
                    warningMsg.append("Buổi sáng: Cần ").append(morningAssigned).append(" tiết nhưng chỉ có ").append(morningCapacity).append(" tiết.\n");
                }
                if (afternoonAssigned > afternoonCapacity) {
                    warningMsg.append("Buổi chiều: Cần ").append(afternoonAssigned).append(" tiết nhưng chỉ có ").append(afternoonCapacity).append(" tiết.\n");
                }

                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Cảnh báo quá tải");
                alert.setHeaderText("Giáo viên này đang bị quá tải!");
                alert.setContentText(warningMsg.toString() + "\nBạn có chắc chắn muốn lưu không?");

                Optional<ButtonType> result = alert.showAndWait();
                if (result.isEmpty() || result.get() != ButtonType.OK) {
                    return;
                }
            }

            // NEW CONFLICT CHECKS
            List<String> conflicts = validateTeacherConflicts(selected);
            if (!conflicts.isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Phát hiện xung đột");
                alert.setHeaderText("Có các vấn đề tiềm ẩn với lịch của giáo viên này:");

                StringBuilder sb = new StringBuilder();
                for (String s : conflicts) sb.append("- ").append(s).append("\n");
                sb.append("\nBạn có chắc chắn muốn lưu không?");

                TextArea area = new TextArea(sb.toString());
                area.setWrapText(true);
                area.setEditable(false);
                area.setPrefHeight(150);
                alert.getDialogPane().setContent(area);

                Optional<ButtonType> result = alert.showAndWait();
                if (result.isEmpty() || result.get() != ButtonType.OK) {
                    return;
                }
            }

            // Save to DB
            try {
                boolean updated = repositoryOrchestrator.getTeacherRepository().update(selected);
                if (!updated) {
                    repositoryOrchestrator.getTeacherRepository().insert(selected);
                }

                // Handle assignments
                // Delete removed assignments
                for (Assignment a : assignmentsToDelete) {
                    repositoryOrchestrator.getAssignmentRepository().delete(a.getId());
                }
                assignmentsToDelete.clear();

                // Save new/existing assignments
                for (Assignment a : currentAssignments) {
                    try {
                        repositoryOrchestrator.getAssignmentRepository().save(a);
                    } catch (Exception e) {
                        // Ignore if already exists
                        System.out.println("Assignment already exists: " + a.getId());
                    }
                }

                // Handle Homeroom Teacher
                if (!handleHomeroomAssignment(selected)) {
                    // If failed (due to conflict), reload data to revert UI changes
                    showTeacherDetails(selected);
                    return;
                }

                // Check for implicit homeroom assignment from subjects
                checkImplicitHomeroomAssignment(selected);

                refreshTeacherTreeView();

                // Re-select the teacher to update UI and internal state
                findAndSelectTeacher(selected.getId());

                showAlert(Alert.AlertType.INFORMATION, "Thành công", "Đã lưu thông tin giáo viên!");
            } catch (Exception e) {
                showAlert(Alert.AlertType.ERROR, "Lỗi", "Không thể lưu giáo viên: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void findAndSelectTeacher(String teacherId) {
        if (teacherTreeView.getRoot() == null) return;

        for (TreeItem<Object> deptNode : teacherTreeView.getRoot().getChildren()) {
            for (TreeItem<Object> teacherNode : deptNode.getChildren()) {
                if (teacherNode.getValue() instanceof Teacher) {
                    Teacher t = (Teacher) teacherNode.getValue();
                    if (t.getId().equals(teacherId)) {
                        teacherTreeView.getSelectionModel().select(teacherNode);
                        return;
                    }
                }
            }
        }
    }

    private void checkImplicitHomeroomAssignment(Teacher teacher) {
        // Iterate through current assignments to find special subjects
        for (Assignment a : currentAssignments) {
            Subject s = repositoryOrchestrator.getSubjectRepository().getById(a.getSubjectId());
            if (s == null) continue;

            String name = s.getName().toLowerCase();
            boolean isSpecial = s.getId().equals(SubjectConstants.FLAG_SALUTE_ID) ||
                    s.getId().equals(SubjectConstants.CLASS_MEETING_ID) ||
                    name.contains("chào cờ") ||
                    name.contains("sinh hoạt") ||
                    name.contains("shcn");

            if (isSpecial) {
                // Found a special subject assignment. Ensure teacher is homeroom for this class.
                Clazz clazz = repositoryOrchestrator.getClassRepository().getById(a.getClassId());
                if (clazz != null) {
                    // If not already homeroom, assign it (we assume conflicts were checked in handleBatchAdd or handleHomeroomAssignment)
                    if (!teacher.getId().equals(clazz.getHomeroomTeacherId())) {
                        clazz.setHomeroomTeacherId(teacher.getId());
                        repositoryOrchestrator.getClassRepository().save(clazz);

                        // Update UI if needed (though we are likely refreshing or saving)
                        if (!chkHomeroom.isSelected()) {
                            chkHomeroom.setSelected(true);
                            for (Clazz c : homeroomClassComboBox.getItems()) {
                                if (c.getId().equals(clazz.getId())) {
                                    homeroomClassComboBox.setValue(c);
                                    break;
                                }
                            }
                        }

                        // Also ensure the OTHER special subject is assigned
                        assignHomeroomDuties(clazz, teacher);
                    }
                }
            }
        }
    }

    private boolean handleHomeroomAssignment(Teacher teacher) {
        // 1. Clear previous homeroom assignment for this teacher
        Clazz previousClass = repositoryOrchestrator.getClassRepository().findByHomeroomTeacher(teacher.getId());
        if (previousClass != null) {
            // If the teacher was assigned to a class, but now checkbox is unchecked or a different class is selected
            if (!chkHomeroom.isSelected() || (homeroomClassComboBox.getValue() != null && !homeroomClassComboBox.getValue().getId().equals(previousClass.getId()))) {
                previousClass.setHomeroomTeacherId(null);
                repositoryOrchestrator.getClassRepository().save(previousClass);

                // Remove duties from previous class
                removeHomeroomDuties(previousClass, teacher);
            }
        }

        // 2. Assign new homeroom class if selected
        if (chkHomeroom.isSelected() && homeroomClassComboBox.getValue() != null) {
            Clazz selectedClass = homeroomClassComboBox.getValue();

            // Check if this class already has a homeroom teacher (and it's not this teacher)
            Clazz freshClass = repositoryOrchestrator.getClassRepository().getById(selectedClass.getId());

            if (freshClass.getHomeroomTeacherId() != null && !freshClass.getHomeroomTeacherId().equals(teacher.getId())) {
                // Conflict: Class already has a homeroom teacher
                Teacher existingTeacher = repositoryOrchestrator.getTeacherRepository().getById(freshClass.getHomeroomTeacherId());

                // Ask user for confirmation to overwrite
                Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
                confirmAlert.setTitle("Xác nhận thay đổi");
                confirmAlert.setHeaderText("Xung đột giáo viên chủ nhiệm");
                confirmAlert.setContentText("Lớp " + freshClass.getClassName() + " đã có giáo viên chủ nhiệm là " +
                        (existingTeacher != null ? existingTeacher.getName() : "Unknown") +
                        ". Bạn có muốn thay thế bằng giáo viên hiện tại không?");

                Optional<ButtonType> result = confirmAlert.showAndWait();
                if (result.isEmpty() || result.get() != ButtonType.OK) {
                    return false; // Exit if they didn't explicitly say OK
                }
            }

            // Check if teacher is already homeroom for another class (One teacher - One class)
            Clazz otherClass = repositoryOrchestrator.getClassRepository().findByHomeroomTeacher(teacher.getId());
            if (otherClass != null && !otherClass.getId().equals(freshClass.getId())) {
                showAlert(Alert.AlertType.ERROR, "Xung đột", "Giáo viên này đang là chủ nhiệm của lớp " + otherClass.getClassName() + ".");
                return false;
            }

            freshClass.setHomeroomTeacherId(teacher.getId());
            repositoryOrchestrator.getClassRepository().save(freshClass);

            // 3. Automatically assign "Chào cờ" and "Sinh hoạt lớp"
            assignHomeroomDuties(freshClass, teacher);
        }
        return true;
    }

    private void assignHomeroomDuties(Clazz clazz, Teacher teacher) {
        List<Subject> subjects = repositoryOrchestrator.getSubjectRepository().getAll();
        String flagSaluteId = null;
        String classMeetingId = null;

        for (Subject s : subjects) {
            if (s.getId().equals(SubjectConstants.FLAG_SALUTE_ID)) {
                flagSaluteId = s.getId();
            } else if (s.getId().equals(SubjectConstants.CLASS_MEETING_ID)) {
                classMeetingId = s.getId();
            }
            // Fallback to name check if ID doesn't match (for backward compatibility or if IDs are different)
            else {
                String name = s.getName().toLowerCase();
                if (name.contains("chào cờ")) {
                    flagSaluteId = s.getId();
                } else if (name.contains("sinh hoạt") || name.contains("shcn")) {
                    classMeetingId = s.getId();
                }
            }
        }

        if (flagSaluteId != null) {
            assignTeacherToSubject(clazz, teacher, flagSaluteId);
        }

        if (classMeetingId != null) {
            assignTeacherToSubject(clazz, teacher, classMeetingId);
        }
    }

    private void removeHomeroomDuties(Clazz clazz, Teacher teacher) {
        List<Subject> subjects = repositoryOrchestrator.getSubjectRepository().getAll();
        String flagSaluteId = null;
        String classMeetingId = null;

        for (Subject s : subjects) {
            if (s.getId().equals(SubjectConstants.FLAG_SALUTE_ID)) {
                flagSaluteId = s.getId();
            } else if (s.getId().equals(SubjectConstants.CLASS_MEETING_ID)) {
                classMeetingId = s.getId();
            } else {
                String name = s.getName().toLowerCase();
                if (name.contains("chào cờ")) {
                    flagSaluteId = s.getId();
                } else if (name.contains("sinh hoạt") || name.contains("shcn")) {
                    classMeetingId = s.getId();
                }
            }
        }

        if (flagSaluteId != null) {
            removeTeacherFromSubject(clazz, teacher, flagSaluteId);
        }

        if (classMeetingId != null) {
            removeTeacherFromSubject(clazz, teacher, classMeetingId);
        }
    }

    private void assignTeacherToSubject(Clazz clazz, Teacher teacher, String subjectId) {
        // Check if assignment exists in DB
        Assignment existing = repositoryOrchestrator.getAssignmentRepository().getByClassAndSubject(clazz.getId(), subjectId);
        if (existing != null) {
            existing.setTeacherId(teacher.getId());
            repositoryOrchestrator.getAssignmentRepository().save(existing);
        } else {
            Assignment newAssignment = new Assignment(UUID.randomUUID().toString(), teacher.getId(), subjectId, clazz.getId());
            repositoryOrchestrator.getAssignmentRepository().save(newAssignment);
            // Also add to current view if not present
            boolean inView = currentAssignments.stream().anyMatch(a -> a.getId().equals(newAssignment.getId()));
            if (!inView) {
                currentAssignments.add(newAssignment);
            }
        }
    }

    private void removeTeacherFromSubject(Clazz clazz, Teacher teacher, String subjectId) {
        Assignment existing = repositoryOrchestrator.getAssignmentRepository().getByClassAndSubject(clazz.getId(), subjectId);
        if (existing != null && existing.getTeacherId().equals(teacher.getId())) {
            repositoryOrchestrator.getAssignmentRepository().delete(existing.getId());
            currentAssignments.removeIf(a -> a.getId().equals(existing.getId()));
        }
    }

    private void deleteTeacher() {
        TreeItem<Object> selectedItem = teacherTreeView.getSelectionModel().getSelectedItem();
        if (selectedItem != null && selectedItem.getValue() instanceof Teacher) {
            Teacher selected = (Teacher) selectedItem.getValue();
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Xoá giáo viên " + selected + "?", ButtonType.YES, ButtonType.NO);
            alert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.YES) {
                    // Unassign homeroom if any
                    Clazz homeroomClass = repositoryOrchestrator.getClassRepository().findByHomeroomTeacher(selected.getId());
                    if (homeroomClass != null) {
                        homeroomClass.setHomeroomTeacherId(null);
                        repositoryOrchestrator.getClassRepository().save(homeroomClass);
                    }

                    // Delete assignments first
                    repositoryOrchestrator.getAssignmentRepository().deleteByTeacherId(selected.getId());
                    // Delete teacher
                    repositoryOrchestrator.getTeacherRepository().delete(selected.getId());

                    teacherList.remove(selected);
                    refreshTeacherTreeView();

                    // Clear selection
                    selectedTeacher = null;
                    nameField.clear();
                    codeField.clear();
                    // Reset other fields...
                }
            });
        }
    }

    // --- HELPER: Create delete button in table ---
    private void addButtonToTable() {
        Callback<TableColumn<Assignment, Void>, TableCell<Assignment, Void>> cellFactory = new Callback<>() {
            @Override
            public TableCell<Assignment, Void> call(final TableColumn<Assignment, Void> param) {
                return new TableCell<>() {
                    private final Button btn = new Button("X");

                    {
                        btn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-size: 10px;");
                        btn.setOnAction(event -> {
                            Assignment data = getTableView().getItems().get(getIndex());
                            currentAssignments.remove(data); // Remove from list -> Automatically updates table & Total periods
                            assignmentsToDelete.add(data); // Mark for deletion
                        });
                    }

                    @Override
                    public void updateItem(Void item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty) {
                            setGraphic(null);
                        } else {
                            setGraphic(btn);
                        }
                    }
                };
            }
        };
        colAction.setCellFactory(cellFactory);
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.show();
    }

    private void loadData() {
        List<Subject> subjects = repositoryOrchestrator.getSubjectRepository().getAll();

        // Allow all subjects, including special ones
        subjectComboBox.setItems(FXCollections.observableArrayList(subjects));

        List<Clazz> classes = repositoryOrchestrator.getClassRepository().getAll();
        multiClassListView.setItems(FXCollections.observableArrayList(classes));
        homeroomClassComboBox.setItems(FXCollections.observableArrayList(classes));

        loadDepartments();

        List<Teacher> teachers = repositoryOrchestrator.getTeacherRepository().getAll();
        teacherList.addAll(teachers);

        refreshTeacherTreeView();
    }

    private void loadDepartments() {
        List<Department> departments = repositoryOrchestrator.getDepartmentRepository().getAll();
        departmentComboBox.setItems(FXCollections.observableArrayList(departments));
    }

    private List<String> validateTeacherConflicts(Teacher teacher) {
        List<Teacher> allTeachers = repositoryOrchestrator.getTeacherRepository().getAll();
        return scheduleValidator.validateTeacherConflicts(teacher, allTeachers, currentAssignments);
    }
}