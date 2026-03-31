package com.langgraph4j.engine.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class JavaExecutor implements CodeExecutor {
    
    private final ObjectMapper objectMapper;
    private final String javaHome;
    private final String classpath;
    
    public JavaExecutor() {
        this.objectMapper = new ObjectMapper();
        this.javaHome = System.getProperty("java.home");
        this.classpath = System.getProperty("java.class.path", ".");
        log.info("JavaExecutor initialized with java.home={}, classpath length={}", 
            javaHome, classpath.length());
    }
    
    @Override
    public String getLanguage() {
        return "java";
    }
    
    @Override
    public Object execute(String code, Map<String, Object> variables, long timeoutMs) throws Exception {
        Path tempDir = Files.createTempDirectory("java_exec_");
        
        try {
            String className = "UserCode_" + System.currentTimeMillis();
            Path javaFile = tempDir.resolve(className + ".java");
            
            String fullCode = buildFullClass(className, code, variables);
            Files.writeString(javaFile, fullCode, StandardCharsets.UTF_8);
            
            String javac = javaHome + "/bin/javac";
            ProcessBuilder compilePb = new ProcessBuilder(
                javac, 
                "-cp", classpath,
                "-d", tempDir.toString(),
                javaFile.toString()
            );
            compilePb.redirectErrorStream(true);
            
            Process compileProcess = compilePb.start();
            String compileOutput = readOutput(compileProcess);
            
            if (!compileProcess.waitFor(30, TimeUnit.SECONDS)) {
                compileProcess.destroyForcibly();
                throw new RuntimeException("Java compilation timed out");
            }
            
            if (compileProcess.exitValue() != 0) {
                throw new RuntimeException("Java compilation failed: " + compileOutput);
            }
            
            String java = javaHome + "/bin/java";
            ProcessBuilder runPb = new ProcessBuilder(
                java,
                "-cp", tempDir.toString() + File.pathSeparator + classpath,
                className
            );
            runPb.redirectErrorStream(true);
            
            Process runProcess = runPb.start();
            String runOutput = readOutput(runProcess);
            
            if (!runProcess.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                runProcess.destroyForcibly();
                throw new RuntimeException("Java execution timed out after " + timeoutMs + "ms");
            }
            
            if (runProcess.exitValue() != 0) {
                throw new RuntimeException("Java execution failed: " + runOutput);
            }
            
            return parseOutput(runOutput);
            
        } finally {
            cleanup(tempDir);
        }
    }
    
    private String buildFullClass(String className, String userCode, Map<String, Object> variables) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("import java.util.*;\n");
        sb.append("import java.math.*;\n");
        sb.append("import com.fasterxml.jackson.databind.*;\n\n");
        sb.append("public class ").append(className).append(" {\n");
        sb.append("    private static final ObjectMapper mapper = new ObjectMapper();\n\n");
        sb.append("    public static void main(String[] args) throws Exception {\n");
        
        if (variables != null && !variables.isEmpty()) {
            sb.append("        // Input variables\n");
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                String varName = sanitizeVarName(entry.getKey());
                String jsonValue;
                try {
                    jsonValue = objectMapper.writeValueAsString(entry.getValue());
                } catch (Exception e) {
                    jsonValue = "null";
                }
                sb.append("        Object ").append(varName).append(" = mapper.readValue(")
                  .append("\"").append(jsonValue.replace("\"", "\\\"")).append("\", Object.class);\n");
            }
        }
        
        sb.append("        // User code\n");
        String indentedCode = userCode.lines()
            .map(line -> "        " + line)
            .collect(java.util.stream.Collectors.joining("\n"));
        sb.append(indentedCode).append("\n");
        
        sb.append("    }\n");
        sb.append("    \n");
        sb.append("    // Helper methods\n");
        sb.append("    private static void print(Object obj) {\n");
        sb.append("        System.out.println(obj);\n");
        sb.append("    }\n");
        sb.append("    \n");
        sb.append("    private static String toJson(Object obj) {\n");
        sb.append("        try { return mapper.writeValueAsString(obj); }\n");
        sb.append("        catch (Exception e) { return \"null\"; }\n");
        sb.append("    }\n");
        sb.append("    \n");
        sb.append("    private static Object fromJson(String json) {\n");
        sb.append("        try { return mapper.readValue(json, Object.class); }\n");
        sb.append("        catch (Exception e) { return null; }\n");
        sb.append("    }\n");
        sb.append("}\n");
        
        return sb.toString();
    }
    
    private String sanitizeVarName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_]", "_");
    }
    
    private String readOutput(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                log.info("[Java] {}", line);
            }
        }
        return output.toString();
    }
    
    private Object parseOutput(String output) {
        if (output == null || output.isEmpty()) {
            return null;
        }
        
        String trimmed = output.trim();
        
        try {
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                return objectMapper.readValue(trimmed, Object.class);
            }
            
            if (trimmed.equalsIgnoreCase("true")) return true;
            if (trimmed.equalsIgnoreCase("false")) return false;
            if (trimmed.equalsIgnoreCase("null")) return null;
            
            try {
                return Long.parseLong(trimmed);
            } catch (NumberFormatException e1) {
                try {
                    return Double.parseDouble(trimmed);
                } catch (NumberFormatException e2) {
                    return trimmed;
                }
            }
        } catch (Exception e) {
            return trimmed;
        }
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
