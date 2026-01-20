package application.services;

import javafx.concurrent.Service;
import javafx.concurrent.Task;
import scheduler.common.constants.PathConstants;
import scheduler.common.models.SwapEngineInput;
import scheduler.common.models.SwapEngineOutput;
import scheduler.common.proto.SwapEngineInputProto;
import scheduler.common.proto.SwapEngineOutputProto;
import scheduler.common.utils.SwapperMapper;

import java.io.*;
import java.util.prefs.Preferences;

public class SwapEngineService extends Service<SwapEngineOutput> {

    private static final String PREF_ENGINE_PATH = "engine_path";
    private static final String DEFAULT_ENGINE_PATH = "";
    private SwapEngineInput inputData;

    public static String getEnginePath() {
        Preferences prefs = Preferences.userNodeForPackage(SchedulerEngineService.class);
        return prefs.get(PREF_ENGINE_PATH, DEFAULT_ENGINE_PATH);
    }

    public void setInputData(SwapEngineInput inputData) {
        this.inputData = inputData;
    }

    @Override
    protected Task<SwapEngineOutput> createTask() {
        return new Task<SwapEngineOutput>() {
            @Override
            protected SwapEngineOutput call() throws Exception {
                if (inputData == null) {
                    throw new IllegalArgumentException("[ERROR] Dữ liệu đầu vào trống!");
                }

                updateMessage("[INFO] Đang chuẩn bị dữ liệu hoán đổi...");

                File tmpIn = null;
                File tmpOut = null;

                try {
                    tmpIn = File.createTempFile("swap_in_", ".bin");
                    tmpOut = File.createTempFile("swap_out_", ".bin");

                    // Map SwapEngineInput to SwapEngineInputProto using SwapperMapper
                    SwapEngineInputProto swapInputProto = SwapperMapper.toProtoInput(inputData);

                    try (FileOutputStream fos = new FileOutputStream(tmpIn)) {
                        swapInputProto.writeTo(fos);
                    }

                    updateMessage("[INFO] Đang khởi tạo thuật toán hoán đổi...");

                    String enginePath = getEnginePath();
                    File engineFile = new File(enginePath);
                    if (!engineFile.exists()) {
                        throw new FileNotFoundException("Engine JAR not found at: " + enginePath);
                    }

                    // Call Engine with SWAP_MODE
                    ProcessBuilder pb = new ProcessBuilder(
                            enginePath,
                            PathConstants.SWAP_MODE, 
                            tmpIn.getAbsolutePath(), 
                            tmpOut.getAbsolutePath()
                    );

                    pb.redirectErrorStream(true);

                    Process process = pb.start();

                    try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = bufferedReader.readLine()) != null) {
                            System.out.println("[SWAP-ENGINE]: " + line);
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

                    SwapEngineOutputProto swapOutputProto;
                    try (FileInputStream fis = new FileInputStream(tmpOut)) {
                        swapOutputProto = SwapEngineOutputProto.parseFrom(fis);
                    }
                    
                    // Convert Proto -> Java Object using SwapperMapper
                    return SwapperMapper.toJavaOutput(swapOutputProto);

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
