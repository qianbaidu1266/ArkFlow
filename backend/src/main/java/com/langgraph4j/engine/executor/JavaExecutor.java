package com.langgraph4j.engine.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
public class JavaExecutor implements CodeExecutor {
    
    private final String javaHome;
    private final ObjectMapper objectMapper;
    
    public JavaExecutor() {
        this.javaHome = System.getProperty("java.home");
        this.objectMapper = new ObjectMapper();
        log.info("JavaExecutor initialized with java.home={}", javaHome);
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
            log.debug("Generated Java code:\n{}", fullCode);
            
            Files.writeString(javaFile, fullCode, StandardCharsets.UTF_8);
            
            String javac = javaHome + "/bin/javac";
            ProcessBuilder compilePb = new ProcessBuilder(
                javac, 
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
                "-cp", tempDir.toString(),
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
        sb.append("import java.time.*;\n");
        sb.append("import java.text.*;\n\n");
        sb.append("public class ").append(className).append(" {\n");
        sb.append("    \n");
        sb.append("    public static void main(String[] args) throws Exception {\n");
        sb.append("        Object result = main();\n");
        sb.append("        if (result != null) {\n");
        sb.append("            System.out.println(result);\n");
        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("    \n");
        
        // 生成 main() 函数签名
        sb.append("    public static Object main(");
        if (variables != null && !variables.isEmpty()) {
            boolean first = true;
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                if (!first) sb.append(", ");
                String varName = sanitizeVarName(entry.getKey());
                sb.append("Object ").append(varName);
                first = false;
            }
        }
        sb.append(") throws Exception {\n");
        
        String indentedCode = userCode.lines()
            .map(line -> "        " + line)
            .collect(java.util.stream.Collectors.joining("\n"));
        sb.append(indentedCode).append("\n");
        sb.append("    }\n");
        sb.append("    \n");
        sb.append("    // ===== Helper methods =====\n");
        sb.append("    \n");
        sb.append("    private static void print(Object obj) {\n");
        sb.append("        System.out.println(obj);\n");
        sb.append("    }\n");
        sb.append("    \n");
        sb.append("    private static void sleep(long millis) {\n");
        sb.append("        try { Thread.sleep(millis); }\n");
        sb.append("        catch (InterruptedException e) { Thread.currentThread().interrupt(); }\n");
        sb.append("    }\n");
        sb.append("    \n");
        sb.append("    private static String format(String pattern, Object... args) {\n");
        sb.append("        return String.format(pattern, args);\n");
        sb.append("    }\n");
        sb.append("    \n");
        sb.append("    private static long currentTimeMillis() {\n");
        sb.append("        return System.currentTimeMillis();\n");
        sb.append("    }\n");
        sb.append("    \n");
        sb.append("    private static int parseInt(Object obj) {\n");
        sb.append("        return obj == null ? 0 : Integer.parseInt(obj.toString());\n");
        sb.append("    }\n");
        sb.append("    \n");
        sb.append("    private static long parseLong(Object obj) {\n");
        sb.append("        return obj == null ? 0L : Long.parseLong(obj.toString());\n");
        sb.append("    }\n");
        sb.append("    \n");
        sb.append("    private static double parseDouble(Object obj) {\n");
        sb.append("        return obj == null ? 0.0 : Double.parseDouble(obj.toString());\n");
        sb.append("    }\n");
        sb.append("    \n");
        sb.append("    private static String str(Object obj) {\n");
        sb.append("        return obj == null ? \"null\" : obj.toString();\n");
        sb.append("    }\n");
        sb.append("}\n");
        
        return sb.toString();
    }
    
    private String sanitizeVarName(String name) {
        String sanitized = name.replaceAll("[^a-zA-Z0-9_]", "_");
        if (sanitized.isEmpty() || Character.isDigit(sanitized.charAt(0))) {
            sanitized = "var_" + sanitized;
        }
        return sanitized;
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
            // 尝试解析为 JSON 对象或数组
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
