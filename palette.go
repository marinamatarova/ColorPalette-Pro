// palette.go - Генератор цветовых палитр на Go (CLI)
package main

import (
	"bufio"
	"encoding/json"
	"flag"
	"fmt"
	"math"
	"math/rand"
	"os"
	"strconv"
	"strings"
	"time"
)

// ========== ОСНОВНЫЕ ФУНКЦИИ ==========
type RGB struct {
	R int
	G int
	B int
}

type HSL struct {
	H float64
	S float64
	L float64
}

func hexToRgb(hex string) (RGB, error) {
	var r, g, b int
	hex = strings.TrimPrefix(hex, "#")
	if len(hex) == 3 {
		hex = string([]byte{hex[0], hex[0], hex[1], hex[1], hex[2], hex[2]})
	}
	if len(hex) != 6 {
		return RGB{}, fmt.Errorf("неверный HEX")
	}
	_, err := fmt.Sscanf(hex, "%02x%02x%02x", &r, &g, &b)
	return RGB{r, g, b}, err
}

func rgbToHex(r, g, b int) string {
	return fmt.Sprintf("#%02x%02x%02x", r, g, b)
}

func rgbToHsl(r, g, b int) HSL {
	rf := float64(r) / 255.0
	gf := float64(g) / 255.0
	bf := float64(b) / 255.0
	max := math.Max(rf, math.Max(gf, bf))
	min := math.Min(rf, math.Min(gf, bf))
	l := (max + min) / 2
	var h, s float64
	if max == min {
		h, s = 0, 0
	} else {
		d := max - min
		s = d / (1 - math.Abs(2*l-1))
		if max == rf {
			h = (gf - bf) / d
			if gf < bf {
				h += 6
			}
		} else if max == gf {
			h = (bf - rf) / d + 2
		} else {
			h = (rf - gf) / d + 4
		}
		h *= 60
		if h < 0 {
			h += 360
		}
	}
	return HSL{h, s * 100, l * 100}
}

func hslToRgb(h, s, l float64) RGB {
	h = math.Mod(h, 360)
	s /= 100
	l /= 100
	c := (1 - math.Abs(2*l-1)) * s
	x := c * (1 - math.Abs(math.Mod(h/60, 2)-1))
	m := l - c/2
	var r, g, b float64
	if h < 60 {
		r, g, b = c, x, 0
	} else if h < 120 {
		r, g, b = x, c, 0
	} else if h < 180 {
		r, g, b = 0, c, x
	} else if h < 240 {
		r, g, b = 0, x, c
	} else if h < 300 {
		r, g, b = x, 0, c
	} else {
		r, g, b = c, 0, x
	}
	return RGB{int((r + m) * 255), int((g + m) * 255), int((b + m) * 255)}
}

// ========== ГЕНЕРАЦИЯ ПАЛИТР ==========
func generatePalette(baseHex string, scheme string, count int) ([]RGB, error) {
	base, err := hexToRgb(baseHex)
	if err != nil {
		return nil, err
	}
	hsl := rgbToHsl(base.R, base.G, base.B)
	var colors []RGB
	switch scheme {
	case "mono":
		for i := 0; i < count; i++ {
			factor := float64(i) / float64(count-1)
			if count == 1 {
				factor = 0.5
			}
			newL := math.Max(10, math.Min(90, hsl.L*(0.5+factor)))
			colors = append(colors, hslToRgb(hsl.H, hsl.S, newL))
		}
	case "comp":
		colors = append(colors, base)
		colors = append(colors, hslToRgb(math.Mod(hsl.H+180, 360), hsl.S, hsl.L))
	case "analog":
		for i := 0; i < count; i++ {
			newH := math.Mod(hsl.H-30+float64(i)*(60/float64(count-1)), 360)
			colors = append(colors, hslToRgb(newH, hsl.S, hsl.L))
		}
	case "triad":
		for i := 0; i < 3; i++ {
			newH := math.Mod(hsl.H+float64(i)*120, 360)
			colors = append(colors, hslToRgb(newH, hsl.S, hsl.L))
		}
	case "tetrad":
		colors = append(colors, base)
		colors = append(colors, hslToRgb(math.Mod(hsl.H+180, 360), hsl.S, hsl.L))
		colors = append(colors, hslToRgb(math.Mod(hsl.H+90, 360), hsl.S, hsl.L))
		colors = append(colors, hslToRgb(math.Mod(hsl.H+270, 360), hsl.S, hsl.L))
	default:
		return nil, fmt.Errorf("неизвестная схема")
	}
	return colors, nil
}

// ========== ВИЗУАЛИЗАЦИЯ ==========
func printPalette(colors []RGB) {
	for i, c := range colors {
		hex := rgbToHex(c.R, c.G, c.B)
		bg := fmt.Sprintf("\033[48;2;%d;%d;%dm", c.R, c.G, c.B)
		reset := "\033[0m"
		lum := 0.299*float64(c.R) + 0.587*float64(c.G) + 0.114*float64(c.B)
		fg := "\033[38;2;255;255;255m"
		if lum < 140 {
			fg = "\033[38;2;255;255;255m"
		} else {
			fg = "\033[38;2;0;0;0m"
		}
		fmt.Printf("%s%s  %d. %s  RGB(%d,%d,%d)  %s\n", bg, fg, i+1, hex, c.R, c.G, c.B, reset)
	}
}

// ========== ЭКСПОРТ ==========
func exportCSS(colors []RGB, filename string) error {
	f, err := os.Create(filename)
	if err != nil {
		return err
	}
	defer f.Close()
	f.WriteString(":root {\n")
	for i, c := range colors {
		f.WriteString(fmt.Sprintf("  --color-%d: %s;\n", i+1, rgbToHex(c.R, c.G, c.B)))
	}
	f.WriteString("}\n")
	return nil
}

func exportJSON(colors []RGB, filename string) error {
	type ColorData struct {
		R   int    `json:"r"`
		G   int    `json:"g"`
		B   int    `json:"b"`
		Hex string `json:"hex"`
	}
	data := make([]ColorData, len(colors))
	for i, c := range colors {
		data[i] = ColorData{c.R, c.G, c.B, rgbToHex(c.R, c.G, c.B)}
	}
	jsonData, err := json.MarshalIndent(data, "", "  ")
	if err != nil {
		return err
	}
	return os.WriteFile(filename, jsonData, 0644)
}

func exportHTML(colors []RGB, filename string) error {
	f, err := os.Create(filename)
	if err != nil {
		return err
	}
	defer f.Close()
	f.WriteString("<html><head><style>body{font-family:sans-serif; display:flex; gap:20px;}</style></head><body>")
	for _, c := range colors {
		hex := rgbToHex(c.R, c.G, c.B)
		lum := 0.299*float64(c.R) + 0.587*float64(c.G) + 0.114*float64(c.B)
		color := "black"
		if lum < 140 {
			color = "white"
		}
		f.WriteString(fmt.Sprintf(`<div style="background:%s; width:100px; height:100px; border-radius:8px; text-align:center; line-height:100px; color:%s;">%s</div>`, hex, color, hex))
	}
	f.WriteString("</body></html>")
	return nil
}

// ========== ИСТОРИЯ ==========
type HistoryEntry struct {
	Date   string   `json:"date"`
	Scheme string   `json:"scheme"`
	Base   string   `json:"base"`
	Colors []string `json:"colors"`
}

const historyFile = "palette_history.json"

func loadHistory() []HistoryEntry {
	file, err := os.ReadFile(historyFile)
	if err != nil {
		return []HistoryEntry{}
	}
	var history []HistoryEntry
	json.Unmarshal(file, &history)
	return history
}

func saveHistoryEntry(entry HistoryEntry) {
	history := loadHistory()
	history = append(history, entry)
	if len(history) > 100 {
		history = history[len(history)-100:]
	}
	data, _ := json.MarshalIndent(history, "", "  ")
	os.WriteFile(historyFile, data, 0644)
}

// ========== CLI ==========
func main() {
	rand.Seed(time.Now().UnixNano())

	var (
		base     string
		scheme   string
		count    int
		random   bool
		export   string
		output   string
		history  bool
	)
	flag.StringVar(&base, "base", "", "Базовый цвет в HEX")
	flag.StringVar(&scheme, "scheme", "analog", "Схема (mono, comp, analog, triad, tetrad)")
	flag.IntVar(&count, "count", 5, "Количество цветов (2-10)")
	flag.BoolVar(&random, "random", false, "Случайный базовый цвет")
	flag.StringVar(&export, "export", "", "Экспорт (css, json, html)")
	flag.StringVar(&output, "output", "", "Имя файла для экспорта")
	flag.BoolVar(&history, "history", false, "Показать историю")
	flag.Parse()

	if history {
		hist := loadHistory()
		if len(hist) == 0 {
			fmt.Println("История пуста.")
		} else {
			fmt.Println("\n📋 ИСТОРИЯ ПАЛИТР")
			for _, entry := range hist {
				fmt.Printf("%s  %s  %d цветов\n", entry.Date[:19], entry.Scheme, len(entry.Colors))
			}
		}
		return
	}

	if random {
		r := rand.Intn(256)
		g := rand.Intn(256)
		b := rand.Intn(256)
		base = rgbToHex(r, g, b)
		fmt.Printf("Случайный базовый цвет: %s\n", base)
	} else if base == "" {
		reader := bufio.NewReader(os.Stdin)
		fmt.Print("Введите базовый цвет (HEX, например #FF5733): ")
		base, _ = reader.ReadString('\n')
		base = strings.TrimSpace(base)
		if !strings.HasPrefix(base, "#") {
			base = "#" + base
		}
	}

	colors, err := generatePalette(base, scheme, count)
	if err != nil {
		fmt.Println("Ошибка:", err)
		return
	}

	fmt.Printf("\n🎨 Палитра '%s' (база %s):\n", scheme, base)
	printPalette(colors)

	// Сохранить историю
	hexColors := make([]string, len(colors))
	for i, c := range colors {
		hexColors[i] = rgbToHex(c.R, c.G, c.B)
	}
	saveHistoryEntry(HistoryEntry{
		Date:   time.Now().Format(time.RFC3339),
		Scheme: scheme,
		Base:   base,
		Colors: hexColors,
	})

	if export != "" {
		filename := output
		if filename == "" {
			filename = fmt.Sprintf("palette.%s", export)
		}
		var err error
		switch export {
		case "css":
			err = exportCSS(colors, filename)
		case "json":
			err = exportJSON(colors, filename)
		case "html":
			err = exportHTML(colors, filename)
		default:
			fmt.Println("Неизвестный формат экспорта")
			return
		}
		if err != nil {
			fmt.Println("Ошибка экспорта:", err)
		} else {
			fmt.Printf("Экспортировано в %s\n", filename)
		}
	}
}
