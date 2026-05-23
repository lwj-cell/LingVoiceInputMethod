package com.lingvoice.day1;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LingVoiceDay1 {
    private final JFrame frame = new JFrame("凌声输入法 - 基础输入版");
    private final JTextArea editor = new JTextArea(16, 56);
    private final JTextField speechField = new JTextField();
    private final JLabel status = new JLabel("输入 0 次 | 字符 0 | 平均延迟 0 ms");
    private final VoiceTextProcessor processor = new VoiceTextProcessor();
    private final Deque<String> undoStack = new ArrayDeque<>();
    private int inputCount;
    private long totalChars;
    private long totalLatencyMs;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LingVoiceDay1().show());
    }

    private void show() {
        editor.setLineWrap(true);
        editor.setWrapStyleWord(true);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(8, 8));

        JPanel inputPanel = new JPanel(new BorderLayout(8, 8));
        inputPanel.add(new JLabel("语音识别文本"), BorderLayout.WEST);
        inputPanel.add(speechField, BorderLayout.CENTER);

        JButton appendButton = new JButton("输入");
        JButton undoButton = new JButton("撤销");
        JButton clearButton = new JButton("清空");
        JButton copyButton = new JButton("复制全文");
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actionPanel.add(appendButton);
        actionPanel.add(undoButton);
        actionPanel.add(clearButton);
        actionPanel.add(copyButton);

        JPanel top = new JPanel(new BorderLayout(8, 8));
        top.add(inputPanel, BorderLayout.CENTER);
        top.add(actionPanel, BorderLayout.EAST);

        appendButton.addActionListener(event -> acceptInput());
        speechField.addActionListener(event -> acceptInput());
        undoButton.addActionListener(event -> undo());
        clearButton.addActionListener(event -> clear());
        copyButton.addActionListener(event -> copyAll());

        frame.add(top, BorderLayout.NORTH);
        frame.add(new JScrollPane(editor), BorderLayout.CENTER);
        frame.add(status, BorderLayout.SOUTH);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void acceptInput() {
        String raw = speechField.getText().trim();
        if (raw.isEmpty()) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }

        long started = System.nanoTime();
        if (processor.isCopyCommand(raw)) {
            copyAll();
            speechField.setText("");
            updateStatus(0, elapsedMillis(started));
            return;
        }

        undoStack.push(editor.getText());
        if (processor.isDeleteCommand(raw)) {
            removeLastCodePoint();
            updateStatus(0, elapsedMillis(started));
            speechField.setText("");
            return;
        }
        if (processor.isClearCommand(raw)) {
            editor.setText("");
            updateStatus(0, elapsedMillis(started));
            speechField.setText("");
            return;
        }

        String text = processor.toInputText(raw);
        editor.append(text);
        updateStatus(countVisibleChars(text), elapsedMillis(started));
        speechField.setText("");
    }

    private void undo() {
        if (undoStack.isEmpty()) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        editor.setText(undoStack.pop());
    }

    private void clear() {
        undoStack.push(editor.getText());
        editor.setText("");
    }

    private void copyAll() {
        StringSelection selection = new StringSelection(editor.getText());
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
    }

    private void removeLastCodePoint() {
        String text = editor.getText();
        if (text.isEmpty()) {
            return;
        }
        int lastStart = text.offsetByCodePoints(text.length(), -1);
        editor.setText(text.substring(0, lastStart));
    }

    private void updateStatus(long chars, long latencyMs) {
        inputCount++;
        totalChars += chars;
        totalLatencyMs += latencyMs;
        long average = inputCount == 0 ? 0 : totalLatencyMs / inputCount;
        status.setText("输入 " + inputCount + " 次 | 字符 " + totalChars + " | 平均延迟 " + average + " ms");
    }

    private long elapsedMillis(long started) {
        return Math.max(1, (System.nanoTime() - started) / 1_000_000);
    }

    private long countVisibleChars(String text) {
        return text.codePoints().filter(codePoint -> !Character.isWhitespace(codePoint)).count();
    }

    private static final class VoiceTextProcessor {
        private final Map<String, String> punctuation = new LinkedHashMap<>();

        private VoiceTextProcessor() {
            punctuation.put("顿号", "、");
            punctuation.put("逗号", "，");
            punctuation.put("句号", "。");
            punctuation.put("问号", "？");
            punctuation.put("感叹号", "！");
            punctuation.put("冒号", "：");
            punctuation.put("分号", "；");
            punctuation.put("左括号", "（");
            punctuation.put("右括号", "）");
        }

        private boolean isDeleteCommand(String raw) {
            return raw.equals("删除") || raw.equals("退格") || raw.equals("删掉一个字");
        }

        private boolean isClearCommand(String raw) {
            return raw.equals("清空") || raw.equals("清空全文");
        }

        private boolean isCopyCommand(String raw) {
            return raw.equals("复制") || raw.equals("复制全文");
        }

        private String toInputText(String raw) {
            String text = raw.trim();
            text = text.replace("换行", "{NEW_LINE}");
            text = text.replace("另起一行", "{NEW_LINE}");
            text = text.replace("空格", "{SPACE}");
            for (Map.Entry<String, String> entry : punctuation.entrySet()) {
                text = text.replace(entry.getKey(), entry.getValue());
            }
            text = text.replace("嗯", "");
            text = text.replace("啊", "");
            text = text.replace("那个", "");
            text = text.replaceAll("\\s+", "");
            text = text.replace("{SPACE}", " ");
            text = text.replace("{NEW_LINE}", System.lineSeparator());
            return text;
        }
    }
}
