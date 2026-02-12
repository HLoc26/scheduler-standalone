package application.controllers;

import application.models.Assignment;
import application.models.Clazz;
import application.models.Subject;
import application.models.Teacher;
import application.repository.RepositoryOrchestrator;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import scheduler.common.constants.SubjectConstants;

import java.text.Collator;
import java.util.*;
import java.util.stream.Collectors;

public class AssignmentController {

    private final RepositoryOrchestrator repo;
    // --- Pending Changes (Waiting to be saved) ---
    // Key = "subjectId_classId", Value = Assignment Object (New state)
    private final Map<String, Assignment> pendingChanges = new HashMap<>();
    @FXML
    private GridPane assignmentGrid;
    @FXML
    private ToggleButton tglQuickMode;
    @FXML
    private ComboBox<Teacher> cbQuickTeacher;
    @FXML
    private Button btnCancelChanges;
    // --- Data Cache (Loaded from DB) ---
    private List<Subject> subjects;
    private List<Clazz> classes;
    private List<Teacher> teachers;
    // Cache map for fast lookup: Key = "subjectId_classId", Value = Assignment Object
    private Map<String, Assignment> assignmentDbCache;
    private boolean isTransposed = false;

    public AssignmentController(RepositoryOrchestrator repo) {
        this.repo = repo;
    }

    public void initialize() {
        loadDataFromDb();
        setupQuickModeControls();
        buildGrid();
        updateCancelButtonVisibility();
    }

    /**
     * Loads all necessary data from the database.
     */
    private void loadDataFromDb() {
        // Filter out special subjects
        subjects = repo.getSubjectRepository().getAll().stream()
                .filter(s -> {
                    String name = s.getName().toLowerCase();
                    return !s.getId().equals(SubjectConstants.FLAG_SALUTE_ID) &&
                            !s.getId().equals(SubjectConstants.CLASS_MEETING_ID) &&
                            !name.contains("chào cờ") &&
                            !name.contains("sinh hoạt") &&
                            !name.contains("shcn");
                })
                .collect(Collectors.toList());

        // Add a virtual subject for Homeroom Duty
        subjects.addFirst(new Subject(SubjectConstants.HOMEROOM_SUBJECT_ID, "GVCN"));

        classes = repo.getClassRepository().getAll();
        teachers = repo.getTeacherRepository().getAll();

        // Load existing assignments and convert to Map for O(1) access
        List<Assignment> dbAssignments = repo.getAssignmentRepository().getAll();
        assignmentDbCache = dbAssignments.stream()
                .collect(Collectors.toMap(
                        a -> genKey(a.getSubjectId(), a.getClassId()),
                        a -> a,
                        (existing, replacement) -> existing // Handle duplicates if any
                ));

        // Add virtual assignments for Homeroom Teachers
        for (Clazz c : classes) {
            if (c.getHomeroomTeacherId() != null) {
                String key = genKey(SubjectConstants.HOMEROOM_SUBJECT_ID, c.getId());
                Assignment a = new Assignment(UUID.randomUUID().toString(), c.getHomeroomTeacherId(), SubjectConstants.HOMEROOM_SUBJECT_ID, c.getId());
                assignmentDbCache.put(key, a);
            }
        }
    }

    /**
     * Configures the Quick Mode Toolbar (ComboBox for Teachers).
     */
    private void setupQuickModeControls() {
        // Group teachers by department for display
        Collator collator = Collator.getInstance(new Locale("vi", "VN"));
        teachers.sort(Comparator.comparing((Teacher t) -> {
            if (t.getDepartment() != null) return t.getDepartment().getName();
            return "zzzz"; // Put "No Department" at the end
        }, collator).thenComparing(Teacher::getName, collator));

        cbQuickTeacher.setItems(FXCollections.observableArrayList(teachers));

        // Use a custom cell factory to add separators/headers
        Callback<ListView<Teacher>, ListCell<Teacher>> cellFactory = lv -> new ListCell<Teacher>() {
            @Override
            protected void updateItem(Teacher item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.getName());

                    // Check if this is the first item of a new group
                    int index = getIndex();
                    boolean isFirstInGroup = false;
                    if (index == 0) {
                        isFirstInGroup = true;
                    } else if (index > 0 && index < getListView().getItems().size()) {
                        Teacher prevItem = getListView().getItems().get(index - 1);
                        String currentDept = (item.getDepartment() != null) ? item.getDepartment().getName() : "Chưa phân tổ";
                        String prevDept = (prevItem.getDepartment() != null) ? prevItem.getDepartment().getName() : "Chưa phân tổ";
                        if (!currentDept.equals(prevDept)) {
                            isFirstInGroup = true;
                        }
                    }

                    if (isFirstInGroup) {
                        String deptName = (item.getDepartment() != null) ? item.getDepartment().getName() : "Chưa phân tổ";
                        Label header = new Label(deptName);
                        header.setStyle("-fx-font-weight: bold; -fx-text-fill: #2c3e50; -fx-padding: 5 0 2 0; -fx-background-color: #ecf0f1;");
                        header.setMaxWidth(Double.MAX_VALUE);

                        VBox container = new VBox(header, new Label("  " + item.getName()));
                        setGraphic(container);
                        setText(null);
                    } else {
                        setGraphic(null);
                        setText("  " + item.getName()); // Indent slightly
                    }
                }
            }
        };

        cbQuickTeacher.setCellFactory(cellFactory);

        cbQuickTeacher.setButtonCell(new ListCell<Teacher>() {
            @Override
            protected void updateItem(Teacher item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getName());
                }
            }
        });

        // Default state: Disabled until toggle is ON
        cbQuickTeacher.setDisable(true);
    }

    /**
     * Handled toggle button for Quick Mode.
     */
    @FXML
    public void onToggleQuickMode() {
        boolean isOn = tglQuickMode.isSelected();
        if (isOn) {
            tglQuickMode.setText("BẬT");
            tglQuickMode.setStyle("-fx-base: #22c55e; -fx-text-fill: white; -fx-font-weight: bold;"); // Green
            cbQuickTeacher.setDisable(false);
            updateCancelButtonVisibility();
        } else {
            tglQuickMode.setText("TẮT");
            tglQuickMode.setStyle("-fx-base: #cbd5e1; -fx-text-fill: black; -fx-font-weight: bold;"); // Gray
            cbQuickTeacher.setDisable(true);
            cbQuickTeacher.getSelectionModel().clearSelection();
            btnCancelChanges.setVisible(false);
        }
    }

    private void updateCancelButtonVisibility() {
        if (tglQuickMode.isSelected() && !pendingChanges.isEmpty()) {
            btnCancelChanges.setVisible(true);
        } else {
            btnCancelChanges.setVisible(false);
        }
    }

    @FXML
    public void handleCancelChanges() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Xác nhận hủy");
        alert.setHeaderText("Hủy bỏ tất cả thay đổi?");
        alert.setContentText("Bạn có chắc chắn muốn hủy bỏ tất cả các thay đổi chưa lưu không?");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            pendingChanges.clear();
            buildGrid();
            updateCancelButtonVisibility();
        }
    }

    /**
     * Rebuilds the entire Matrix Grid.
     */

    private void buildGrid() {
        assignmentGrid.getChildren().clear();
        assignmentGrid.getColumnConstraints().clear();
        assignmentGrid.getRowConstraints().clear();
        // A. Top-left corner
        String cornerText = isTransposed ? "Lớp \\ Môn" : "Môn \\ Lớp";
        StackPane corner = new StackPane(new Label(cornerText));
        corner.setStyle("-fx-background-color: #94a3b8; -fx-padding: 5;");
        assignmentGrid.add(corner, 0, 0);

        // B. Determine which list is for Rows and which is for Columns
        List<?> rowEntities = isTransposed ? classes : subjects;
        List<?> colEntities = isTransposed ? subjects : classes;
        ColumnConstraints headerCol = new ColumnConstraints();
        headerCol.setMinWidth(50);
        headerCol.setPrefWidth(100);
        assignmentGrid.getColumnConstraints().add(headerCol);

        double COLUMN_WIDTH = 100.0;
        for (int i = 0; i < colEntities.size(); i++) {
            ColumnConstraints colConst = new ColumnConstraints();
            colConst.setMinWidth(COLUMN_WIDTH);
            colConst.setPrefWidth(COLUMN_WIDTH);
            colConst.setMaxWidth(COLUMN_WIDTH);
            assignmentGrid.getColumnConstraints().add(colConst);
        }

        // C. Draw Row Headers (Column 0)
        for (int r = 0; r < rowEntities.size(); r++) {
            Object entity = rowEntities.get(r);
            String labelText = getNameOf(entity);

            Label lbl = new Label(labelText);
            lbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #1e293b;");

            StackPane cell = new StackPane(lbl);
            cell.setPrefHeight(50);
//            cell.setPrefWidth(150); // Row header should be a bit wide
            cell.setStyle("-fx-background-color: #e2e8f0; -fx-border-color: #cbd5e1;");

            assignmentGrid.add(cell, 0, r + 1);
        }

        // D. Draw Column Headers (Row 0)
        for (int c = 0; c < colEntities.size(); c++) {
            Object entity = colEntities.get(c);
            String labelText = getNameOf(entity);

            Label lbl = new Label(labelText);
            lbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #1e293b;");

            StackPane cell = new StackPane(lbl);
            cell.setPrefWidth(40);
            cell.setStyle("-fx-background-color: #e2e8f0; -fx-border-color: #cbd5e1;");

            assignmentGrid.add(cell, c + 1, 0);
        }

        // E. Draw data cells
        for (int r = 0; r < rowEntities.size(); r++) {
            for (int c = 0; c < colEntities.size(); c++) {

                // Logic to determine Subject and Class based on r, c coordinates
                Subject subject;
                Clazz clazz;

                if (isTransposed) {
                    // Row is Class, Column is Subject
                    clazz = (Clazz) rowEntities.get(r);
                    subject = (Subject) colEntities.get(c);
                } else {
                    // Row is Subject, Column is Class (Default)
                    subject = (Subject) rowEntities.get(r);
                    clazz = (Clazz) colEntities.get(c);
                }

                // Call the cell creation function as before (no need to modify createAssignmentCell)
                // Note: r + 1 and c + 1 are the display positions on the Grid
                createAssignmentCell(r + 1, c + 1, subject, clazz);
            }
        }
    }

    // Helper to get display name
    private String getNameOf(Object entity) {
        if (entity instanceof Subject) return ((Subject) entity).getName();
        if (entity instanceof Clazz) return ((Clazz) entity).getClassName();
        return "?";
    }

    /**
     * Creates a single cell in the matrix.
     */
    private void createAssignmentCell(int row, int col, Subject subject, Clazz clazz) {
        String key = genKey(subject.getId(), clazz.getId());

        // Determine the Teacher to display:
        // Priority 1: Pending Change (Unsaved)
        // Priority 2: Database Cache (Saved)
        Assignment effectiveAssignment = null;
        boolean isPending = false;

        if (pendingChanges.containsKey(key)) {
            effectiveAssignment = pendingChanges.get(key);
            isPending = true;
        } else {
            effectiveAssignment = assignmentDbCache.get(key);
        }

        Teacher displayTeacher;
        if (effectiveAssignment != null) {
            displayTeacher = findTeacherById(effectiveAssignment.getTeacherId());
        } else {
            displayTeacher = null;
        }

        // Create UI Components
        VBox cell = new VBox();
        cell.setAlignment(Pos.CENTER);
        cell.setPrefSize(120, 50); // Fixed size for uniformity

        Label lblTeacher = new Label();
        styleCell(cell, lblTeacher, displayTeacher, isPending);

        // --- Interaction Logic ---
        cell.setOnMouseClicked(e -> {
            if (tglQuickMode.isSelected()) {
                // Quick Mode: Assign immediately from Toolbar
                Teacher selected = cbQuickTeacher.getValue();
                if (selected != null) {
                    handleLocalUpdate(cell, lblTeacher, subject, clazz, selected);
                } else {
                    showAlert(Alert.AlertType.WARNING, "Chưa chọn giáo viên!", "Vui lòng chọn giáo viên ở thanh công cụ phía trên.");
                }
            } else {
                // Normal Mode: Show Dialog
                showSelectTeacherDialog(cell, lblTeacher, subject, clazz, displayTeacher);
            }
        });

        cell.getChildren().add(lblTeacher);
        assignmentGrid.add(cell, col, row);
    }

    /**
     * Handles updating the "Pending Map" and UI when a user assigns a teacher.
     */
    private void handleLocalUpdate(VBox cell, Label label, Subject s, Clazz c, Teacher t) {
        String key = genKey(s.getId(), c.getId());

        // Check if we are toggling off the change (clicking again with same teacher)
        if (pendingChanges.containsKey(key)) {
            Assignment pending = pendingChanges.get(key);
            if (pending.getTeacherId().equals(t.getId())) {
                // Revert change
                pendingChanges.remove(key);

                // Restore original state
                Assignment original = assignmentDbCache.get(key);
                Teacher originalTeacher = (original != null) ? findTeacherById(original.getTeacherId()) : null;

                styleCell(cell, label, originalTeacher, false);
                updateCancelButtonVisibility();
                return;
            }
        }

        // --- HOMEROOM CONSTRAINT CHECK ---
        if (s.getId().equals(SubjectConstants.HOMEROOM_SUBJECT_ID)) {
            // Check if class already has a homeroom teacher (in Pending and DB) by proposing a NEW teacher 't' for class 'c'
            // Check if 't' is already homeroom for another class
            for (Map.Entry<String, Assignment> entry : pendingChanges.entrySet()) {
                if (entry.getKey().startsWith(SubjectConstants.HOMEROOM_SUBJECT_ID + "_")) {
                    if (entry.getValue().getTeacherId().equals(t.getId()) && !entry.getValue().getClassId().equals(c.getId())) {
                        showAlert(Alert.AlertType.ERROR, "Xung đột", "Giáo viên " + t.getName() + " đang làm chủ nhiệm lớp khác trong các thay đổi chưa lưu.");
                        return;
                    }
                }
            }
            // Scan DB
            Clazz otherClass = repo.getClassRepository().findByHomeroomTeacher(t.getId());
            if (otherClass != null && !otherClass.getId().equals(c.getId())) {
                // Check if we have a pending change that REMOVES this teacher from 'otherClass'
                String otherKey = genKey(SubjectConstants.HOMEROOM_SUBJECT_ID, otherClass.getId());
                if (!pendingChanges.containsKey(otherKey)) {
                    showAlert(Alert.AlertType.ERROR, "Xung đột", "Giáo viên " + t.getName() + " đang là chủ nhiệm của lớp " + otherClass.getClassName() + ".");
                    return;
                }
            }
        }

        // Create new Assignment Object
        Assignment assignment = new Assignment();
        // Check if we are updating an existing DB record to keep the ID, else gen new UUID
        Assignment existingInDb = assignmentDbCache.get(key);
        if (existingInDb != null) {
            assignment.setId(existingInDb.getId());
        } else {
            assignment.setId(UUID.randomUUID().toString());
        }

        assignment.setSubjectId(s.getId());
        assignment.setClassId(c.getId());
        assignment.setTeacherId(t.getId());

        // Add to Pending
        pendingChanges.put(key, assignment);

        // Update UI Visuals immediately
        styleCell(cell, label, t, true);
        updateCancelButtonVisibility();
    }

    /**
     * Styles the cell based on state (Assigned, Unassigned, Pending).
     */
    private void styleCell(VBox cell, Label label, Teacher t, boolean isPending) {
        if (t == null) {
            label.setText("Empty");
            label.setStyle("-fx-text-fill: #ef4444; -fx-font-style: italic; -fx-font-size: 11px;");
            cell.setStyle("-fx-background-color: #fff1f2; -fx-border-color: #ffe4e6; -fx-cursor: hand;"); // Red tint
        } else {
            if (isPending) {
                label.setText(t.getName() + " (*)");
                label.setStyle("-fx-text-fill: #854d0e; -fx-font-weight: bold;"); // Dark Yellow text
                cell.setStyle("-fx-background-color: #fef9c3; -fx-border-color: #eab308; -fx-border-width: 1.5; -fx-cursor: hand;"); // Yellow bg
            } else {
                label.setText(t.getName());
                label.setStyle("-fx-text-fill: #0f172a;");
                cell.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-cursor: hand;");
            }
        }
    }

    /**
     * Shows a dialog to select a teacher.
     */
    private void showSelectTeacherDialog(VBox cell, Label label, Subject s, Clazz c, Teacher current) {
        // Filter teachers if subject is qualified for a department
        // But for now, we just show all teachers, maybe sorted by department

        // We can use a custom dialog with a TreeView or grouped ListView, but ChoiceDialog is simple.
        // Let's sort the list by department first.
        Collator collator = Collator.getInstance(new Locale("vi", "VN"));
        List<Teacher> sortedTeachers = new ArrayList<>(teachers);
        sortedTeachers.sort(Comparator.comparing((Teacher t) -> {
            if (t.getDepartment() != null) return t.getDepartment().getName();
            return "zzzz";
        }, collator).thenComparing(Teacher::getName, collator));

        ChoiceDialog<Teacher> dialog = new ChoiceDialog<>(current, sortedTeachers);
        dialog.setTitle("Assign Teacher");
        dialog.setHeaderText("Subject: " + s.getName() + " - Class: " + c.getClassName());
        dialog.setContentText("Select Teacher:");

        // Fix display in ComboBox inside Dialog
        ComboBox<Teacher> combo = (ComboBox<Teacher>) dialog.getDialogPane().lookup(".combo-box");
        if (combo != null) {
            // Use same cell factory logic as quick mode
            Callback<ListView<Teacher>, ListCell<Teacher>> cellFactory = lv -> new ListCell<Teacher>() {
                @Override
                protected void updateItem(Teacher item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                    } else {
                        setText(item.getName());

                        // Check if this is the first item of a new group
                        int index = getIndex();
                        boolean isFirstInGroup = false;
                        if (index == 0) {
                            isFirstInGroup = true;
                        } else if (index > 0 && index < getListView().getItems().size()) {
                            Teacher prevItem = getListView().getItems().get(index - 1);
                            String currentDept = (item.getDepartment() != null) ? item.getDepartment().getName() : "Chưa phân tổ";
                            String prevDept = (prevItem.getDepartment() != null) ? prevItem.getDepartment().getName() : "Chưa phân tổ";
                            if (!currentDept.equals(prevDept)) {
                                isFirstInGroup = true;
                            }
                        }

                        if (isFirstInGroup) {
                            String deptName = (item.getDepartment() != null) ? item.getDepartment().getName() : "Chưa phân tổ";
                            Label header = new Label(deptName);
                            header.setStyle("-fx-font-weight: bold; -fx-text-fill: #2c3e50; -fx-padding: 5 0 2 0; -fx-background-color: #ecf0f1;");
                            header.setMaxWidth(Double.MAX_VALUE);

                            VBox container = new VBox(header, new Label("  " + item.getName()));
                            setGraphic(container);
                            setText(null);
                        } else {
                            setGraphic(null);
                            setText("  " + item.getName()); // Indent slightly
                        }
                    }
                }
            };

            combo.setCellFactory(cellFactory);
            combo.setButtonCell(new ListCell<Teacher>() {
                @Override
                protected void updateItem(Teacher item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        setText(item.getName());
                    }
                }
            });
        }

        Optional<Teacher> result = dialog.showAndWait();
        result.ifPresent(selectedTeacher -> {
            handleLocalUpdate(cell, label, s, c, selectedTeacher);
        });
    }

    /**
     * Triggered by "Save Changes" button in FXML.
     */
    @FXML
    public void handleSaveChanges() {
        if (pendingChanges.isEmpty()) {
            showAlert(Alert.AlertType.INFORMATION, "Thông báo", "Không có thay đổi nào để lưu.");
            return;
        }

        try {
            // Separate normal assignments from Homeroom assignments
            List<Assignment> normalAssignments = new ArrayList<>();
            Map<String, String> homeroomUpdates = new HashMap<>(); // ClassID -> TeacherID

            for (Assignment a : pendingChanges.values()) {
                if (a.getSubjectId().equals(SubjectConstants.HOMEROOM_SUBJECT_ID)) {
                    homeroomUpdates.put(a.getClassId(), a.getTeacherId());
                } else {
                    normalAssignments.add(a);
                }
            }

            // 1. Save normal assignments
            if (!normalAssignments.isEmpty()) {
                repo.getAssignmentRepository().saveAll(normalAssignments);
            }

            // 2. Save Homeroom updates
            for (Map.Entry<String, String> entry : homeroomUpdates.entrySet()) {
                String classId = entry.getKey();
                String teacherId = entry.getValue();

                Clazz clazz = repo.getClassRepository().getById(classId);
                if (clazz != null) {
                    // Check constraint again before saving (just in case)
                    Clazz existingClass = repo.getClassRepository().findByHomeroomTeacher(teacherId);
                    if (existingClass != null && !existingClass.getId().equals(classId)) {
                        // Conflict found during save (rare if UI check works, but possible)
                        // For now, we overwrite or skip. Let's overwrite to respect user's latest intent.
                        existingClass.setHomeroomTeacherId(null);
                        repo.getClassRepository().save(existingClass);
                    }

                    clazz.setHomeroomTeacherId(teacherId);
                    repo.getClassRepository().save(clazz);

                    // Auto-assign special subjects
                    assignHomeroomDuties(clazz, teacherId);
                }
            }

            // 3. Clear pending
            pendingChanges.clear();

            // 4. Reload from DB to verify and reset UI
            loadDataFromDb();
            buildGrid();
            updateCancelButtonVisibility();

            showAlert(Alert.AlertType.INFORMATION, "Success", "Saved changes successfully!");

        } catch (Exception e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Error", "Save failed: " + e.getMessage());
        }
    }

    private void assignHomeroomDuties(Clazz clazz, String teacherId) {
        // Similar logic to TeacherController
        List<Subject> allSubjects = repo.getSubjectRepository().getAll();
        String flagSaluteId = null;
        String classMeetingId = null;

        for (Subject s : allSubjects) {
            if (s.getId().equals(SubjectConstants.FLAG_SALUTE_ID)) flagSaluteId = s.getId();
            else if (s.getId().equals(SubjectConstants.CLASS_MEETING_ID)) classMeetingId = s.getId();
            else {
                String name = s.getName().toLowerCase();
                if (name.contains("chào cờ")) flagSaluteId = s.getId();
                else if (name.contains("sinh hoạt") || name.contains("shcn")) classMeetingId = s.getId();
            }
        }

        if (flagSaluteId != null) saveAssignment(clazz.getId(), teacherId, flagSaluteId);
        if (classMeetingId != null) saveAssignment(clazz.getId(), teacherId, classMeetingId);
    }

    private void saveAssignment(String classId, String teacherId, String subjectId) {
        Assignment existing = repo.getAssignmentRepository().getByClassAndSubject(classId, subjectId);
        if (existing != null) {
            existing.setTeacherId(teacherId);
            repo.getAssignmentRepository().save(existing);
        } else {
            Assignment newAssignment = new Assignment(UUID.randomUUID().toString(), teacherId, subjectId, classId);
            repo.getAssignmentRepository().save(newAssignment);
        }
    }

    @FXML
    public void handleDeleteAll() {
        // Level 1 Warning
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("CẢNH BÁO NGUY HIỂM");
        alert.setHeaderText("XOÁ TẤT CẢ PHÂN CÔNG?");
        alert.setContentText("Bạn có chắc chắn muốn xoá tất cả phân công giảng dạy không? Hành động này sẽ xoá toàn bộ dữ liệu phân công hiện tại.");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            // Level 2 Warning: Require typing "Delete"
            TextInputDialog confirmDialog = new TextInputDialog();
            confirmDialog.setTitle("Xác nhận lần cuối");
            confirmDialog.setHeaderText("Hành động này không thể hoàn tác");
            confirmDialog.setContentText("Vui lòng nhập chính xác từ 'Delete' để xác nhận xoá:");

            Optional<String> confirmResult = confirmDialog.showAndWait();
            if (confirmResult.isPresent() && confirmResult.get().equals("Delete")) {
                try {
                    // Perform deletion of all assignments
                    repo.getAssignmentRepository().deleteAll();

                    // Clear local caches
                    pendingChanges.clear();
                    assignmentDbCache.clear();

                    // Rebuild grid
                    buildGrid();
                    updateCancelButtonVisibility();

                    // Show success message
                    Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                    successAlert.setTitle("Thành công");
                    successAlert.setHeaderText(null);
                    successAlert.setContentText("Đã xoá tất cả phân công thành công.");
                    successAlert.showAndWait();
                } catch (Exception e) {
                    e.printStackTrace();
                    showAlert(Alert.AlertType.ERROR, "Lỗi", "Không thể xoá dữ liệu: " + e.getMessage());
                }
            } else {
                Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                errorAlert.setTitle("Huỷ bỏ");
                errorAlert.setHeaderText(null);
                errorAlert.setContentText("Mã xác nhận không đúng. Đã huỷ thao tác xoá.");
                errorAlert.showAndWait();
            }
        }
    }

    @FXML
    public void onToggleTranspose() {
        isTransposed = !isTransposed; // Invert the state
        buildGrid(); // Redraw the Grid
    }
    // --- Helpers ---

    private String genKey(String subjectId, String classId) {
        return subjectId + "_" + classId;
    }

    private Teacher findTeacherById(String id) {
        if (id == null) return null;
        return teachers.stream()
                .filter(t -> t.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
