package com.langgraph4j.engine.rag;

import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * BM25 工具类
 * 将文本转换为 Milvus 稀疏向量格式，用于 BM25 全文检索
 *
 * Milvus 2.4+ 原生支持 SparseFloatVector，
 * 通过内置的 BM25 Function 可以自动生成稀疏向量，
 * 也可以手动构建稀疏向量用于检索
 */
@Slf4j
public class Bm25Util {

    /**
     * 简易分词器：按空格和标点分词，支持中文按字符切分
     */
    public static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return tokens;
        }

        // 中文字符单独切分，英文按空格分词
        StringBuilder currentToken = new StringBuilder();
        for (char c : text.toLowerCase().toCharArray()) {
            if (isChinese(c)) {
                // 先保存当前英文 token
                if (currentToken.length() > 0) {
                    tokens.add(currentToken.toString());
                    currentToken = new StringBuilder();
                }
                tokens.add(String.valueOf(c));
            } else if (Character.isLetterOrDigit(c)) {
                currentToken.append(c);
            } else {
                if (currentToken.length() > 0) {
                    tokens.add(currentToken.toString());
                    currentToken = new StringBuilder();
                }
            }
        }
        if (currentToken.length() > 0) {
            tokens.add(currentToken.toString());
        }

        return tokens;
    }

    /**
     * 将文本转换为 SortedMap<Long, Float> 格式
     * 用于 Milvus SparseFloatVector 的插入和搜索
     *
     * @param text 输入文本
     * @return SortedMap<维度索引, 权重>
     */
    public static SortedMap<Long, Float> tokenizeToSparseMap(String text) {
        List<String> tokens = tokenize(text);
        SortedMap<Long, Float> sparseMap = new TreeMap<>();

        // 计算 TF (Term Frequency)
        Map<String, Integer> termFreq = new HashMap<>();
        for (String token : tokens) {
            termFreq.merge(token, 1, Integer::sum);
        }

        // 使用 hash 作为稀疏向量的维度索引，TF 作为权重
        for (Map.Entry<String, Integer> entry : termFreq.entrySet()) {
            long hash = Math.abs((long) entry.getKey().hashCode()) % 100000L;
            float tf = (float) entry.getValue() / tokens.size();
            sparseMap.merge(hash, tf, Float::sum);
        }

        return sparseMap;
    }

    private static boolean isChinese(char c) {
        Character.UnicodeBlock ub = Character.UnicodeBlock.of(c);
        return ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS;
    }
}
