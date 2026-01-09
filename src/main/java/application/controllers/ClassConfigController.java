package application.controllers;

import application.models.*;
import application.repository.RepositoryOrchestrator;
import application.views.TimeGridSelector;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ClassConfigController {
    private final RepositoryOrchestrator repo;
    private Clazz currentSelectingClass;
    private Grade currentEditingGrade = null;
    // Controls
    @FXML
    private ToggleGroup sessionGroup;
    @FXML
    private ToggleButton btnMorning;
    @FXML
    private ToggleButton btnAfternoon;
    @FXML
    private TableView<Curriculum> curriculumTable;
    @FXML
    private TableColumn<Curriculum, String> colSubject;
    @FXML
    private TableColumn<Curriculum, Integer> colPeriods;
    @FXML
    private TableColumn<Curriculum, Boolean> colDouble;
    @FXML
    private TableColumn<Curriculum, Teacher> colTeacher;
    @FXML
    private Label lblTotalPeriods;
    @FXML
    private Button btnSave;
    @FXML
    private Label lblClassName;
    @FXML
    private TextField searchField;
    @FXML
    private Accordion gradeAccordion;
    @FXML
    private Button btnAddClass;
    @FXML
    private Button btnDeleteClass;
    @FXML
    private StackPane timeGridContainer;
    @FXML
    private Button btnAddGrade;
    @FXML
    private Button btnDeleteGrade;

    private TimeGridSelector timeGridSelector;

    public ClassConfigController(RepositoryOrchestrator repo) {
        this.repo = repo;
    }

    public void initialize() {
        sessionGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) {
                oldVal.setSelected(true); // Must select either Morning or Afternoon
                return;
            }
            updateToggleStyles();

            if (currentEditingGrade != null) {
                ESession session = newVal == btnMorning ? ESession.MORNING : ESession.AFTERNOON;
                Session dbSession = repo.getSessionRepository().getByName(session);
                currentEditingGrade.setSession(dbSession);
                // Re-create time grid when session changes, and reset matrix
                updateTimeGridForGrade(currentEditingGrade, true);
            }
        });

        // Set default styles
        updateToggleStyles();

        // Setup Accordion
        loadAccordionData();

        // Set up the table
        setUpTableColumns();

        // Initially hide buttons and clear grid
        setButtonVisibility(false, false, false);
        timeGridContainer.getChildren().clear();
    }

    private void setButtonVisibility(boolean addClass, boolean deleteClass, boolean deleteGrade) {
        btnAddClass.setVisible(addClass);
        btnAddClass.setManaged(addClass);

        btnDeleteClass.setVisible(deleteClass);
        btnDeleteClass.setManaged(deleteClass);

        btnDeleteGrade.setVisible(deleteGrade);
        btnDeleteGrade.setManaged(deleteGrade);
    }

    private void setUpTableColumns() {
        // Subject col
        colSubject.setCellValueFactory(data -> {
            String subId = data.getValue().getSubjectId();
            Subject s = repo.getSubjectRepository().getById(subId);
            return new SimpleStringProperty(s != null ? s.getName() : subId);
        });

        // Periods col (2 way binding)
        colPeriods.setCellValueFactory(data ->
                new SimpleObjectProperty<>(data.getValue().getPeriodsPerWeek())
        );
        colPeriods.setCellFactory(col -> new TableCell<Curriculum, Integer>() {
            private final Spinner<Integer> spinner = new Spinner<>(0, 10, 0);

            {
                spinner.setEditable(true);
                // Listener sync data to model
                spinner.valueProperty().addListener((obs, oldVal, newVal) -> {
                    // Check null to avoid NPE
                    if (newVal != null && getTableRow().getItem() != null) {
                        getTableRow().getItem().setPeriodsPerWeek(newVal);
                    }
                });
            }

            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    spinner.getValueFactory().setValue(item);
                    spinner.setDisable(!curriculumTable.isEditable());
                    spinner.setStyle(curriculumTable.isEditable() ? "-fx-opacity: 1;" : "-fx-opacity: 0.7;");
                    setGraphic(spinner);
                }
            }
        });

        // Double Period col
        colDouble.setCellValueFactory(data ->
                new SimpleBooleanProperty(data.getValue().isShouldBeDoubled())
        );
        colDouble.setCellFactory(col -> new TableCell<Curriculum, Boolean>() {
            private final CheckBox checkBox = new CheckBox();

            {
                checkBox.setAlignment(Pos.CENTER);

                // Bind disable into table's editable
                // Meaning: Table NOT editable -> CheckBox automatically disable
                checkBox.disableProperty().bind(curriculumTable.editableProperty().not());

                // Update model khi click
                checkBox.setOnAction(e -> {
                    Curriculum item = getTableRow().getItem();
                    if (item != null) {
                        item.setShouldBeDoubled(checkBox.isSelected());
                    }
                });
            }

            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    checkBox.setSelected(item); // Set display value
                    setAlignment(Pos.CENTER);   // Center align
                    setGraphic(checkBox);
                }
            }
        });

        // Teacher col
        colTeacher.setCellValueFactory(data -> {
            if (currentSelectingClass == null) return new SimpleObjectProperty<>(null);
            Curriculum c = data.getValue();

            Assignment a = repo.getAssignmentRepository().getByClassAndSubject(currentSelectingClass.getId(), c.getSubjectId());
            if (a == null) return new SimpleObjectProperty<>();

            Teacher t = repo.getTeacherRepository().getById(a.getTeacherId());
            if (t != null) return new SimpleObjectProperty<>(t);
            return new SimpleObjectProperty<>(null);
        });
        colTeacher.setCellFactory(col -> new TableCell<Curriculum, Teacher>() {
            {
            }

            @Override
            protected void updateItem(Teacher item, boolean empty) {
                super.updateItem(item, empty);

                if (empty) {
                    setText(null);
                    setStyle("");
                } else if (item == null) {
                    // Case null (not assigned or in grade config)
                    if (currentSelectingClass != null) {
                        // If viewing class -> Show not yet assigned
                        setText("Chưa phân công");
                        setStyle("-fx-text-fill: #95a5a6; -fx-font-style: italic; -fx-font-size: 11px;"); // Gray, italic
                    } else {
                        // If viewing Grade config -> empty
                        setText(null);
                        setStyle("");
                    }
                } else {
                    // Case assigned
                    setText(item.toString()); // Call Teacher.toString()
                    setStyle("-fx-text-fill: black; -fx-font-style: normal;"); // Reset style
                }
            }
        });
    }

    private void updateToggleStyles() {
        String activeStyle = "-fx-background-color: white; -fx-text-fill: #4f46e5; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 3, 0, 0, 1); -fx-font-weight: bold; -fx-background-radius: 6; -fx-cursor: hand;";
        String inactiveStyle = "-fx-background-color: transparent; -fx-text-fill: #64748b; -fx-font-weight: normal; -fx-background-radius: 6; -fx-cursor: hand;";

        if (btnMorning.isSelected()) {
            btnMorning.setStyle(activeStyle);
            btnAfternoon.setStyle(inactiveStyle);
        } else {
            btnMorning.setStyle(inactiveStyle);
            btnAfternoon.setStyle(activeStyle);
        }
    }

    private void updateTotalPeriods() {
        int total = curriculumTable.getItems().stream()
                .mapToInt(Curriculum::getPeriodsPerWeek)
                .sum();
        lblTotalPeriods.setText("Tổng số tiết: " + total);
    }

    private void loadAccordionData() {
        List<Grade> grades = repo.getGradeRepository().getAll();
        gradeAccordion.getPanes().clear();
        for (Grade grade : grades) {
            TitledPane pane = new TitledPane();
            pane.setText(grade.getName());

            ListView<NavItem> list = new ListView<>();
            List<NavItem> items = new ArrayList<>();

            items.add(new NavItem("Cấu hình chung " + grade.getName(), grade, true));

            List<Clazz> classes = repo.getClassRepository().getByGrade(grade.getId());
            for (Clazz c : classes) {
                items.add(new NavItem("   Lớp " + c.getClassName(), c, false));
            }

            list.getItems().addAll(items);

            list.setCellFactory(_ -> new ListCell<>() {
                @Override
                protected void updateItem(NavItem item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        setText(item.label());
                        if (item.isGradeConfig()) {
                            // Style for Grade
                            setStyle("-fx-font-weight: bold; -fx-text-fill: #2980b9;");
                        } else {
                            // Style clazz
                            setStyle("-fx-padding: 0 0 0 10;"); // Indent left
                        }
                    }
                }
            });

            double CELL_HEIGHT = 25.0;
            list.setFixedCellSize(CELL_HEIGHT);

            // ListView height = (element count * row's height) + 2px (border)
            list.prefHeightProperty().bind(
                    Bindings.size(list.getItems()).multiply(CELL_HEIGHT).add(2.0)
            );

            // Click event
            list.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    handleSelection(newVal); // Load data to the main panel
                }
            });
            pane.setContent(list);
            gradeAccordion.getPanes().add(pane);
        }
        // Auto open first selection
        if (!gradeAccordion.getPanes().isEmpty()) {
            gradeAccordion.setExpandedPane(gradeAccordion.getPanes().get(0));
        }
    }

    private void loadCurriculumTable(String gradeId) {
        List<Subject> subjects = repo.getSubjectRepository().getAll();
        curriculumTable.getItems().clear();

        for (Subject s : subjects) {
            Curriculum curriculum = repo.getCurriculumRepository().getByGradeAndSubject(gradeId, s.getId());
            if (curriculum == null) {
                // If not exists, create new
                curriculum = new Curriculum(gradeId, s.getId(), 0, false);
            }
            curriculumTable.getItems().add(curriculum);
        }

        // Refresh to update Spinner's state (Enable/Disable)
        curriculumTable.refresh();
        updateTotalPeriods();
    }

    private void handleSelection(NavItem item) {
        String gradeId;

        if (item.isGradeConfig()) {
            // CASE 1: EDITING GRADE
            Grade g = (Grade) item.data();
            gradeId = g.getId();
            currentEditingGrade = g; // Save this to re-use when click saveBtn
            currentSelectingClass = null;

            lblClassName.setText("Cấu hình chương trình: " + g.getName());
            lblClassName.setStyle("-fx-text-fill: #2980b9;"); // Blue for editing

            // Edit mode
            curriculumTable.setEditable(true);
            btnSave.setDisable(false); // Allow save
            btnSave.setText("LƯU CẤU HÌNH KHỐI");

            setButtonVisibility(true, false, true);

            // Enable session toggle for Grade config
            btnMorning.setDisable(false);
            btnAfternoon.setDisable(false);
            if (g.getSession().getSessionName() == ESession.MORNING) {
                sessionGroup.selectToggle(btnMorning);
            } else {
                sessionGroup.selectToggle(btnAfternoon);
            }

            // Update time grid for the selected grade
            updateTimeGridForGrade(g, false); // false to load existing matrix
            if (timeGridSelector != null) {
                timeGridSelector.setReadOnly(true);
            }

        } else {
            // CASE 2: VIEWING CLASS
            Clazz c = (Clazz) item.data();
            gradeId = c.getGradeId();
            currentEditingGrade = null; // DOES NOT ALLOW SAVING
            currentSelectingClass = c;

            lblClassName.setText("Xem chi tiết lớp: " + c.getClassName());
            lblClassName.setStyle("-fx-text-fill: #7f8c8d;"); // Gray for read-only

            // Disable editing
            curriculumTable.setEditable(false);
            btnSave.setDisable(true); // Hide
            btnSave.setText("CHẾ ĐỘ XEM");

            setButtonVisibility(false, true, false);

            // Disable session toggle for Class view (inherited from Grade)
            btnMorning.setDisable(true);
            btnAfternoon.setDisable(true);

            // Show inherited session
            Grade g = repo.getGradeRepository().getById(c.getGradeId());
            if (g != null) {
                if (g.getSession().getSessionName() == ESession.MORNING) {
                    sessionGroup.selectToggle(btnMorning);
                } else {
                    sessionGroup.selectToggle(btnAfternoon);
                }
                // Update time grid for the selected grade
                updateTimeGridForGrade(g, false); // false to load existing matrix
                if (timeGridSelector != null) {
                    timeGridSelector.setReadOnly(true);
                }
            } else {
                // Clear time grid when viewing a class
                timeGridContainer.getChildren().clear();
                timeGridSelector = null; // clear reference
            }
        }

        loadCurriculumTable(gradeId);
    }

    private void updateTimeGridForGrade(Grade grade, boolean resetMatrix) {
        if (grade == null) {
            timeGridContainer.getChildren().clear();
            return;
        }
        timeGridContainer.getChildren().clear();
        timeGridSelector = new TimeGridSelector(grade.getSession().getSessionName());
        Session session = repo.getSessionRepository().getByName(grade.getSession().getSessionName());
        if (!resetMatrix) {
            timeGridSelector.setBusyMatrix(session.getBusyMatrix());
        }
        timeGridContainer.getChildren().add(timeGridSelector);
    }

    @FXML
    public void handleSave() {
        if (currentEditingGrade == null) return;

        // Get item list from table
        List<Curriculum> list = curriculumTable.getItems();

        // Save each item into db
        for (Curriculum c : list) {
            repo.getCurriculumRepository().save(c);
        }

        // Also save the grade itself to persist session and busy matrix
        repo.getGradeRepository().save(currentEditingGrade);

        // Show success alert
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Thành công");
        alert.setHeaderText(null);
        alert.setContentText("Đã lưu cấu hình cho " + currentEditingGrade.getName());
        alert.showAndWait();
    }

    @FXML
    public void handleAddClass() {
        if (currentEditingGrade == null) return;

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Thêm lớp mới");
        dialog.setHeaderText("Thêm lớp mới vào " + currentEditingGrade.getName());
        dialog.setContentText("Nhập tên lớp:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(className -> {
            if (className.trim().isEmpty()) return;

            // Create new class
            Clazz newClass = new Clazz(java.util.UUID.randomUUID().toString(), className, currentEditingGrade.getId());
            repo.getClassRepository().save(newClass);

            // Reload accordion to show new class
            loadAccordionData();

            // Show success message
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Thành công");
            alert.setHeaderText(null);
            alert.setContentText("Đã thêm lớp " + className);
            alert.showAndWait();
        });
    }

    @FXML
    public void handleDeleteClass() {
        if (currentSelectingClass == null) return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Xác nhận xóa");
        alert.setHeaderText("Xóa lớp " + currentSelectingClass.getClassName());
        alert.setContentText("Bạn có chắc chắn muốn xóa lớp này không? Tất cả phân công giảng dạy của lớp này cũng sẽ bị xóa.");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            // Delete assignments first
            repo.getAssignmentRepository().deleteByClassId(currentSelectingClass.getId());

            // Delete class
            repo.getClassRepository().delete(currentSelectingClass.getId());

            // Reload accordion
            loadAccordionData();

            // Clear selection
            currentSelectingClass = null;
            lblClassName.setText("Chọn lớp để cấu hình");
            curriculumTable.getItems().clear();
            setButtonVisibility(false, false, false);

            // Show success message
            Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
            successAlert.setTitle("Thành công");
            successAlert.setHeaderText(null);
            successAlert.setContentText("Đã xóa lớp thành công");
            successAlert.showAndWait();
        }
    }

    @FXML
    public void handleAddGrade() {
        Dialog<GradeCreationResult> dialog = new Dialog<>();
        dialog.setTitle("Thêm Khối mới");
        dialog.setHeaderText("Cấu hình Khối lớp và Lớp học");

        ButtonType addButtonType = new ButtonType("Thêm", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 20, 10, 10));

        // Level selection
        ComboBox<Integer> levelComboBox = new ComboBox<>();
        levelComboBox.getItems().addAll(6, 7, 8, 9, 10, 11, 12);
        levelComboBox.setValue(10);

        // Class count
        Spinner<Integer> classCountSpinner = new Spinner<>(1, 20, 5);
        classCountSpinner.setEditable(true);

        // Naming convention type
        ComboBox<String> namingTypeComboBox = new ComboBox<>();
        namingTypeComboBox.getItems().addAll("Số (1, 2, 3...)", "Chữ cái (A, B, C...)");
        namingTypeComboBox.setValue("Số (1, 2, 3...)");

        // Delimiter
        TextField delimiterField = new TextField("A");
        delimiterField.setPromptText("Ví dụ: A, /, -");

        // Preview Label
        Label previewLabel = new Label("Xem trước: 10A1, 10A2, 10A3, 10A4, 10A5");
        previewLabel.setStyle("-fx-font-style: italic; -fx-text-fill: grey;");

        // Update preview logic
        Runnable updatePreview = () -> {
            Integer level = levelComboBox.getValue();
            int count = classCountSpinner.getValue();
            String type = namingTypeComboBox.getValue();
            String delimiter = delimiterField.getText();

            if (level == null) return;

            StringBuilder sb = new StringBuilder("Xem trước: ");
            for (int i = 1; i <= Math.min(count, 3); i++) {
                if (i > 1) sb.append(", ");
                if (type.startsWith("Số")) {
                    sb.append(level).append(delimiter).append(i);
                } else {
                    // Chữ cái: 6A, 6B... (delimiter ignored usually, but let's respect if user wants 6-A)
                    char suffix = (char) ('A' + i - 1);
                    // If delimiter is empty, default behavior for letters is just append
                    // If delimiter is not empty, append delimiter then letter
                    if (delimiter.isEmpty()) {
                        sb.append(level).append(suffix);
                    } else {
                        sb.append(level).append(delimiter).append(suffix);
                    }
                }
            }
            if (count > 3) sb.append(", ...");
            previewLabel.setText(sb.toString());
        };

        // Add listeners
        levelComboBox.valueProperty().addListener((obs, oldVal, newVal) -> updatePreview.run());
        classCountSpinner.valueProperty().addListener((obs, oldVal, newVal) -> updatePreview.run());
        namingTypeComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.startsWith("Chữ cái")) {
                delimiterField.setText(""); // Default empty for letters
            } else {
                delimiterField.setText("A"); // Default A for numbers (e.g. 10A1)
            }
            updatePreview.run();
        });
        delimiterField.textProperty().addListener((obs, oldVal, newVal) -> updatePreview.run());

        grid.add(new Label("Chọn Khối:"), 0, 0);
        grid.add(levelComboBox, 1, 0);

        grid.add(new Label("Số lượng lớp:"), 0, 1);
        grid.add(classCountSpinner, 1, 1);

        grid.add(new Label("Kiểu đặt tên:"), 0, 2);
        grid.add(namingTypeComboBox, 1, 2);

        grid.add(new Label("Ký tự ngăn cách:"), 0, 3);
        grid.add(delimiterField, 1, 3);

        grid.add(previewLabel, 1, 4);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                return new GradeCreationResult(
                        levelComboBox.getValue(),
                        classCountSpinner.getValue(),
                        namingTypeComboBox.getValue(),
                        delimiterField.getText()
                );
            }
            return null;
        });

        Optional<GradeCreationResult> result = dialog.showAndWait();
        result.ifPresent(data -> {
            // Check if grade already exists
            Grade existingGrade = repo.getGradeRepository().getByLevel(data.level);
            if (existingGrade != null) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Lỗi");
                alert.setHeaderText(null);
                alert.setContentText("Khối " + data.level + " đã tồn tại!");
                alert.showAndWait();
                return;
            }

            String gradeName = "Khối " + data.level;

            Session defaultSession = repo.getSessionRepository().getByName(ESession.MORNING);
            Grade newGrade = new Grade(java.util.UUID.randomUUID().toString(), gradeName, data.level, defaultSession);
            repo.getGradeRepository().save(newGrade);

            // Create classes
            for (int i = 1; i <= data.count; i++) {
                String className;
                if (data.namingType.startsWith("Số")) {
                    className = data.level + data.delimiter + i;
                } else {
                    char suffix = (char) ('A' + i - 1);
                    if (data.delimiter.isEmpty()) {
                        className = data.level + "" + suffix;
                    } else {
                        className = data.level + data.delimiter + suffix;
                    }
                }
                Clazz newClass = new Clazz(java.util.UUID.randomUUID().toString(), className, newGrade.getId());
                repo.getClassRepository().save(newClass);
            }

            // Reload accordion
            loadAccordionData();

            // Show success message
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Thành công");
            alert.setHeaderText(null);
            alert.setContentText("Đã thêm " + gradeName + " và " + data.count + " lớp học.");
            alert.showAndWait();
        });
    }

    @FXML
    public void handleDeleteGrade() {
        if (currentEditingGrade == null) return;

        // Check if grade has classes
        List<Clazz> classes = repo.getClassRepository().getByGrade(currentEditingGrade.getId());
        if (!classes.isEmpty()) {
            // Level 1 Warning
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("CẢNH BÁO NGUY HIỂM");
            alert.setHeaderText("KHỐI NÀY ĐANG CÓ LỚP HỌC!");
            alert.setContentText("BẠN CÓ CHẮC CHẮN MUỐN XOÁ KHỐI NÀY KHÔNG? TẤT CẢ CÁC LỚP VÀ PHÂN CÔNG SẼ BỊ XOÁ VĨNH VIỄN!");

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                // Level 2 Warning: Require typing "Delete"
                TextInputDialog confirmDialog = new TextInputDialog();
                confirmDialog.setTitle("Xác nhận lần cuối");
                confirmDialog.setHeaderText("Hành động này không thể hoàn tác");
                confirmDialog.setContentText("Vui lòng nhập chính xác từ 'Delete' để xác nhận xoá:");

                Optional<String> confirmResult = confirmDialog.showAndWait();
                if (confirmResult.isPresent() && confirmResult.get().equals("Delete")) {
                    // Perform deletion
                    // 1. Delete assignments for all classes in this grade
                    for (Clazz c : classes) {
                        repo.getAssignmentRepository().deleteByClassId(c.getId());
                        repo.getClassRepository().delete(c.getId());
                    }

                    // 2. Delete the grade itself
                    repo.getGradeRepository().delete(currentEditingGrade.getId());

                    // Reload accordion
                    loadAccordionData();

                    // Clear selection
                    currentEditingGrade = null;
                    lblClassName.setText("Chọn lớp để cấu hình");
                    curriculumTable.getItems().clear();
                    setButtonVisibility(false, false, false);
                    btnSave.setDisable(true);

                    // Show success message
                    Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                    successAlert.setTitle("Thành công");
                    successAlert.setHeaderText(null);
                    successAlert.setContentText("Đã xóa khối và tất cả các lớp liên quan thành công");
                    successAlert.showAndWait();
                } else {
                    Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                    errorAlert.setTitle("Huỷ bỏ");
                    errorAlert.setHeaderText(null);
                    errorAlert.setContentText("Mã xác nhận không đúng. Đã huỷ thao tác xoá.");
                    errorAlert.showAndWait();
                }
            }
        } else {
            // Normal delete if no classes
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Xác nhận xóa");
            alert.setHeaderText("Xóa " + currentEditingGrade.getName());
            alert.setContentText("Bạn có chắc chắn muốn xóa Khối này không?");

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                repo.getGradeRepository().delete(currentEditingGrade.getId());

                // Reload accordion
                loadAccordionData();

                // Clear selection
                currentEditingGrade = null;
                lblClassName.setText("Chọn lớp để cấu hình");
                curriculumTable.getItems().clear();
                setButtonVisibility(false, false, false);
                btnSave.setDisable(true);

                // Show success message
                Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                successAlert.setTitle("Thành công");
                successAlert.setHeaderText(null);
                successAlert.setContentText("Đã xóa khối thành công");
                successAlert.showAndWait();
            }
        }
    }

    /**
     * @param isGradeConfig True = Grade config, False = Viewing class detail
     * @param data          Grade or Clazz
     */
    public record NavItem(String label, Object data, boolean isGradeConfig) {

        @Override
        public String toString() {
            return label;
        }
    }

    private record GradeCreationResult(int level, int count, String namingType, String delimiter) {
    }
}
