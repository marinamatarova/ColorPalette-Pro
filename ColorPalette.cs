// ColorPalette.cs - Генератор цветовых палитр на C# (CLI + WinForms)
using System;
using System.Collections.Generic;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Windows.Forms;

namespace ColorPalette
{
    public class RGB
    {
        public int R { get; set; }
        public int G { get; set; }
        public int B { get; set; }
        public RGB(int r, int g, int b) { R = r; G = g; B = b; }
    }

    public class HSL
    {
        public double H { get; set; }
        public double S { get; set; }
        public double L { get; set; }
        public HSL(double h, double s, double l) { H = h; S = s; L = l; }
    }

    public static class ColorUtils
    {
        public static RGB HexToRgb(string hex)
        {
            hex = hex.Replace("#", "");
            if (hex.Length == 3)
                hex = new string(new char[] { hex[0], hex[0], hex[1], hex[1], hex[2], hex[2] });
            if (hex.Length != 6) throw new Exception("Неверный HEX");
            int r = Convert.ToInt32(hex.Substring(0,2), 16);
            int g = Convert.ToInt32(hex.Substring(2,2), 16);
            int b = Convert.ToInt32(hex.Substring(4,2), 16);
            return new RGB(r, g, b);
        }

        public static string RgbToHex(int r, int g, int b) => $"#{r:X2}{g:X2}{b:X2}";

        public static HSL RgbToHsl(int r, int g, int b)
        {
            double rf = r/255.0, gf = g/255.0, bf = b/255.0;
            double max = Math.Max(rf, Math.Max(gf, bf));
            double min = Math.Min(rf, Math.Min(gf, bf));
            double l = (max + min) / 2;
            double h = 0, s = 0;
            if (max != min)
            {
                double d = max - min;
                s = d / (1 - Math.Abs(2*l - 1));
                if (max == rf) h = (gf - bf) / d + (gf < bf ? 6 : 0);
                else if (max == gf) h = (bf - rf) / d + 2;
                else h = (rf - gf) / d + 4;
                h *= 60;
                if (h < 0) h += 360;
            }
            return new HSL(h, s*100, l*100);
        }

        public static RGB HslToRgb(double h, double s, double l)
        {
            h = h % 360; s /= 100; l /= 100;
            double c = (1 - Math.Abs(2*l - 1)) * s;
            double x = c * (1 - Math.Abs((h/60) % 2 - 1));
            double m = l - c/2;
            double r = 0, g = 0, b = 0;
            if (h < 60) { r = c; g = x; b = 0; }
            else if (h < 120) { r = x; g = c; b = 0; }
            else if (h < 180) { r = 0; g = c; b = x; }
            else if (h < 240) { r = 0; g = x; b = c; }
            else if (h < 300) { r = x; g = 0; b = c; }
            else { r = c; g = 0; b = x; }
            return new RGB((int)Math.Round((r+m)*255), (int)Math.Round((g+m)*255), (int)Math.Round((b+m)*255));
        }

        public static List<RGB> GeneratePalette(string baseHex, string scheme, int count)
        {
            RGB baseColor = HexToRgb(baseHex);
            HSL hsl = RgbToHsl(baseColor.R, baseColor.G, baseColor.B);
            List<RGB> colors = new List<RGB>();
            switch (scheme)
            {
                case "mono":
                    for (int i = 0; i < count; i++)
                    {
                        double factor = count > 1 ? (double)i / (count-1) : 0.5;
                        double newL = Math.Max(10, Math.Min(90, hsl.L * (0.5 + factor)));
                        colors.Add(HslToRgb(hsl.H, hsl.S, newL));
                    }
                    break;
                case "comp":
                    colors.Add(baseColor);
                    colors.Add(HslToRgb((hsl.H + 180) % 360, hsl.S, hsl.L));
                    break;
                case "analog":
                    for (int i = 0; i < count; i++)
                    {
                        double newH = (hsl.H - 30 + i * (60.0 / (count-1))) % 360;
                        colors.Add(HslToRgb(newH, hsl.S, hsl.L));
                    }
                    break;
                case "triad":
                    for (int i = 0; i < 3; i++)
                        colors.Add(HslToRgb((hsl.H + i * 120) % 360, hsl.S, hsl.L));
                    break;
                case "tetrad":
                    colors.Add(baseColor);
                    colors.Add(HslToRgb((hsl.H + 180) % 360, hsl.S, hsl.L));
                    colors.Add(HslToRgb((hsl.H + 90) % 360, hsl.S, hsl.L));
                    colors.Add(HslToRgb((hsl.H + 270) % 360, hsl.S, hsl.L));
                    break;
                default: throw new Exception("Неизвестная схема");
            }
            return colors;
        }
    }

    // ========== ИСТОРИЯ ==========
    public class HistoryEntry
    {
        public string Date { get; set; }
        public string Scheme { get; set; }
        public string Base { get; set; }
        public List<string> Colors { get; set; }
    }

    public static class HistoryManager
    {
        private static string file = "palette_history.json";
        public static List<HistoryEntry> Load()
        {
            if (File.Exists(file))
            {
                try
                {
                    string json = File.ReadAllText(file);
                    return JsonSerializer.Deserialize<List<HistoryEntry>>(json) ?? new List<HistoryEntry>();
                }
                catch { return new List<HistoryEntry>(); }
            }
            return new List<HistoryEntry>();
        }

        public static void Save(HistoryEntry entry)
        {
            var history = Load();
            history.Add(entry);
            if (history.Count > 100) history = history.Skip(history.Count - 100).ToList();
            string json = JsonSerializer.Serialize(history, new JsonSerializerOptions { WriteIndented = true });
            File.WriteAllText(file, json);
        }
    }

    // ========== ЭКСПОРТ ==========
    public static class Exporter
    {
        public static void ExportCSS(List<RGB> colors, string filename)
        {
            using (var sw = new StreamWriter(filename))
            {
                sw.WriteLine(":root {");
                for (int i = 0; i < colors.Count; i++)
                {
                    var c = colors[i];
                    sw.WriteLine($"  --color-{i+1}: {ColorUtils.RgbToHex(c.R, c.G, c.B)};");
                }
                sw.WriteLine("}");
            }
        }

        public static void ExportJSON(List<RGB> colors, string filename)
        {
            var data = colors.Select(c => new { r = c.R, g = c.G, b = c.B, hex = ColorUtils.RgbToHex(c.R, c.G, c.B) });
            string json = JsonSerializer.Serialize(data, new JsonSerializerOptions { WriteIndented = true });
            File.WriteAllText(filename, json);
        }

        public static void ExportHTML(List<RGB> colors, string filename)
        {
            using (var sw = new StreamWriter(filename))
            {
                sw.WriteLine("<html><head><style>body{font-family:sans-serif; display:flex; gap:20px;}</style></head><body>");
                foreach (var c in colors)
                {
                    string hex = ColorUtils.RgbToHex(c.R, c.G, c.B);
                    double lum = 0.299*c.R + 0.587*c.G + 0.114*c.B;
                    string color = lum < 140 ? "white" : "black";
                    sw.WriteLine($"<div style='background:{hex}; width:100px; height:100px; border-radius:8px; text-align:center; line-height:100px; color:{color};'>{hex}</div>");
                }
                sw.WriteLine("</body></html>");
            }
        }
    }

    // ========== CLI ==========
    class Program
    {
        static void Main(string[] args)
        {
            if (args.Length > 0 && args[0] == "--gui")
            {
                Application.EnableVisualStyles();
                Application.Run(new PaletteGUI());
                return;
            }
            // CLI
            string baseHex = null, scheme = "analog", exportFmt = null, output = null;
            int count = 5;
            bool random = false, history = false;
            for (int i = 0; i < args.Length; i++)
            {
                switch (args[i])
                {
                    case "--base": baseHex = args[++i]; break;
                    case "--scheme": scheme = args[++i]; break;
                    case "--count": count = int.Parse(args[++i]); break;
                    case "--random": random = true; break;
                    case "--export": exportFmt = args[++i]; break;
                    case "--output": output = args[++i]; break;
                    case "--history": history = true; break;
                }
            }
            if (history)
            {
                var hist = HistoryManager.Load();
                if (hist.Count == 0) Console.WriteLine("История пуста.");
                else
                {
                    Console.WriteLine("\n📋 ИСТОРИЯ ПАЛИТР");
                    foreach (var e in hist)
                        Console.WriteLine($"{e.Date.Substring(0,19)}  {e.Scheme}  {e.Colors.Count} цветов");
                }
                return;
            }
            if (random)
            {
                Random rand = new Random();
                int r = rand.Next(256), g = rand.Next(256), b = rand.Next(256);
                baseHex = ColorUtils.RgbToHex(r, g, b);
                Console.WriteLine($"Случайный базовый цвет: {baseHex}");
            }
            else if (string.IsNullOrEmpty(baseHex))
            {
                Console.Write("Введите базовый цвет (HEX, например #FF5733): ");
                baseHex = Console.ReadLine().Trim();
                if (!baseHex.StartsWith("#")) baseHex = "#" + baseHex;
            }
            try
            {
                var colors = ColorUtils.GeneratePalette(baseHex, scheme, count);
                Console.WriteLine($"\n🎨 Палитра '{scheme}' (база {baseHex}):");
                for (int i = 0; i < colors.Count; i++)
                {
                    var c = colors[i];
                    Console.WriteLine($"{i+1}. {ColorUtils.RgbToHex(c.R, c.G, c.B)}  RGB({c.R},{c.G},{c.B})");
                }
                // Сохранить историю
                HistoryManager.Save(new HistoryEntry
                {
                    Date = DateTime.Now.ToString("o"),
                    Scheme = scheme,
                    Base = baseHex,
                    Colors = colors.Select(c => ColorUtils.RgbToHex(c.R, c.G, c.B)).ToList()
                });
                // Экспорт
                if (!string.IsNullOrEmpty(exportFmt))
                {
                    string filename = output ?? $"palette.{exportFmt}";
                    if (exportFmt == "css") Exporter.ExportCSS(colors, filename);
                    else if (exportFmt == "json") Exporter.ExportJSON(colors, filename);
                    else if (exportFmt == "html") Exporter.ExportHTML(colors, filename);
                    else Console.WriteLine("Неизвестный формат");
                    Console.WriteLine($"Экспортировано в {filename}");
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Ошибка: {ex.Message}");
            }
        }
    }

    // ========== GUI ==========
    public class PaletteGUI : Form
    {
        private TextBox baseBox;
        private ComboBox schemeCombo;
        private NumericUpDown countUpDown;
        private Panel palettePanel;
        private TextBox historyBox;
        private List<RGB> currentColors;

        public PaletteGUI()
        {
            Text = "🎨 Палитра цветов";
            Size = new Size(700, 550);
            StartPosition = FormStartPosition.CenterScreen;

            var top = new FlowLayoutPanel();
            top.Controls.Add(new Label { Text = "Базовый цвет:" });
            baseBox = new TextBox { Text = "#FF5733", Width = 100 };
            top.Controls.Add(baseBox);
            var randomBtn = new Button { Text = "🎲 Случайный" };
            randomBtn.Click += (s, e) => RandomBase();
            top.Controls.Add(randomBtn);
            top.Controls.Add(new Label { Text = "Схема:" });
            schemeCombo = new ComboBox { DropDownStyle = ComboBoxStyle.DropDownList, Items = { "mono", "comp", "analog", "triad", "tetrad" }, SelectedIndex = 2 };
            schemeCombo.SelectedIndexChanged += (s, e) => Generate();
            top.Controls.Add(schemeCombo);
            top.Controls.Add(new Label { Text = "Кол-во:" });
            countUpDown = new NumericUpDown { Minimum = 2, Maximum = 10, Value = 5 };
            countUpDown.ValueChanged += (s, e) => Generate();
            top.Controls.Add(countUpDown);
            var genBtn = new Button { Text = "🔄 Обновить" };
            genBtn.Click += (s, e) => Generate();
            top.Controls.Add(genBtn);
            Controls.Add(top);

            palettePanel = new Panel { Dock = DockStyle.Fill, AutoScroll = true };
            Controls.Add(palettePanel);

            var bottom = new FlowLayoutPanel { Dock = DockStyle.Bottom };
            var cssBtn = new Button { Text = "💾 CSS" };
            cssBtn.Click += (s, e) => Export("css");
            bottom.Controls.Add(cssBtn);
            var jsonBtn = new Button { Text = "💾 JSON" };
            jsonBtn.Click += (s, e) => Export("json");
            bottom.Controls.Add(jsonBtn);
            var htmlBtn = new Button { Text = "💾 HTML" };
            htmlBtn.Click += (s, e) => Export("html");
            bottom.Controls.Add(htmlBtn);
            var historyBtn = new Button { Text = "📋 История" };
            historyBtn.Click += (s, e) => ShowHistory();
            bottom.Controls.Add(historyBtn);
            Controls.Add(bottom);

            historyBox = new TextBox { Multiline = true, Height = 100, ReadOnly = true, Dock = DockStyle.Bottom };
            Controls.Add(historyBox);

            Generate();
        }

        private void RandomBase()
        {
            Random rand = new Random();
            int r = rand.Next(256), g = rand.Next(256), b = rand.Next(256);
            baseBox.Text = ColorUtils.RgbToHex(r, g, b);
            Generate();
        }

        private void Generate()
        {
            try
            {
                string baseHex = baseBox.Text.Trim();
                string scheme = schemeCombo.SelectedItem.ToString();
                int count = (int)countUpDown.Value;
                currentColors = ColorUtils.GeneratePalette(baseHex, scheme, count);
                DisplayPalette(currentColors);
                // История
                HistoryManager.Save(new HistoryEntry
                {
                    Date = DateTime.Now.ToString("o"),
                    Scheme = scheme,
                    Base = baseHex,
                    Colors = currentColors.Select(c => ColorUtils.RgbToHex(c.R, c.G, c.B)).ToList()
                });
            }
            catch (Exception ex)
            {
                MessageBox.Show(ex.Message);
            }
        }

        private void DisplayPalette(List<RGB> colors)
        {
            palettePanel.Controls.Clear();
            foreach (var c in colors)
            {
                string hex = ColorUtils.RgbToHex(c.R, c.G, c.B);
                var panel = new Panel { Width = 100, Height = 80, BackColor = Color.FromArgb(c.R, c.G, c.B) };
                var label = new Label { Text = hex, Dock = DockStyle.Fill, TextAlign = ContentAlignment.MiddleCenter };
                double lum = 0.299*c.R + 0.587*c.G + 0.114*c.B;
                label.ForeColor = lum < 140 ? Color.White : Color.Black;
                panel.Controls.Add(label);
                palettePanel.Controls.Add(panel);
            }
        }

        private void Export(string fmt)
        {
            if (currentColors == null) return;
            var sfd = new SaveFileDialog { Filter = $"{fmt.ToUpper()} files|*.{fmt}", DefaultExt = fmt };
            if (sfd.ShowDialog() == DialogResult.OK)
            {
                try
                {
                    string filename = sfd.FileName;
                    if (fmt == "css") Exporter.ExportCSS(currentColors, filename);
                    else if (fmt == "json") Exporter.ExportJSON(currentColors, filename);
                    else if (fmt == "html") Exporter.ExportHTML(currentColors, filename);
                    MessageBox.Show($"Сохранено в {filename}");
                }
                catch (Exception ex)
                {
                    MessageBox.Show($"Ошибка: {ex.Message}");
                }
            }
        }

        private void ShowHistory()
        {
            var history = HistoryManager.Load();
            if (history.Count == 0)
            {
                MessageBox.Show("История пуста.");
                return;
            }
            historyBox.Text = string.Join("\n", history.Select(e => $"{e.Date.Substring(0,19)}  {e.Scheme}  {e.Colors.Count} цветов"));
        }
    }
}
