package com.lingvoice.day2;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LingVoiceDay2 {
    private final JFrame frame = new JFrame("凌声输入法 - 词库增强版");
    private final JTextArea editor = new JTextArea(18, 54);
    private final JTextField speechField = new JTextField();
    private final JLabel status = new JLabel();
    private final DefaultListModel<String> dictionaryModel = new DefaultListModel<>();
    private final DefaultListModel<String> candidateModel = new DefaultListModel<>();
    private final DefaultListModel<String> historyModel = new DefaultListModel<>();
    private final JList<String> candidateList = new JList<>(candidateModel);
    private final UserDictionary dictionary = new UserDictionary();
    private final TextProcessor processor = new TextProcessor();
    private final HistoryBuffer history = new HistoryBuffer(30);
    private final MetricTracker metrics = new MetricTracker();
    private final Deque<String> undoStack = new ArrayDeque<>();

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LingVoiceDay2().show());
    }

    private void show() {
        editor.setLineWrap(true);
        editor.setWrapStyleWord(true);

        JButton inputButton = new JButton("输入");
        JButton undoButton = new JButton("撤销");
        JButton copyButton = new JButton("复制");
        JButton exportButton = new JButton("导出");
        JButton applyCandidateButton = new JButton("应用候选");

        JPanel top = new JPanel(new BorderLayout(8, 8));
        top.add(new JLabel("语音识别文本"), BorderLayout.WEST);
        top.add(speechField, BorderLayout.CENTER);
        JPanel topActions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        topActions.add(inputButton);
        topActions.add(undoButton);
        topActions.add(copyButton);
        topActions.add(exportButton);
        top.add(topActions, BorderLayout.EAST);

        JTextField spokenField = new JTextField();
        JTextField targetField = new JTextField();
        JButton addWordButton = new JButton("添加词条");
        JPanel wordForm = new JPanel(new GridLayout(3, 2, 6, 6));
        wordForm.add(new JLabel("识别词"));
        wordForm.add(spokenField);
        wordForm.add(new JLabel("输出词"));
        wordForm.add(targetField);
        wordForm.add(new JLabel(""));
        wordForm.add(addWordButton);

        JPanel dictionaryPanel = titledPanel("用户词库", new BorderLayout(6, 6));
        dictionaryPanel.add(wordForm, BorderLayout.NORTH);
        dictionaryPanel.add(new JScrollPane(new JList<>(dictionaryModel)), BorderLayout.CENTER);

        JPanel candidatePanel = titledPanel("候选建议", new BorderLayout(6, 6));
        candidatePanel.add(new JScrollPane(candidateList), BorderLayout.CENTER);
        candidatePanel.add(applyCandidateButton, BorderLayout.SOUTH);

        JPanel historyPanel = titledPanel("历史记录", new BorderLayout(6, 6));
        historyPanel.add(new JScrollPane(new JList<>(historyModel)), BorderLayout.CENTER);

        JPanel right = new JPanel(new GridLayout(3, 1, 8, 8));
        right.add(dictionaryPanel);
        right.add(candidatePanel);
        right.add(historyPanel);

        inputButton.addActionListener(event -> acceptInput());
        speechField.addActionListener(event -> acceptInput());
        speechField.getDocument().addDocumentListener(new SimpleDocumentListener(this::refreshCandidates));
        undoButton.addActionListener(event -> undo());
        copyButton.addActionListener(event -> copyAll());
        exportButton.addActionListener(event -> exportText());
        addWordButton.addActionListener(event -> addDictionaryEntry(spokenField, targetField));
        applyCandidateButton.addActionListener(event -> applySelectedCandidate());

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(8, 8));
        frame.add(top, BorderLayout.NORTH);
        frame.add(new JScrollPane(editor), BorderLayout.CENTER);
        frame.add(right, BorderLayout.EAST);
        frame.add(status, BorderLayout.SOUTH);
        refreshDictionary();
        refreshHistory();
        refreshStatus();
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private JPanel titledPanel(String title, BorderLayout layout) {
        JPanel panel = new JPanel(layout);
        panel.setBorder(BorderFactory.createTitledBorder(title));
        return panel;
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
            metrics.record(0, elapsedMillis(started));
            refreshStatus();
            speechField.setText("");
            return;
        }

        undoStack.push(editor.getText());
        if (processor.isDeleteCommand(raw)) {
            removeLastCodePoint();
            metrics.record(0, elapsedMillis(started));
            refreshStatus();
            speechField.setText("");
            return;
        }
        if (processor.isClearCommand(raw)) {
            editor.setText("");
            metrics.record(0, elapsedMillis(started));
            refreshStatus();
            speechField.setText("");
            return;
        }

        String text = processor.process(raw, dictionary);
        editor.append(text);
        history.add(text);
        metrics.record(countVisibleChars(text), elapsedMillis(started));
        refreshHistory();
        refreshStatus();
        speechField.setText("");
    }

    private void addDictionaryEntry(JTextField spokenField, JTextField targetField) {
        String spoken = spokenField.getText().trim();
        String target = targetField.getText().trim();
        if (spoken.isEmpty() || target.isEmpty()) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        dictionary.put(spoken, target);
        spokenField.setText("");
        targetField.setText("");
        refreshDictionary();
        refreshCandidates();
    }

    private void applySelectedCandidate() {
        String selected = candidateList.getSelectedValue();
        if (selected == null) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        undoStack.push(editor.getText());
        String selectedText = editor.getSelectedText();
        if (selectedText == null || selectedText.isEmpty()) {
            editor.append(selected);
        } else {
            editor.replaceSelection(selected);
        }
        history.add(selected);
        refreshHistory();
    }

    private void undo() {
        if (undoStack.isEmpty()) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        editor.setText(undoStack.pop());
    }

    private void copyAll() {
        StringSelection selection = new StringSelection(editor.getText());
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
    }

    private void exportText() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("lingvoice-output.txt"));
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            Files.writeString(chooser.getSelectedFile().toPath(), editor.getText(), StandardCharsets.UTF_8);
            JOptionPane.showMessageDialog(frame, "导出完成");
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(frame, "导出失败：" + exception.getMessage());
        }
    }

    private void removeLastCodePoint() {
        String text = editor.getText();
        if (text.isEmpty()) {
            return;
        }
        int lastStart = text.offsetByCodePoints(text.length(), -1);
        editor.setText(text.substring(0, lastStart));
    }

    private void refreshDictionary() {
        dictionaryModel.clear();
        for (Map.Entry<String, String> entry : dictionary.entries()) {
            dictionaryModel.addElement(entry.getKey() + " -> " + entry.getValue());
        }
    }

    private void refreshCandidates() {
        candidateModel.clear();
        for (String candidate : processor.suggest(speechField.getText().trim(), dictionary)) {
            candidateModel.addElement(candidate);
        }
    }

    private void refreshHistory() {
        historyModel.clear();
        for (String item : history.items()) {
            historyModel.addElement(item);
        }
    }

    private void refreshStatus() {
        status.setText(metrics.summary() + " | 历史 " + history.size() + " 条 | 词库 " + dictionary.size() + " 条");
    }

    private long elapsedMillis(long started) {
        return Math.max(1, (System.nanoTime() - started) / 1_000_000);
    }

    private long countVisibleChars(String text) {
        return text.codePoints().filter(codePoint -> !Character.isWhitespace(codePoint)).count();
    }

    private static final class TextProcessor {
        private final Map<String, String> punctuation = new LinkedHashMap<>();

        private TextProcessor() {
            punctuation.put("顿号", "、");
            punctuation.put("逗号", "，");
            punctuation.put("句号", "。");
            punctuation.put("问号", "？");
            punctuation.put("感叹号", "！");
            punctuation.put("冒号", "：");
            punctuation.put("分号", "；");
        }

        private String process(String raw, UserDictionary dictionary) {
            String text = normalize(raw);
            for (Map.Entry<String, String> entry : dictionary.entries()) {
                text = text.replace(entry.getKey(), entry.getValue());
            }
            return text;
        }

        private List<String> suggest(String raw, UserDictionary dictionary) {
            List<String> result = new ArrayList<>();
            if (raw == null || raw.isBlank()) {
                return result;
            }
            String normalized = normalize(raw);
            for (Map.Entry<String, String> entry : dictionary.entries()) {
                if (normalized.contains(entry.getKey()) || entry.getKey().contains(normalized)) {
                    result.add(normalized.replace(entry.getKey(), entry.getValue()));
                }
            }
            if (!result.contains(normalized)) {
                result.add(normalized);
            }
            return result;
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

        private String normalize(String raw) {
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
            text = text.replace("这个", "");
            text = text.replaceAll("\\s+", "");
            text = text.replace("{SPACE}", " ");
            text = text.replace("{NEW_LINE}", System.lineSeparator());
            return text;
        }
    }

    private static final class UserDictionary {
        private final Map<String, String> words = new LinkedHashMap<>();

        private UserDictionary() {
            words.put("林沃斯", "LingVoice");
            words.put("杰森", "JSON");
            words.put("接口", "API");
            words.put("语音输入法", "语音输入法");
        }

        private void put(String spoken, String target) {
            words.put(spoken, target);
        }

        private Iterable<Map.Entry<String, String>> entries() {
            return words.entrySet();
        }

        private int size() {
            return words.size();
        }
    }

    private static final class HistoryBuffer {
        private final int limit;
        private final Deque<String> items = new ArrayDeque<>();

        private HistoryBuffer(int limit) {
            this.limit = limit;
        }

        private void add(String text) {
            items.addFirst(text);
            while (items.size() > limit) {
                items.removeLast();
            }
        }

        private List<String> items() {
            return new ArrayList<>(items);
        }

        private int size() {
            return items.size();
        }
    }

    private static final class MetricTracker {
        private int inputs;
        private long chars;
        private long latencyMs;

        private void record(long newChars, long newLatencyMs) {
            inputs++;
            chars += newChars;
            latencyMs += newLatencyMs;
        }

        private String summary() {
            long average = inputs == 0 ? 0 : latencyMs / inputs;
            return "输入 " + inputs + " 次 | 字符 " + chars + " | 平均延迟 " + average + " ms";
        }
    }

    private static final class SimpleDocumentListener implements DocumentListener {
        private final Runnable onChange;

        private SimpleDocumentListener(Runnable onChange) {
            this.onChange = onChange;
        }

        @Override
        public void insertUpdate(DocumentEvent event) {
            onChange.run();
        }

        @Override
        public void removeUpdate(DocumentEvent event) {
            onChange.run();
        }

        @Override
        public void changedUpdate(DocumentEvent event) {
            onChange.run();
        }
    }
}
