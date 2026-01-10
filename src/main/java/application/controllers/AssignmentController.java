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
import javafx.util.StringConverter;

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
        subjects = repo.getSubjectRepository().getAll();
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
    }

    /**
     * Configures the Quick Mode Toolbar (ComboBox for Teachers).
     */
    private void setupQuickModeControls() {
        cbQuickTeacher.setItems(FXCollections.observableArrayList(teachers));

        // Display Teacher Name nicely
        cbQuickTeacher.setConverter(new StringConverter<>() {
            @Override
            public String toString(Teacher t) {
                return (t == null) ? "" : t.getName();
            }

            @Override
            public Teacher fromString(String string) {
                return null; // Not needed
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
//            cell.setPrefHeight(40);
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
        ChoiceDialog<Teacher> dialog = new ChoiceDialog<>(current, teachers);
        dialog.setTitle("Assign Teacher");
        dialog.setHeaderText("Subject: " + s.getName() + " - Class: " + c.getClassName());
        dialog.setContentText("Select Teacher:");

        // Fix display in ComboBox inside Dialog
        ComboBox<Teacher> combo = (ComboBox<Teacher>) dialog.getDialogPane().lookup(".combo-box");
        if (combo != null) {
            combo.setConverter(new StringConverter<>() {
                @Override
                public String toString(Teacher t) {
                    return t == null ? "" : t.getName();
                }

                @Override
                public Teacher fromString(String s) {
                    return null;
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
            // 1. Convert pending map to list
            List<Assignment> batchList = new ArrayList<>(pendingChanges.values());

            // 2. Call Repository Batch Save (INSERT OR REPLACE)
            repo.getAssignmentRepository().saveAll(batchList);

            // 3. Clear pending
            pendingChanges.clear();

            // 4. Reload from DB to verify and reset UI
            loadDataFromDb();
            buildGrid();
            updateCancelButtonVisibility();

            showAlert(Alert.AlertType.INFORMATION, "Success", "Saved " + batchList.size() + " assignments successfully!");

        } catch (Exception e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Error", "Save failed: " + e.getMessage());
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