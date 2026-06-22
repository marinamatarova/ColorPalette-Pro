// ColorPalette.java - Генератор цветовых палитр на Java (CLI + Swing GUI)
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import com.google.gson.*;

public class ColorPalette {
    // ========== ОСНОВНЫЕ ФУНКЦИИ ==========
    static class RGB {
        int r, g, b;
        RGB(int r, int g, int b) { this.r = r; this.g = g; this.b = b; }
    }
    static class HSL {
        double h, s, l;
        HSL(double h, double s, double l) { this.h = h; this.s = s; this.l = l; }
    }

    static RGB hexToRgb(String hex) throws Exception {
        hex = hex.replace("#", "");
        if (hex.length() == 3) {
            hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
        }
        if (hex.length() != 6) throw new Exception("Неверный HEX");
        int r = Integer.parseInt(hex.substring(0,2), 16);
        int g = Integer.parseInt(hex.substring(2,4), 16);
        int b = Integer.parseInt(hex.substring(4,6), 16);
        return new RGB(r, g, b);
    }

    static String rgbToHex(int r, int g, int b) {
        return String.format("#%02x%02x%02x", r, g, b);
    }

    static HSL rgbToHsl(int r, int g, int b) {
        double rf = r/255.0, gf = g/255.0, bf = b/255.0;
        double max = Math.max(rf, Math.max(gf, bf));
        double min = Math.min(rf, Math.min(gf, bf));
        double l = (max + min) / 2;
        double h = 0, s = 0;
        if (max != min) {
            double d = max - min;
            s = d / (1 - Math.abs(2*l - 1));
            if (max == rf) h = (gf - bf) / d + (gf < bf ? 6 : 0);
            else if (max == gf) h = (bf - rf) / d + 2;
            else h = (rf - gf) / d + 4;
            h *= 60;
            if (h < 0) h += 360;
        }
        return new HSL(h, s*100, l*100);
    }

    static RGB hslToRgb(double h, double s, double l) {
        h = h % 360; s /= 100; l /= 100;
        double c = (1 - Math.abs(2*l - 1)) * s;
        double x = c * (1 - Math.abs((h/60) % 2 - 1));
        double m = l - c/2;
        double r=0, g=0, b=0;
        if (h < 60) { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        return new RGB((int)Math.round((r+m)*255), (int)Math.round((g+m)*255), (int)Math.round((b+m)*255));
    }

    // ========== ГЕНЕРАЦИЯ ==========
    static List<RGB> generatePalette(String baseHex, String scheme, int count) throws Exception {
        RGB base = hexToRgb(baseHex);
        HSL hsl = rgbToHsl(base.r, base.g, base.b);
        List<RGB> colors = new ArrayList<>();
        switch (scheme) {
            case "mono":
                for (int i = 0; i < count; i++) {
                    double factor = count > 1 ? (double)i / (count-1) : 0.5;
                    double newL = Math.max(10, Math.min(90, hsl.l * (0.5 + factor)));
                    colors.add(hslToRgb(hsl.h, hsl.s, newL));
                }
                break;
            case "comp":
                colors.add(base);
                colors.add(hslToRgb((hsl.h + 180) % 360, hsl.s, hsl.l));
                break;
            case "analog":
                for (int i = 0; i < count; i++) {
                    double newH = (hsl.h - 30 + i * (60.0 / (count-1))) % 360;
                    colors.add(hslToRgb(newH, hsl.s, hsl.l));
                }
                break;
            case "triad":
                for (int i = 0; i < 3; i++) {
                    colors.add(hslToRgb((hsl.h + i * 120) % 360, hsl.s, hsl.l));
                }
                break;
            case "tetrad":
                colors.add(base);
                colors.add(hslToRgb((hsl.h + 180) % 360, hsl.s, hsl.l));
                colors.add(hslToRgb((hsl.h + 90) % 360, hsl.s, hsl.l));
                colors.add(hslToRgb((hsl.h + 270) % 360, hsl.s, hsl.l));
                break;
            default: throw new Exception("Неизвестная схема");
        }
        return colors;
    }

    // ========== ВИЗУАЛИЗАЦИЯ ==========
    static void printPalette(List<RGB> colors) {
        for (int i = 0; i < colors.size(); i++) {
            RGB c = colors.get(i);
            String hex = rgbToHex(c.r, c.g, c.b);
            // ANSI цвета не удобны в Java, выводим просто текст
            System.out.printf("%d. %s  RGB(%d,%d,%d)\n", i+1, hex, c.r, c.g, c.b);
        }
    }

    // ========== ЭКСПОРТ ==========
    static void exportCSS(List<RGB> colors, String filename) throws IOException {
        try (PrintWriter pw = new PrintWriter(filename)) {
            pw.println(":root {");
            for (int i = 0; i < colors.size(); i++) {
                RGB c = colors.get(i);
                pw.printf("  --color-%d: %s;\n", i+1, rgbToHex(c.r, c.g, c.b));
            }
            pw.println("}");
        }
    }

    static void exportJSON(List<RGB> colors, String filename) throws IOException {
        List<Map<String, Object>> data = new ArrayList<>();
        for (RGB c : colors) {
            Map<String, Object> map = new HashMap<>();
            map.put("r", c.r);
            map.put("g", c.g);
            map.put("b", c.b);
            map.put("hex", rgbToHex(c.r, c.g, c.b));
            data.add(map);
        }
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter fw = new FileWriter(filename)) {
            gson.toJson(data, fw);
        }
    }

    static void exportHTML(List<RGB> colors, String filename) throws IOException {
        try (PrintWriter pw = new PrintWriter(filename)) {
            pw.println("<html><head><style>body{font-family:sans-serif; display:flex; gap:20px;}</style></head><body>");
            for (RGB c : colors) {
                String hex = rgbToHex(c.r, c.g, c.b);
                double lum = 0.299*c.r + 0.587*c.g + 0.114*c.b;
                String color = lum < 140 ? "white" : "black";
                pw.printf("<div style='background:%s; width:100px; height:100px; border-radius:8px; text-align:center; line-height:100px; color:%s;'>%s</div>", hex, color, hex);
            }
            pw.println("</body></html>");
        }
    }

    // ========== ИСТОРИЯ ==========
    static class HistoryEntry {
        String date;
        String scheme;
        String base;
        List<String> colors;
    }

    static final String HISTORY_FILE = "palette_history.json";
    static Gson gson = new GsonBuilder().setPrettyPrinting().create();

    static List<HistoryEntry> loadHistory() {
        try {
            String content = new String(Files.readAllBytes(Paths.get(HISTORY_FILE)));
            Type listType = new com.google.gson.reflect.TypeToken<List<HistoryEntry>>(){}.getType();
            return gson.fromJson(content, listType);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    static void saveHistoryEntry(HistoryEntry entry) {
        List<HistoryEntry> history = loadHistory();
        history.add(entry);
        if (history.size() > 100) history = history.subList(history.size()-100, history.size());
        try (FileWriter fw = new FileWriter(HISTORY_FILE)) {
            gson.toJson(history, fw);
        } catch (IOException e) {}
    }

    // ========== CLI ==========
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--gui")) {
            SwingUtilities.invokeLater(() -> new PaletteGUI().setVisible(true));
            return;
        }
        // CLI parsing
        String base = null, scheme = "analog", export = null, output = null;
        int count = 5;
        boolean random = false, history = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--base": base = args[++i]; break;
                case "--scheme": scheme = args[++i]; break;
                case "--count": count = Integer.parseInt(args[++i]); break;
                case "--random": random = true; break;
                case "--export": export = args[++i]; break;
                case "--output": output = args[++i]; break;
                case "--history": history = true; break;
            }
        }
        if (history) {
            List<HistoryEntry> hist = loadHistory();
            if (hist.isEmpty()) System.out.println("История пуста.");
            else {
                System.out.println("\n📋 ИСТОРИЯ ПАЛИТР");
                for (HistoryEntry e : hist) {
                    System.out.printf("%s  %s  %d цветов\n", e.date.substring(0,19), e.scheme, e.colors.size());
                }
            }
            return;
        }
        if (random) {
            Random rand = new Random();
            int r = rand.nextInt(256), g = rand.nextInt(256), b = rand.nextInt(256);
            base = rgbToHex(r, g, b);
            System.out.println("Случайный базовый цвет: " + base);
        } else if (base == null) {
            System.out.print("Введите базовый цвет (HEX, например #FF5733): ");
            Scanner sc = new Scanner(System.in);
            base = sc.nextLine().trim();
            if (!base.startsWith("#")) base = "#" + base;
        }
        List<RGB> colors = generatePalette(base, scheme, count);
        System.out.printf("\n🎨 Палитра '%s' (база %s):\n", scheme, base);
        printPalette(colors);
        // Сохранить историю
        HistoryEntry entry = new HistoryEntry();
        entry.date = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        entry.scheme = scheme;
        entry.base = base;
        entry.colors = new ArrayList<>();
        for (RGB c : colors) entry.colors.add(rgbToHex(c.r, c.g, c.b));
        saveHistoryEntry(entry);
        // Экспорт
        if (export != null) {
            String filename = output != null ? output : "palette." + export;
            if (export.equals("css")) exportCSS(colors, filename);
            else if (export.equals("json")) exportJSON(colors, filename);
            else if (export.equals("html")) exportHTML(colors, filename);
            else System.out.println("Неизвестный формат");
            System.out.println("Экспортировано в " + filename);
        }
    }

    // ========== GUI ==========
    static class PaletteGUI extends JFrame {
        private JTextField baseField;
        private JComboBox<String> schemeCombo;
        private JSpinner countSpinner;
        private JPanel palettePanel;
        private JTextArea historyArea;
        private List<RGB> currentColors;

        public PaletteGUI() {
            setTitle("🎨 Палитра цветов");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setSize(700, 550);
            setLayout(new BorderLayout());

            JPanel top = new JPanel(new FlowLayout());
            top.add(new JLabel("Базовый цвет:"));
            baseField = new JTextField(10);
            baseField.setText("#FF5733");
            top.add(baseField);
            JButton randomBtn = new JButton("🎲 Случайный");
            randomBtn.addActionListener(e -> randomBase());
            top.add(randomBtn);
            top.add(new JLabel("Схема:"));
            schemeCombo = new JComboBox<>(new String[]{"mono","comp","analog","triad","tetrad"});
            schemeCombo.setSelectedItem("analog");
            schemeCombo.addActionListener(e -> generate());
            top.add(schemeCombo);
            top.add(new JLabel("Кол-во:"));
            countSpinner = new JSpinner(new SpinnerNumberModel(5, 2, 10, 1));
            countSpinner.addChangeListener(e -> generate());
            top.add(countSpinner);
            JButton genBtn = new JButton("🔄 Обновить");
            genBtn.addActionListener(e -> generate());
            top.add(genBtn);
            add(top, BorderLayout.NORTH);

            palettePanel = new JPanel(new FlowLayout());
            palettePanel.setPreferredSize(new Dimension(600, 120));
            add(palettePanel, BorderLayout.CENTER);

            JPanel bottom = new JPanel(new FlowLayout());
            JButton cssBtn = new JButton("💾 CSS");
            cssBtn.addActionListener(e -> export("css"));
            bottom.add(cssBtn);
            JButton jsonBtn = new JButton("💾 JSON");
            jsonBtn.addActionListener(e -> export("json"));
            bottom.add(jsonBtn);
            JButton htmlBtn = new JButton("💾 HTML");
            htmlBtn.addActionListener(e -> export("html"));
            bottom.add(htmlBtn);
            JButton historyBtn = new JButton("📋 История");
            historyBtn.addActionListener(e -> showHistory());
            bottom.add(historyBtn);
            add(bottom, BorderLayout.SOUTH);

            historyArea = new JTextArea(5, 50);
            historyArea.setEditable(false);
            add(new JScrollPane(historyArea), BorderLayout.AFTER_LAST_LINE);

            generate();
            setVisible(true);
        }

        private void randomBase() {
            Random rand = new Random();
            int r = rand.nextInt(256), g = rand.nextInt(256), b = rand.nextInt(256);
            baseField.setText(rgbToHex(r, g, b));
            generate();
        }

        private void generate() {
            try {
                String base = baseField.getText().trim();
                String scheme = (String) schemeCombo.getSelectedItem();
                int count = (Integer) countSpinner.getValue();
                currentColors = generatePalette(base, scheme, count);
                displayPalette(currentColors);
                // Сохранить историю
                HistoryEntry entry = new HistoryEntry();
                entry.date = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                entry.scheme = scheme;
                entry.base = base;
                entry.colors = new ArrayList<>();
                for (RGB c : currentColors) entry.colors.add(rgbToHex(c.r, c.g, c.b));
                saveHistoryEntry(entry);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage());
            }
        }

        private void displayPalette(List<RGB> colors) {
            palettePanel.removeAll();
            for (RGB c : colors) {
                String hex = rgbToHex(c.r, c.g, c.b);
                JPanel panel = new JPanel();
                panel.setBackground(Color.decode(hex));
                panel.setPreferredSize(new Dimension(100, 80));
                JLabel label = new JLabel(hex);
                double lum = 0.299*c.r + 0.587*c.g + 0.114*c.b;
                label.setForeground(lum < 140 ? Color.WHITE : Color.BLACK);
                panel.add(label);
                palettePanel.add(panel);
            }
            palettePanel.revalidate();
            palettePanel.repaint();
        }

        private void export(String fmt) {
            if (currentColors == null) return;
            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new File("palette." + fmt));
            if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    String filename = fc.getSelectedFile().getAbsolutePath();
                    if (fmt.equals("css")) exportCSS(currentColors, filename);
                    else if (fmt.equals("json")) exportJSON(currentColors, filename);
                    else if (fmt.equals("html")) exportHTML(currentColors, filename);
                    JOptionPane.showMessageDialog(this, "Сохранено в " + filename);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Ошибка: " + ex.getMessage());
                }
            }
        }

        private void showHistory() {
            List<HistoryEntry> history = loadHistory();
            if (history.isEmpty()) {
                JOptionPane.showMessageDialog(this, "История пуста.");
                return;
            }
            StringBuilder sb = new StringBuilder();
            for (HistoryEntry e : history) {
                sb.append(e.date.substring(0,19)).append("  ").append(e.scheme).append("  ").append(e.colors.size()).append(" цветов\n");
            }
            historyArea.setText(sb.toString());
        }
    }
}
