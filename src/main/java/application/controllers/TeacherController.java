package application.controllers;

import application.models.*;
import application.repository.RepositoryOrchestrator;
import application.views.TimeGridSelector;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.util.Callback;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class TeacherController {

    private final RepositoryOrchestrator repositoryOrchestrator;
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
    private ListView<Teacher> teacherListView;
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
    // --- Data & Logic ---
    private TimeGridSelector timeGridSelector;

    public TeacherController(RepositoryOrchestrator repositoryOrchestrator) {
        this.repositoryOrchestrator = repositoryOrchestrator;
    }

    public void initialize() {
        setupTimeGrid();
        setupTeacherList();
        setupAssignmentForm(); // Setup logic for assignment
        setupButtons();

        loadData(); // Will be replaced by load from DB
        Platform.runLater(() -> root.setDividerPosition(0, 0.2));
    }

    private void setupTimeGrid() {
        timeGridSelector = new TimeGridSelector();
        timeGridContainer.getChildren().add(timeGridSelector);
    }

    private void setupTeacherList() {
        teacherListView.setItems(teacherList);
        teacherListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> showTeacherDetails(newVal)
        );
        Platform.runLater(() -> teacherListView.getSelectionModel().select(0));
    }

    private void setupButtons() {
        btnAdd.setOnAction(e -> createNewTeacher());
        btnSave.setOnAction(e -> saveCurrentTeacher());
        btnDelete.setOnAction(e -> deleteTeacher());

        // Batch Add button event
        btnAddBatch.setOnAction(e -> handleBatchAdd());
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
        currentAssignments.addListener((ListChangeListener<Assignment>) c -> updateTotalPeriods());
    }

    // --- ASSIGNMENT LOGIC (Batch Add) ---
    private void handleBatchAdd() {

        Subject subject = subjectComboBox.getValue();
        ObservableList<Clazz> selectedClasses = multiClassListView.getSelectionModel().getSelectedItems();


        if (subject == null || selectedClasses.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Thiếu thông tin", "Vui lòng chọn Môn học và ít nhất một Lớp học.");
            return;
        }

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

        int total = 0;
        for (Assignment assignment : currentAssignments) {
            total += getPeriodsForAssignment(assignment);
        }
        totalPeriodsLabel.setText("Tổng số tiết: " + total);

        // Red warning if teaching too many periods (e.g., > 20 periods)
        if (total > 20) {
            totalPeriodsLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
            totalPeriodsLabel.setText(totalPeriodsLabel.getText() + " (QUÁ TẢI)");
        } else {
            totalPeriodsLabel.setStyle("-fx-text-fill: black; -fx-font-weight: bold;");
        }
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
            teacherListView.getSelectionModel().select(newTeacher);
            nameField.requestFocus();
        });
    }

    private void saveCurrentTeacher() {
        Teacher selected = teacherListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            selected.setName(nameField.getText());
            selected.setId(codeField.getText());
            selected.setBusyMatrix(timeGridSelector.getBusyMatrix());

            // Save assignment list from table to Teacher Object
            selected.setAssignments(FXCollections.observableArrayList(currentAssignments));

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

                teacherListView.refresh();
                showAlert(Alert.AlertType.INFORMATION, "Thành công", "Đã lưu thông tin giáo viên!");
            } catch (Exception e) {
                showAlert(Alert.AlertType.ERROR, "Lỗi", "Không thể lưu giáo viên: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void deleteTeacher() {
        Teacher selected = teacherListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Xoá giáo viên " + selected + "?", ButtonType.YES, ButtonType.NO);
            alert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.YES) {
                    // Delete assignments first
                    repositoryOrchestrator.getAssignmentRepository().deleteByTeacherId(selected.getId());
                    // Delete teacher
                    repositoryOrchestrator.getTeacherRepository().delete(selected.getId());

                    teacherList.remove(selected);
                    teacherListView.getSelectionModel().clearSelection();
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
        subjectComboBox.setItems(FXCollections.observableArrayList(subjects));

        List<Clazz> classes = repositoryOrchestrator.getClassRepository().getAll();
        multiClassListView.setItems(FXCollections.observableArrayList(classes));

        List<Teacher> teachers = repositoryOrchestrator.getTeacherRepository().getAll();
        teacherList.addAll(teachers);
    }
}