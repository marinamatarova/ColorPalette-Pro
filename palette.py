#!/usr/bin/env python3
# palette.py - Генератор цветовых палитр на Python (CLI + Tkinter GUI)
"""
Поддерживает: 5 схем, экспорт в CSS/JSON/HTML/PNG, историю, визуализацию.
"""
import sys
import random
import json
import os
import math
from datetime import datetime
from typing import List, Tuple, Optional
import argparse
from PIL import Image, ImageDraw  # для PNG

try:
    import tkinter as tk
    from tkinter import ttk, filedialog, messagebox, scrolledtext
    GUI_AVAILABLE = True
except ImportError:
    GUI_AVAILABLE = False

# ========== ОСНОВНЫЕ ФУНКЦИИ ЦВЕТА ==========
def hex_to_rgb(hex_str: str) -> Tuple[int, int, int]:
    hex_str = hex_str.lstrip('#')
    if len(hex_str) == 3:
        hex_str = ''.join([c*2 for c in hex_str])
    return (int(hex_str[0:2], 16), int(hex_str[2:4], 16), int(hex_str[4:6], 16))

def rgb_to_hex(r: int, g: int, b: int) -> str:
    return f"#{r:02x}{g:02x}{b:02x}"

def rgb_to_hsl(r: int, g: int, b: int) -> Tuple[float, float, float]:
    rf = r / 255.0; gf = g / 255.0; bf = b / 255.0
    max_c = max(rf, gf, bf); min_c = min(rf, gf, bf)
    l = (max_c + min_c) / 2
    if max_c == min_c:
        h = s = 0.0
    else:
        d = max_c - min_c
        s = d / (1 - abs(2*l - 1))
        if max_c == rf:
            h = (gf - bf) / d % 6
        elif max_c == gf:
            h = (bf - rf) / d + 2
        else:
            h = (rf - gf) / d + 4
        h *= 60
        if h < 0: h += 360
    return (h, s*100, l*100)

def hsl_to_rgb(h: float, s: float, l: float) -> Tuple[int, int, int]:
    h = h % 360; s /= 100; l /= 100
    c = (1 - abs(2*l - 1)) * s
    x = c * (1 - abs((h/60) % 2 - 1))
    m = l - c/2
    if h < 60: r, g, b = c, x, 0
    elif h < 120: r, g, b = x, c, 0
    elif h < 180: r, g, b = 0, c, x
    elif h < 240: r, g, b = 0, x, c
    elif h < 300: r, g, b = x, 0, c
    else: r, g, b = c, 0, x
    return (int((r+m)*255), int((g+m)*255), int((b+m)*255))

# ========== ГЕНЕРАЦИЯ ПАЛИТР ==========
def generate_palette(base_hex: str, scheme: str, count: int = 5) -> List[Tuple[int, int, int]]:
    r, g, b = hex_to_rgb(base_hex)
    h, s, l = rgb_to_hsl(r, g, b)
    colors = []
    if scheme == 'mono':
        for i in range(count):
            factor = i / (count-1) if count > 1 else 0.5
            new_l = max(10, min(90, l * (0.5 + factor)))
            colors.append(hsl_to_rgb(h, s, new_l))
    elif scheme == 'comp':
        colors.append((r, g, b))
        colors.append(hsl_to_rgb((h + 180) % 360, s, l))
    elif scheme == 'analog':
        for i in range(count):
            new_h = (h - 30 + i * (60 / (count-1))) % 360
            colors.append(hsl_to_rgb(new_h, s, l))
    elif scheme == 'triad':
        for i in range(3):
            colors.append(hsl_to_rgb((h + i * 120) % 360, s, l))
    elif scheme == 'tetrad':
        colors.append((r, g, b))
        colors.append(hsl_to_rgb((h + 180) % 360, s, l))
        colors.append(hsl_to_rgb((h + 90) % 360, s, l))
        colors.append(hsl_to_rgb((h + 270) % 360, s, l))
    return colors

# ========== ВИЗУАЛИЗАЦИЯ ==========
def print_palette(colors: List[Tuple[int, int, int]]) -> None:
    for i, (r, g, b) in enumerate(colors):
        hex_c = rgb_to_hex(r, g, b)
        # ANSI цветной фон
        bg = f"\033[48;2;{r};{g};{b}m"
        reset = "\033[0m"
        # Определяем цвет текста (белый или чёрный)
        lum = 0.299*r + 0.587*g + 0.114*b
        fg = "\033[38;2;255;255;255m" if lum < 140 else "\033[38;2;0;0;0m"
        print(f"{bg}{fg}  {i+1}. {hex_c}  RGB({r},{g},{b})  {reset}")

# ========== ЭКСПОРТ ==========
def export_css(colors: List[Tuple[int, int, int]], filename: str):
    with open(filename, 'w', encoding='utf-8') as f:
        f.write(":root {\n")
        for i, (r, g, b) in enumerate(colors):
            hex_c = rgb_to_hex(r, g, b)
            f.write(f"  --color-{i+1}: {hex_c};\n")
        f.write("}\n")

def export_json(colors: List[Tuple[int, int, int]], filename: str):
    data = [{"r": r, "g": g, "b": b, "hex": rgb_to_hex(r, g, b)} for r, g, b in colors]
    with open(filename, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2, ensure_ascii=False)

def export_html(colors: List[Tuple[int, int, int]], filename: str):
    html = "<html><head><style>body{font-family:sans-serif; display:flex; gap:20px;}</style></head><body>"
    for r, g, b in colors:
        hex_c = rgb_to_hex(r, g, b)
        html += f'<div style="background:{hex_c}; width:100px; height:100px; border-radius:8px; text-align:center; line-height:100px; color:{"white" if (r*0.299+g*0.587+b*0.114)<140 else "black"};">{hex_c}</div>'
    html += "</body></html>"
    with open(filename, 'w', encoding='utf-8') as f:
        f.write(html)

def export_png(colors: List[Tuple[int, int, int]], filename: str):
    # Создаём изображение с цветными полосами
    width = 100 * len(colors)
    height = 100
    img = Image.new('RGB', (width, height), color='white')
    draw = ImageDraw.Draw(img)
    for i, (r, g, b) in enumerate(colors):
        x0 = i * 100
        x1 = (i+1) * 100
        draw.rectangle([x0, 0, x1, height], fill=(r, g, b))
    img.save(filename)

# ========== ИСТОРИЯ ==========
HISTORY_FILE = "palette_history.json"

def load_history() -> List[dict]:
    if os.path.exists(HISTORY_FILE):
        try:
            with open(HISTORY_FILE, 'r', encoding='utf-8') as f:
                return json.load(f)
        except: return []
    return []

def save_history_entry(entry: dict):
    history = load_history()
    history.append(entry)
    if len(history) > 100: history = history[-100:]
    with open(HISTORY_FILE, 'w', encoding='utf-8') as f:
        json.dump(history, f, indent=2, ensure_ascii=False)

# ========== CLI ==========
def cli():
    parser = argparse.ArgumentParser(description="Генератор цветовых палитр")
    parser.add_argument("--base", help="Базовый цвет в HEX (например #FF5733)")
    parser.add_argument("--scheme", choices=['mono','comp','analog','triad','tetrad'], default='analog', help="Схема")
    parser.add_argument("--count", type=int, default=5, help="Количество цветов (2-10)")
    parser.add_argument("--random", action="store_true", help="Случайный базовый цвет")
    parser.add_argument("--export", choices=['css','json','html','png'], help="Экспортировать")
    parser.add_argument("--output", help="Имя файла для экспорта")
    parser.add_argument("--history", action="store_true", help="Показать историю")
    parser.add_argument("--gui", action="store_true", help="Запустить GUI")
    args = parser.parse_args()

    if args.gui and GUI_AVAILABLE:
        root = tk.Tk()
        app = PaletteGUI(root)
        root.mainloop()
        return

    if args.history:
        history = load_history()
        if not history:
            print("История пуста.")
        else:
            print("\n📋 ИСТОРИЯ ПАЛИТР")
            for entry in history[-10:]:
                print(f"{entry['date'][:19]}  {entry['scheme']}  {len(entry['colors'])} цветов")
        return

    if args.random:
        base_hex = rgb_to_hex(random.randint(0,255), random.randint(0,255), random.randint(0,255))
        print(f"Случайный базовый цвет: {base_hex}")
    elif args.base:
        base_hex = args.base
    else:
        base_hex = input("Введите базовый цвет (HEX, например #FF5733): ").strip()
        if not base_hex.startswith('#'):
            base_hex = '#' + base_hex

    try:
        colors = generate_palette(base_hex, args.scheme, args.count)
    except:
        print("Ошибка: неверный HEX-цвет.")
        return

    print(f"\n🎨 Палитра '{args.scheme}' (база {base_hex}):")
    print_palette(colors)

    # Сохранить историю
    save_history_entry({
        "date": datetime.now().isoformat(),
        "scheme": args.scheme,
        "base": base_hex,
        "colors": [rgb_to_hex(r, g, b) for r, g, b in colors]
    })

    if args.export:
        filename = args.output or f"palette.{args.export}"
        if args.export == 'css':
            export_css(colors, filename)
        elif args.export == 'json':
            export_json(colors, filename)
        elif args.export == 'html':
            export_html(colors, filename)
        elif args.export == 'png':
            export_png(colors, filename)
        print(f"Экспортировано в {filename}")

# ========== GUI ==========
if GUI_AVAILABLE:
    class PaletteGUI:
        def __init__(self, root):
            self.root = root
            self.root.title("🎨 Палитра цветов")
            self.root.geometry("700x550")
            self.root.resizable(True, True)
            self.colors = []
            self.base_color = "#FF5733"
            self.scheme_var = tk.StringVar(value="analog")
            self.count_var = tk.IntVar(value=5)
            self.create_widgets()
            self.generate()

        def create_widgets(self):
            main = ttk.Frame(self.root, padding="10")
            main.pack(fill=tk.BOTH, expand=True)

            # Верхняя панель
            top = ttk.Frame(main)
            top.pack(fill=tk.X, pady=5)
            ttk.Label(top, text="Базовый цвет:").pack(side=tk.LEFT)
            self.hex_entry = ttk.Entry(top, width=10)
            self.hex_entry.insert(0, "#FF5733")
            self.hex_entry.pack(side=tk.LEFT, padx=5)
            ttk.Button(top, text="🎲 Случайный", command=self.random_base).pack(side=tk.LEFT, padx=5)

            ttk.Label(top, text="Схема:").pack(side=tk.LEFT, padx=(20,5))
            schemes = ['mono','comp','analog','triad','tetrad']
            ttk.Combobox(top, textvariable=self.scheme_var, values=schemes, state="readonly", width=10).pack(side=tk.LEFT)
            self.scheme_var.trace('w', lambda *a: self.generate())

            ttk.Label(top, text="Кол-во:").pack(side=tk.LEFT, padx=(20,5))
            ttk.Spinbox(top, from_=2, to=10, textvariable=self.count_var, width=5).pack(side=tk.LEFT)
            self.count_var.trace('w', lambda *a: self.generate())

            ttk.Button(top, text="🔄 Обновить", command=self.generate).pack(side=tk.LEFT, padx=10)

            # Область отображения палитры
            self.palette_frame = ttk.Frame(main)
            self.palette_frame.pack(fill=tk.BOTH, expand=True, pady=10)

            # Кнопки экспорта
            exp_frame = ttk.Frame(main)
            exp_frame.pack(fill=tk.X, pady=5)
            ttk.Button(exp_frame, text="💾 CSS", command=lambda: self.export('css')).pack(side=tk.LEFT, padx=5)
            ttk.Button(exp_frame, text="💾 JSON", command=lambda: self.export('json')).pack(side=tk.LEFT, padx=5)
            ttk.Button(exp_frame, text="💾 HTML", command=lambda: self.export('html')).pack(side=tk.LEFT, padx=5)
            ttk.Button(exp_frame, text="💾 PNG", command=lambda: self.export('png')).pack(side=tk.LEFT, padx=5)
            ttk.Button(exp_frame, text="📋 История", command=self.show_history).pack(side=tk.LEFT, padx=5)

            self.status_label = ttk.Label(main, text="Готов")
            self.status_label.pack(pady=5)

        def random_base(self):
            r = random.randint(0,255); g = random.randint(0,255); b = random.randint(0,255)
            self.hex_entry.delete(0, tk.END)
            self.hex_entry.insert(0, rgb_to_hex(r, g, b))
            self.generate()

        def generate(self):
            try:
                base = self.hex_entry.get().strip()
                scheme = self.scheme_var.get()
                count = self.count_var.get()
                self.colors = generate_palette(base, scheme, count)
                self.display_palette()
                self.status_label.config(text=f"Палитра '{scheme}' из {len(self.colors)} цветов")
                # Сохранить в историю
                save_history_entry({
                    "date": datetime.now().isoformat(),
                    "scheme": scheme,
                    "base": base,
                    "colors": [rgb_to_hex(r, g, b) for r, g, b in self.colors]
                })
            except Exception as e:
                messagebox.showerror("Ошибка", str(e))

        def display_palette(self):
            for widget in self.palette_frame.winfo_children():
                widget.destroy()
            for i, (r, g, b) in enumerate(self.colors):
                hex_c = rgb_to_hex(r, g, b)
                frame = tk.Frame(self.palette_frame, bg=hex_c, height=80, relief=tk.RIDGE, bd=2)
                frame.pack(side=tk.LEFT, fill=tk.BOTH, expand=True, padx=2, pady=2)
                label = tk.Label(frame, text=f"{hex_c}\nRGB({r},{g},{b})",
                                 bg=hex_c,
                                 fg="white" if (r*0.299 + g*0.587 + b*0.114) < 140 else "black",
                                 font=("Arial", 10))
                label.pack(expand=True)

        def export(self, fmt):
            if not self.colors:
                messagebox.showwarning("Нет палитры", "Сначала сгенерируйте палитру.")
                return
            filename = filedialog.asksaveasfilename(defaultextension=f".{fmt}", filetypes=[(fmt.upper(), f"*.{fmt}")])
            if not filename:
                return
            if fmt == 'css':
                export_css(self.colors, filename)
            elif fmt == 'json':
                export_json(self.colors, filename)
            elif fmt == 'html':
                export_html(self.colors, filename)
            elif fmt == 'png':
                export_png(self.colors, filename)
            messagebox.showinfo("Экспорт", f"Сохранено в {filename}")

        def show_history(self):
            history = load_history()
            if not history:
                messagebox.showinfo("История", "История пуста.")
                return
            win = tk.Toplevel(self.root)
            win.title("История палитр")
            win.geometry("500x400")
            text = scrolledtext.ScrolledText(win, wrap=tk.WORD)
            text.pack(fill=tk.BOTH, expand=True)
            for entry in history[-20:]:
                text.insert(tk.END, f"{entry['date'][:19]}  {entry['scheme']}  {len(entry['colors'])} цветов\n")
            text.config(state='disabled')

if __name__ == "__main__":
    cli()
