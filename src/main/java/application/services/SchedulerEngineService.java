package application.services;

import javafx.concurrent.Service;
import javafx.concurrent.Task;
import scheduler.common.models.Slot;
import scheduler.common.models.TaskData;
import scheduler.common.models.Variable;
import scheduler.common.proto.EngineInput;
import scheduler.common.proto.EngineOutput;
import scheduler.common.proto.TaskDataProto;
import scheduler.common.utils.ProtoMapper;

import java.io.*;
import java.util.List;
import java.util.Map;

public class SchedulerEngineService extends Service<Map<Variable, Slot>> {

    private List<TaskData> inputData;

    public void setInputData(List<TaskData> inputData) {
        this.inputData = inputData;
    }

    @Override
    protected Task<Map<Variable, Slot>> createTask() {
        return new Task<Map<Variable, Slot>>() {
            @Override
            protected Map<Variable, Slot> call() throws Exception {
                if(inputData == null || inputData.isEmpty()){
                    throw new IllegalArgumentException("[ERROR] Dữ liệu đầu vào trống!");
                }

                updateMessage("[INFO] Đang chuẩn bị dữ liệu...");

                File tmpIn = null;
                File tmpOut = null;

                try{
                    tmpIn = File.createTempFile("sched_in_", ".bin");
                    tmpOut = File.createTempFile("sched_out_", ".bin");

                    List<TaskDataProto> taskDataProtoList = inputData.stream().map(ProtoMapper::toProto).toList();

                    EngineInput engineInput = EngineInput.newBuilder().addAllTasks(taskDataProtoList).build();

                    try (FileOutputStream fos = new FileOutputStream(tmpIn)) {
                        engineInput.writeTo(fos);
                    }

                    updateMessage("[INFO] Đang khởi tạo thuật toán...");

                    // TODO: replace with real path
                    String enginePath = "D:\\.HCMUTE\\.PET.PROJECT\\scheduler-standalone\\engine\\target\\scheduler.engine-2.1.0.jar";

                    ProcessBuilder pb = new ProcessBuilder("java", "-jar", enginePath, tmpIn.getAbsolutePath(), tmpOut.getAbsolutePath());

                    pb.redirectErrorStream(true);

                    Process process = pb.start();

                    try(BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()))){
                        String line;
                        while((line = bufferedReader.readLine()) != null){
                            System.out.println("[ENGINE]: " + line);
                        }
                    }

                    int exitCode = process.waitFor();

                    if (exitCode != 0) {
                        throw new RuntimeException("[ERROR] Engine kết thúc với lỗi (Exit code: " + exitCode + ")");
                    }

                    updateMessage("[INFO] Đang xử lý kết quả...");

                    if (!tmpOut.exists() || tmpOut.length() == 0) {
                        throw new RuntimeException("[ERROR] Engine không sinh ra file output!");
                    }

                    EngineOutput engineOutput;
                    try (FileInputStream fis = new FileInputStream(tmpOut)) {
                        engineOutput = EngineOutput.parseFrom(fis);
                    }

                    if (!engineOutput.getSuccess()) {
                        throw new RuntimeException("[ERROR] Engine báo thất bại: " + engineOutput.getMessage());
                    }

                    // Convert Proto -> Map Java
                    return ProtoMapper.fromEngineOutput(engineOutput);

                } catch (IOException | InterruptedException | RuntimeException e) {
                    throw new RuntimeException(e);
                } finally {
                    if (tmpIn != null && tmpIn.exists()) tmpIn.delete();
                    if (tmpOut != null && tmpOut.exists()) tmpOut.delete();
                }
            }
        };
    }
}
