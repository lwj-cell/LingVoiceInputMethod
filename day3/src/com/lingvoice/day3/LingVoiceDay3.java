package com.lingvoice.day3;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LingVoiceDay3 {
    private final JFrame frame = new JFrame("凌声输入法 - 综合体验版");
    private final JTextArea editor = new JTextArea(20, 58);
    private final JTextArea metricsArea = new JTextArea(7, 22);
    private final JTextField speechField = new JTextField();
    private final JTextField spokenField = new JTextField();
    private final JTextField targetField = new JTextField();
    private final JComboBox<WorkMode> modeBox = new JComboBox<>(WorkMode.values());
    private final JCheckBox noiseBox = new JCheckBox("降噪", true);
    private final JCheckBox punctuationBox = new JCheckBox("自动标点", true);
    private final JToggleButton themeButton = new JToggleButton("深色");
    private final DefaultListModel<String> dictionaryModel = new DefaultListModel<>();
    private final UserDictionary dictionary = new UserDictionary();
    private final InputEngine inputEngine = new InputEngine(dictionary);
    private final CommandEngine commandEngine = new CommandEngine();
    private final SessionMetrics metrics = new SessionMetrics();
    private final Deque<String> undoStack = new ArrayDeque<>();
    private boolean darkTheme;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LingVoiceDay3().show());
    }

    private void show() {
        editor.setLineWrap(true);
        editor.setWrapStyleWord(true);
        metricsArea.setEditable(false);
        metricsArea.setLineWrap(true);
        metricsArea.setWrapStyleWord(true);

        JButton inputButton = new JButton("输入");
        JButton undoButton = new JButton("撤销");
        JButton copyButton = new JButton("复制");
        JButton clearButton = new JButton("清空");

        JPanel top = new JPanel(new BorderLayout(8, 8));
        JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT));
        options.add(new JLabel("场景"));
        options.add(modeBox);
        options.add(noiseBox);
        options.add(punctuationBox);
        options.add(themeButton);
        top.add(options, BorderLayout.NORTH);

        JPanel inputLine = new JPanel(new BorderLayout(8, 8));
        inputLine.add(new JLabel("语音识别文本"), BorderLayout.WEST);
        inputLine.add(speechField, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(inputButton);
        actions.add(undoButton);
        actions.add(copyButton);
        actions.add(clearButton);
        inputLine.add(actions, BorderLayout.EAST);
        top.add(inputLine, BorderLayout.CENTER);

        JPanel dictionaryPanel = createDictionaryPanel();
        JPanel templatePanel = createTemplatePanel();
        JPanel metricsPanel = titledPanel("会话分析", new BorderLayout(6, 6));
        metricsPanel.add(new JScrollPane(metricsArea), BorderLayout.CENTER);

        JPanel right = new JPanel(new GridLayout(3, 1, 8, 8));
        right.add(dictionaryPanel);
        right.add(templatePanel);
        right.add(metricsPanel);

        inputButton.addActionListener(event -> acceptInput());
        speechField.addActionListener(event -> acceptInput());
        undoButton.addActionListener(event -> undo());
        copyButton.addActionListener(event -> copyAll());
        clearButton.addActionListener(event -> clearEditor());
        themeButton.addActionListener(event -> toggleTheme());

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(8, 8));
        frame.add(top, BorderLayout.NORTH);
        frame.add(new JScrollPane(editor), BorderLayout.CENTER);
        frame.add(right, BorderLayout.EAST);
        refreshDictionary();
        refreshMetrics();
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private JPanel createDictionaryPanel() {
        JPanel form = new JPanel(new GridLayout(3, 2, 6, 6));
        JButton addButton = new JButton("添加");
        form.add(new JLabel("识别词"));
        form.add(spokenField);
        form.add(new JLabel("输出词"));
        form.add(targetField);
        form.add(new JLabel(""));
        form.add(addButton);

        JPanel panel = titledPanel("用户词库", new BorderLayout(6, 6));
        panel.add(form, BorderLayout.NORTH);
        panel.add(new JScrollPane(new JList<>(dictionaryModel)), BorderLayout.CENTER);
        addButton.addActionListener(event -> addDictionaryEntry());
        return panel;
    }

    private JPanel createTemplatePanel() {
        JPanel panel = titledPanel("场景模板", new GridLayout(3, 1, 6, 6));
        JButton meetingButton = new JButton("会议纪要");
        JButton serviceButton = new JButton("客服记录");
        JButton noteButton = new JButton("学习笔记");
        meetingButton.addActionListener(event -> insertTemplate("【会议主题】\n【讨论内容】\n【待办事项】\n"));
        serviceButton.addActionListener(event -> insertTemplate("【客户问题】\n【处理过程】\n【处理结果】\n"));
        noteButton.addActionListener(event -> insertTemplate("【知识点】\n【解释】\n【例子】\n"));
        panel.add(meetingButton);
        panel.add(serviceButton);
        panel.add(noteButton);
        return panel;
    }

    private JPanel titledPanel(String title, java.awt.LayoutManager layout) {
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
        Command command = commandEngine.parse(raw);
        if (command.type() != CommandType.TEXT) {
            executeCommand(command, started);
            speechField.setText("");
            return;
        }

        undoStack.push(editor.getText());
        String text = inputEngine.process(
                raw,
                (WorkMode) modeBox.getSelectedItem(),
                noiseBox.isSelected(),
                punctuationBox.isSelected()
        );
        editor.append(text);
        metrics.recordInput(countVisibleChars(text), elapsedMillis(started));
        refreshMetrics();
        speechField.setText("");
    }

    private void executeCommand(Command command, long started) {
        switch (command.type()) {
            case NEW_LINE -> {
                undoStack.push(editor.getText());
                editor.append(System.lineSeparator());
            }
            case DELETE_COUNT -> {
                undoStack.push(editor.getText());
                deleteLastCodePoints(command.count());
            }
            case UNDO -> undo();
            case CLEAR -> clearEditor();
            case COPY -> copyAll();
            case TEXT -> { }
        }
        metrics.recordCommand(elapsedMillis(started));
        refreshMetrics();
    }

    private void addDictionaryEntry() {
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
    }

    private void insertTemplate(String template) {
        undoStack.push(editor.getText());
        editor.append(template);
    }

    private void undo() {
        if (undoStack.isEmpty()) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        editor.setText(undoStack.pop());
    }

    private void clearEditor() {
        undoStack.push(editor.getText());
        editor.setText("");
    }

    private void copyAll() {
        StringSelection selection = new StringSelection(editor.getText());
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
    }

    private void deleteLastCodePoints(int count) {
        String text = editor.getText();
        int end = text.length();
        int begin = end;
        for (int i = 0; i < count && begin > 0; i++) {
            begin = text.offsetByCodePoints(begin, -1);
        }
        editor.setText(text.substring(0, begin));
    }

    private void refreshDictionary() {
        dictionaryModel.clear();
        for (Map.Entry<String, String> entry : dictionary.entries()) {
            dictionaryModel.addElement(entry.getKey() + " -> " + entry.getValue());
        }
    }

    private void refreshMetrics() {
        metricsArea.setText(metrics.summary());
    }

    private void toggleTheme() {
        darkTheme = !darkTheme;
        Color background = darkTheme ? new Color(36, 39, 46) : Color.WHITE;
        Color foreground = darkTheme ? new Color(235, 238, 245) : Color.BLACK;
        editor.setBackground(background);
        editor.setForeground(foreground);
        metricsArea.setBackground(background);
        metricsArea.setForeground(foreground);
        themeButton.setText(darkTheme ? "浅色" : "深色");
    }

    private long elapsedMillis(long started) {
        return Math.max(1, (System.nanoTime() - started) / 1_000_000);
    }

    private long countVisibleChars(String text) {
        return text.codePoints().filter(codePoint -> !Character.isWhitespace(codePoint)).count();
    }

    private enum WorkMode {
        GENERAL("通用"),
        MEETING("会议"),
        SERVICE("客服");

        private final String label;

        WorkMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private enum CommandType {
        TEXT,
        NEW_LINE,
        DELETE_COUNT,
        UNDO,
        CLEAR,
        COPY
    }

    private record Command(CommandType type, int count) {
        private static Command text() {
            return new Command(CommandType.TEXT, 0);
        }
    }

    private static final class CommandEngine {
        private static final Pattern DIGIT_PATTERN = Pattern.compile("(\\d+)");

        private Command parse(String raw) {
            if (raw.equals("换行") || raw.equals("另起一行")) {
                return new Command(CommandType.NEW_LINE, 1);
            }
            if (raw.equals("撤销") || raw.equals("撤回")) {
                return new Command(CommandType.UNDO, 1);
            }
            if (raw.equals("清空") || raw.equals("清空全文")) {
                return new Command(CommandType.CLEAR, 1);
            }
            if (raw.equals("复制") || raw.equals("复制全文")) {
                return new Command(CommandType.COPY, 1);
            }
            if (raw.startsWith("删除前") || raw.startsWith("删掉前")) {
                return new Command(CommandType.DELETE_COUNT, Math.max(1, parseCount(raw)));
            }
            if (raw.equals("删除") || raw.equals("退格")) {
                return new Command(CommandType.DELETE_COUNT, 1);
            }
            return Command.text();
        }

        private int parseCount(String raw) {
            Matcher matcher = DIGIT_PATTERN.matcher(raw);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
            Map<Character, Integer> numbers = Map.of(
                    '一', 1, '二', 2, '三', 3, '四', 4, '五', 5,
                    '六', 6, '七', 7, '八', 8, '九', 9, '十', 10
            );
            for (int i = 0; i < raw.length(); i++) {
                Integer value = numbers.get(raw.charAt(i));
                if (value != null) {
                    return value;
                }
            }
            return 1;
        }
    }

    private static final class InputEngine {
        private final Map<String, String> punctuation = new LinkedHashMap<>();
        private final UserDictionary dictionary;
        private final NoiseFilter noiseFilter = new NoiseFilter();
        private final SmartPunctuation smartPunctuation = new SmartPunctuation();

        private InputEngine(UserDictionary dictionary) {
            this.dictionary = dictionary;
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

        private String process(String raw, WorkMode mode, boolean reduceNoise, boolean autoPunctuation) {
            String text = raw.trim();
            if (reduceNoise) {
                text = noiseFilter.clean(text);
            }
            text = text.replace("换行", "{NEW_LINE}");
            text = text.replace("另起一行", "{NEW_LINE}");
            text = text.replace("空格", "{SPACE}");
            for (Map.Entry<String, String> entry : punctuation.entrySet()) {
                text = text.replace(entry.getKey(), entry.getValue());
            }
            for (Map.Entry<String, String> entry : dictionary.entries()) {
                text = text.replace(entry.getKey(), entry.getValue());
            }
            text = text.replaceAll("\\s+", "");
            text = text.replace("{SPACE}", " ");
            text = text.replace("{NEW_LINE}", System.lineSeparator());
            text = formatByMode(text, mode);
            if (autoPunctuation) {
                text = smartPunctuation.finish(text);
            }
            return text;
        }

        private String formatByMode(String text, WorkMode mode) {
            if (mode == WorkMode.MEETING && (text.startsWith("结论") || text.startsWith("待办"))) {
                return System.lineSeparator() + text;
            }
            if (mode == WorkMode.SERVICE && (text.startsWith("客户") || text.startsWith("处理"))) {
                return System.lineSeparator() + text;
            }
            return text;
        }
    }

    private static final class NoiseFilter {
        private final List<String> fillers = List.of("嗯", "啊", "呃", "这个", "那个", "就是", "然后然后");

        private String clean(String raw) {
            String text = raw;
            for (String filler : fillers) {
                text = text.replace(filler, "");
            }
            return text;
        }
    }

    private static final class SmartPunctuation {
        private final List<String> questionWords = List.of("吗", "呢", "怎么", "为什么", "是否", "能不能", "是不是");

        private String finish(String text) {
            String stripped = text.stripTrailing();
            if (stripped.isEmpty() || endsWithPunctuation(stripped)) {
                return text;
            }
            for (String word : questionWords) {
                if (stripped.contains(word)) {
                    return text + "？";
                }
            }
            return text + "。";
        }

        private boolean endsWithPunctuation(String text) {
            return text.endsWith("。")
                    || text.endsWith("，")
                    || text.endsWith("？")
                    || text.endsWith("！")
                    || text.endsWith("：")
                    || text.endsWith("；");
        }
    }

    private static final class UserDictionary {
        private final Map<String, String> words = new LinkedHashMap<>();

        private UserDictionary() {
            words.put("林沃斯", "LingVoice");
            words.put("杰森", "JSON");
            words.put("接口", "API");
            words.put("阿萨尔", "ASR");
            words.put("语音输入法", "语音输入法");
        }

        private void put(String spoken, String target) {
            words.put(spoken, target);
        }

        private Iterable<Map.Entry<String, String>> entries() {
            return words.entrySet();
        }
    }

    private static final class SessionMetrics {
        private int inputCount;
        private int commandCount;
        private long chars;
        private long latencyMs;

        private void recordInput(long newChars, long newLatencyMs) {
            inputCount++;
            chars += newChars;
            latencyMs += newLatencyMs;
        }

        private void recordCommand(long newLatencyMs) {
            commandCount++;
            latencyMs += newLatencyMs;
        }

        private String summary() {
            int operations = inputCount + commandCount;
            long averageLatency = operations == 0 ? 0 : latencyMs / operations;
            double savedSeconds = chars * 0.28;
            return "输入次数：" + inputCount + System.lineSeparator()
                    + "命令次数：" + commandCount + System.lineSeparator()
                    + "累计字符：" + chars + System.lineSeparator()
                    + "平均延迟：" + averageLatency + " ms" + System.lineSeparator()
                    + "估算节省：" + String.format("%.1f", savedSeconds) + " 秒" + System.lineSeparator()
                    + "运行成本：本地处理，无云调用费用";
        }
    }
}
