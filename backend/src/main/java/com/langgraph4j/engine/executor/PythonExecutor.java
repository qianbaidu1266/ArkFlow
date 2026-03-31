package com.langgraph4j.engine.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
public class PythonExecutor implements CodeExecutor {
    
    private final ObjectMapper objectMapper;
    private final String pythonCommand;
    
    public PythonExecutor() {
        this.objectMapper = new ObjectMapper();
        this.pythonCommand = detectPythonCommand();
    }
    
    private String detectPythonCommand() {
        String[] commands = {"python3", "python"};
        for (String cmd : commands) {
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd, "--version");
                pb.redirectErrorStream(true);
                Process process = pb.start();
                boolean finished = process.waitFor(5, TimeUnit.SECONDS);
                if (finished && process.exitValue() == 0) {
                    log.info("Detected Python command: {}", cmd);
                    return cmd;
                }
            } catch (Exception e) {
                log.debug("Python command {} not available: {}", cmd, e.getMessage());
            }
        }
        log.warn("No Python installation found, defaulting to python3");
        return "python3";
    }
    
    @Override
    public String getLanguage() {
        return "python";
    }
    
    @Override
    public Object execute(String code, Map<String, Object> variables, long timeoutMs) throws Exception {
        Path tempDir = Files.createTempDirectory("python_exec_");
        Path scriptFile = tempDir.resolve("script.py");
        Path inputFile = tempDir.resolve("input.json");
        Path outputFile = tempDir.resolve("output.json");
        
        try {
            String wrapperCode = buildWrapperCode(code, inputFile.toString(), outputFile.toString());
            Files.writeString(scriptFile, wrapperCode, StandardCharsets.UTF_8);
            
            if (variables != null && !variables.isEmpty()) {
                String inputJson = objectMapper.writeValueAsString(variables);
                Files.writeString(inputFile, inputJson, StandardCharsets.UTF_8);
            } else {
                Files.writeString(inputFile, "{}", StandardCharsets.UTF_8);
            }
            
            ProcessBuilder pb = new ProcessBuilder(pythonCommand, scriptFile.toString());
            pb.directory(tempDir.toFile());
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    log.info("[Python] {}", line);
                }
            }
            
            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("Python execution timed out after " + timeoutMs + "ms");
            }
            
            if (process.exitValue() != 0) {
                throw new RuntimeException("Python execution failed with exit code " + process.exitValue() + 
                    ": " + output);
            }
            
            if (Files.exists(outputFile)) {
                String resultJson = Files.readString(outputFile, StandardCharsets.UTF_8);
                if (!resultJson.isEmpty() && !resultJson.equals("null")) {
                    return objectMapper.readValue(resultJson, Object.class);
                }
            }
            
            return output.toString().trim();
            
        } finally {
            cleanup(tempDir);
        }
    }
    
    private String buildWrapperCode(String userCode, String inputPath, String outputPath) {
        return """
import json
import sys

# Load input variables
try:
    with open('%s', 'r') as f:
        _variables = json.load(f)
        for _k, _v in _variables.items():
            globals()[_k] = _v
except Exception as e:
    _variables = {}

# Helper functions
def _to_json(obj):
    return json.dumps(obj, ensure_ascii=False, default=str)

def _from_json(s):
    return json.loads(s)

# Print function that captures output
_original_print = print
_output_buffer = []

def print(*args, **kwargs):
    _original_print(*args, **kwargs)

# User code
_result = None
try:
%s
except Exception as e:
    _result = {'error': str(e)}

# Save result
try:
    with open('%s', 'w') as f:
        if _result is not None:
            json.dump(_result, f, ensure_ascii=False, default=str)
        else:
            f.write('null')
except Exception as e:
    with open('%s', 'w') as f:
        json.dump({'error': str(e)}, f)
""".formatted(
            inputPath.replace("\\", "\\\\"),
            indentCode(userCode, 4),
            outputPath.replace("\\", "\\\\"),
            outputPath.replace("\\", "\\\\")
        );
    }
    
    private String indentCode(String code, int spaces) {
        String indent = " ".repeat(spaces);
        return code.lines()
            .map(line -> indent + line)
            .collect(java.util.stream.Collectors.joining("\n"));
    }
    
    private void cleanup(Path tempDir) {
        try {
            Files.walk(tempDir)
                .sorted((a, b) -> -a.compareTo(b))
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        log.debug("Failed to delete: {}", path);
                    }
                });
        } catch (IOException e) {
            log.debug("Failed to cleanup temp directory: {}", tempDir);
        }
    }
}
