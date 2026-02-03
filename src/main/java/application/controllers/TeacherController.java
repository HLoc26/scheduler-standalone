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
import scheduler.common.constants.SubjectConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

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
    // --- Homeroom Teacher Controls ---
    @FXML
    private CheckBox chkHomeroom;
    @FXML
    private ComboBox<Clazz> homeroomClassComboBox;

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
        setupHomeroomControls();

        loadData(); // Will be replaced by load from DB
        Platform.runLater(() -> root.setDividerPosition(0, 0.2));
        
        // Check for implicit homeroom assignments on startup
        Platform.runLater(this::checkAllImplicitHomeroomAssignments);
    }
    
    private void checkAllImplicitHomeroomAssignments() {
        // This method scans all assignments in the DB and ensures homeroom consistency
        List<Assignment> allAssignments = repositoryOrchestrator.getAssignmentRepository().getAll();
        List<Subject> subjects = repositoryOrchestrator.getSubjectRepository().getAll();
        
        for (Assignment a : allAssignments) {
            Subject s = subjects.stream().filter(sub -> sub.getId().equals(a.getSubjectId())).findFirst().orElse(null);
            if (s == null) continue;
            
            String name = s.getName().toLowerCase();
            boolean isSpecial = s.getId().equals(SubjectConstants.FLAG_SALUTE_ID) ||
                                s.getId().equals(SubjectConstants.CLASS_MEETING_ID) ||
                                name.contains("chào cờ") || 
                                name.contains("sinh hoạt") || 
                                name.contains("shcn");
                                
            if (isSpecial) {
                Clazz clazz = repositoryOrchestrator.getClassRepository().getById(a.getClassId());
                if (clazz != null) {
                    // If class has no homeroom teacher, or different one, update it
                    // Note: This auto-fix might overwrite existing homeroom if data is inconsistent.
                    // Assuming special subject assignment implies homeroom duty.
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

    private void setupHomeroomControls() {
        chkHomeroom.selectedProperty().addListener((obs, oldVal, newVal) -> {
            homeroomClassComboBox.setDisable(!newVal);
            if (!newVal) {
                homeroomClassComboBox.setValue(null);
            }
        });
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

        // Check if user is trying to manually assign special subjects
        String subjectName = subject.getName().toLowerCase();
        boolean isSpecialSubject = subject.getId().equals(SubjectConstants.FLAG_SALUTE_ID) ||
                                   subject.getId().equals(SubjectConstants.CLASS_MEETING_ID) ||
                                   subjectName.contains("chào cờ") || 
                                   subjectName.contains("sinh hoạt") || 
                                   subjectName.contains("shcn");

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
                // Note: We need fresh data from DB for accurate check
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
                        // We don't need to do anything special here, as the save logic will handle the update.
                        // However, we should probably update the UI to reflect this change if needed.
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
                // For UI feedback, we can update the homeroom controls immediately if single class selected
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

                // Handle Homeroom Teacher
                if (!handleHomeroomAssignment(selected)) {
                    // If failed (due to conflict), reload data to revert UI changes
                    showTeacherDetails(selected);
                    return;
                }
                
                // Check for implicit homeroom assignment from subjects
                checkImplicitHomeroomAssignment(selected);

                teacherListView.refresh();
                showAlert(Alert.AlertType.INFORMATION, "Thành công", "Đã lưu thông tin giáo viên!");
            } catch (Exception e) {
                showAlert(Alert.AlertType.ERROR, "Lỗi", "Không thể lưu giáo viên: " + e.getMessage());
                e.printStackTrace();
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
            // Note: The object in ComboBox might be stale, so fetch fresh from DB or trust the ID
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
                 if (result.isPresent() && result.get() == ButtonType.OK) {
                     // User confirmed overwrite. Proceed with assignment.
                 } else {
                     return false; // User cancelled
                 }
            }
            
            // Check if teacher is already homeroom for another class (Constraint 1: One teacher - One class)
            // This is implicitly handled because we cleared previousClass above.
            // But let's double check if there's any other class pointing to this teacher (shouldn't happen if DB is consistent)
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
        Teacher selected = teacherListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
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
        
        // Allow all subjects, including special ones
        subjectComboBox.setItems(FXCollections.observableArrayList(subjects));

        List<Clazz> classes = repositoryOrchestrator.getClassRepository().getAll();
        multiClassListView.setItems(FXCollections.observableArrayList(classes));
        homeroomClassComboBox.setItems(FXCollections.observableArrayList(classes));

        List<Teacher> teachers = repositoryOrchestrator.getTeacherRepository().getAll();
        teacherList.addAll(teachers);
    }
}