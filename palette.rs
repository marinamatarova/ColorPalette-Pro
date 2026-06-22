// palette.rs - Генератор цветовых палитр на Rust (CLI)
use clap::{Arg, App};
use rand::Rng;
use serde::{Serialize, Deserialize};
use std::fs;
use std::io::{self, Write};
use std::time::{SystemTime, UNIX_EPOCH};
use chrono::Local;

// ========== ОСНОВНЫЕ ФУНКЦИИ ==========
#[derive(Debug, Clone, Copy)]
struct RGB {
    r: u8,
    g: u8,
    b: u8,
}

struct HSL {
    h: f64,
    s: f64,
    l: f64,
}

fn hex_to_rgb(hex: &str) -> Result<RGB, String> {
    let hex = hex.trim_start_matches('#');
    if hex.len() == 3 {
        let chars: Vec<char> = hex.chars().collect();
        let expanded = format!("{}{}{}{}{}{}", chars[0], chars[0], chars[1], chars[1], chars[2], chars[2]);
        return hex_to_rgb(&expanded);
    }
    if hex.len() != 6 {
        return Err("Неверный HEX".to_string());
    }
    let r = u8::from_str_radix(&hex[0..2], 16).map_err(|_| "Ошибка парсинга")?;
    let g = u8::from_str_radix(&hex[2..4], 16).map_err(|_| "Ошибка парсинга")?;
    let b = u8::from_str_radix(&hex[4..6], 16).map_err(|_| "Ошибка парсинга")?;
    Ok(RGB { r, g, b })
}

fn rgb_to_hex(r: u8, g: u8, b: u8) -> String {
    format!("#{:02x}{:02x}{:02x}", r, g, b)
}

fn rgb_to_hsl(r: u8, g: u8, b: u8) -> HSL {
    let rf = r as f64 / 255.0;
    let gf = g as f64 / 255.0;
    let bf = b as f64 / 255.0;
    let max = rf.max(gf).max(bf);
    let min = rf.min(gf).min(bf);
    let l = (max + min) / 2.0;
    let (h, s) = if max == min {
        (0.0, 0.0)
    } else {
        let d = max - min;
        let s = d / (1.0 - (2.0 * l - 1.0).abs());
        let h = if max == rf {
            (gf - bf) / d + if gf < bf { 6.0 } else { 0.0 }
        } else if max == gf {
            (bf - rf) / d + 2.0
        } else {
            (rf - gf) / d + 4.0
        } * 60.0;
        (h % 360.0, s * 100.0)
    };
    HSL { h, s, l: l * 100.0 }
}

fn hsl_to_rgb(h: f64, s: f64, l: f64) -> RGB {
    let h = h % 360.0;
    let s = s / 100.0;
    let l = l / 100.0;
    let c = (1.0 - (2.0 * l - 1.0).abs()) * s;
    let x = c * (1.0 - ((h / 60.0) % 2.0 - 1.0).abs());
    let m = l - c / 2.0;
    let (r, g, b) = if h < 60.0 {
        (c, x, 0.0)
    } else if h < 120.0 {
        (x, c, 0.0)
    } else if h < 180.0 {
        (0.0, c, x)
    } else if h < 240.0 {
        (0.0, x, c)
    } else if h < 300.0 {
        (x, 0.0, c)
    } else {
        (c, 0.0, x)
    };
    RGB {
        r: ((r + m) * 255.0).round() as u8,
        g: ((g + m) * 255.0).round() as u8,
        b: ((b + m) * 255.0).round() as u8,
    }
}

// ========== ГЕНЕРАЦИЯ ПАЛИТР ==========
fn generate_palette(base_hex: &str, scheme: &str, count: usize) -> Result<Vec<RGB>, String> {
    let base = hex_to_rgb(base_hex)?;
    let hsl = rgb_to_hsl(base.r, base.g, base.b);
    let mut colors = Vec::new();
    match scheme {
        "mono" => {
            for i in 0..count {
                let factor = if count > 1 { i as f64 / (count - 1) as f64 } else { 0.5 };
                let new_l = (10.0f64).max((90.0f64).min(hsl.l * (0.5 + factor)));
                colors.push(hsl_to_rgb(hsl.h, hsl.s, new_l));
            }
        }
        "comp" => {
            colors.push(base);
            colors.push(hsl_to_rgb((hsl.h + 180.0) % 360.0, hsl.s, hsl.l));
        }
        "analog" => {
            for i in 0..count {
                let new_h = (hsl.h - 30.0 + i as f64 * (60.0 / (count - 1) as f64)) % 360.0;
                colors.push(hsl_to_rgb(new_h, hsl.s, hsl.l));
            }
        }
        "triad" => {
            for i in 0..3 {
                let new_h = (hsl.h + i as f64 * 120.0) % 360.0;
                colors.push(hsl_to_rgb(new_h, hsl.s, hsl.l));
            }
        }
        "tetrad" => {
            colors.push(base);
            colors.push(hsl_to_rgb((hsl.h + 180.0) % 360.0, hsl.s, hsl.l));
            colors.push(hsl_to_rgb((hsl.h + 90.0) % 360.0, hsl.s, hsl.l));
            colors.push(hsl_to_rgb((hsl.h + 270.0) % 360.0, hsl.s, hsl.l));
        }
        _ => return Err("Неизвестная схема".to_string()),
    }
    Ok(colors)
}

// ========== ВИЗУАЛИЗАЦИЯ ==========
fn print_palette(colors: &[RGB]) {
    for (i, c) in colors.iter().enumerate() {
        let hex = rgb_to_hex(c.r, c.g, c.b);
        let bg = format!("\x1b[48;2;{};{};{}m", c.r, c.g, c.b);
        let reset = "\x1b[0m";
        let lum = 0.299 * c.r as f64 + 0.587 * c.g as f64 + 0.114 * c.b as f64;
        let fg = if lum < 140.0 { "\x1b[38;2;255;255;255m" } else { "\x1b[38;2;0;0;0m" };
        println!("{}{}  {}. {}  RGB({},{},{})  {}", bg, fg, i+1, hex, c.r, c.g, c.b, reset);
    }
}

// ========== ЭКСПОРТ ==========
fn export_css(colors: &[RGB], filename: &str) -> Result<(), Box<dyn std::error::Error>> {
    let mut content = String::from(":root {\n");
    for (i, c) in colors.iter().enumerate() {
        content.push_str(&format!("  --color-{}: {};\n", i+1, rgb_to_hex(c.r, c.g, c.b)));
    }
    content.push_str("}\n");
    fs::write(filename, content)?;
    Ok(())
}

fn export_json(colors: &[RGB], filename: &str) -> Result<(), Box<dyn std::error::Error>> {
    #[derive(Serialize)]
    struct ColorData {
        r: u8,
        g: u8,
        b: u8,
        hex: String,
    }
    let data: Vec<ColorData> = colors.iter().map(|c| ColorData {
        r: c.r,
        g: c.g,
        b: c.b,
        hex: rgb_to_hex(c.r, c.g, c.b),
    }).collect();
    let json = serde_json::to_string_pretty(&data)?;
    fs::write(filename, json)?;
    Ok(())
}

fn export_html(colors: &[RGB], filename: &str) -> Result<(), Box<dyn std::error::Error>> {
    let mut content = String::from("<html><head><style>body{font-family:sans-serif; display:flex; gap:20px;}</style></head><body>");
    for c in colors {
        let hex = rgb_to_hex(c.r, c.g, c.b);
        let lum = 0.299 * c.r as f64 + 0.587 * c.g as f64 + 0.114 * c.b as f64;
        let text_color = if lum < 140.0 { "white" } else { "black" };
        content.push_str(&format!(
            r#"<div style="background:{}; width:100px; height:100px; border-radius:8px; text-align:center; line-height:100px; color:{};">{}</div>"#,
            hex, text_color, hex
        ));
    }
    content.push_str("</body></html>");
    fs::write(filename, content)?;
    Ok(())
}

// ========== ИСТОРИЯ ==========
#[derive(Serialize, Deserialize, Clone)]
struct HistoryEntry {
    date: String,
    scheme: String,
    base: String,
    colors: Vec<String>,
}

const HISTORY_FILE: &str = "palette_history.json";

fn load_history() -> Vec<HistoryEntry> {
    if let Ok(data) = fs::read_to_string(HISTORY_FILE) {
        if let Ok(entries) = serde_json::from_str(&data) {
            return entries;
        }
    }
    Vec::new()
}

fn save_history_entry(entry: HistoryEntry) {
    let mut history = load_history();
    history.push(entry);
    if history.len() > 100 {
        history = history[history.len()-100..].to_vec();
    }
    let json = serde_json::to_string_pretty(&history).unwrap();
    fs::write(HISTORY_FILE, json).unwrap();
}

// ========== CLI ==========
fn read_line(prompt: &str) -> String {
    print!("{}", prompt);
    io::stdout().flush().unwrap();
    let mut input = String::new();
    io::stdin().read_line(&mut input).unwrap();
    input.trim().to_string()
}

fn main() {
    let matches = App::new("Palette Generator")
        .arg(Arg::with_name("base").long("base").takes_value(true).help("Базовый цвет в HEX"))
        .arg(Arg::with_name("scheme").long("scheme").takes_value(true).default_value("analog").help("Схема"))
        .arg(Arg::with_name("count").long("count").takes_value(true).default_value("5").help("Количество цветов"))
        .arg(Arg::with_name("random").long("random").help("Случайный базовый цвет"))
        .arg(Arg::with_name("export").long("export").takes_value(true).help("Экспорт (css, json, html)"))
        .arg(Arg::with_name("output").long("output").takes_value(true).help("Имя файла"))
        .arg(Arg::with_name("history").long("history").help("Показать историю"))
        .get_matches();

    if matches.is_present("history") {
        let history = load_history();
        if history.is_empty() {
            println!("История пуста.");
        } else {
            println!("\n📋 ИСТОРИЯ ПАЛИТР");
            for entry in history.iter().take(10) {
                println!("{}  {}  {} цветов", &entry.date[..19], entry.scheme, entry.colors.len());
            }
        }
        return;
    }

    let base = if matches.is_present("random") {
        let r = rand::thread_rng().gen_range(0..=255);
        let g = rand::thread_rng().gen_range(0..=255);
        let b = rand::thread_rng().gen_range(0..=255);
        let hex = rgb_to_hex(r, g, b);
        println!("Случайный базовый цвет: {}", hex);
        hex
    } else if let Some(b) = matches.value_of("base") {
        b.to_string()
    } else {
        read_line("Введите базовый цвет (HEX, например #FF5733): ")
    };

    let scheme = matches.value_of("scheme").unwrap_or("analog");
    let count: usize = matches.value_of("count").unwrap().parse().unwrap_or(5);

    match generate_palette(&base, scheme, count) {
        Ok(colors) => {
            println!("\n🎨 Палитра '{}' (база {}):", scheme, base);
            print_palette(&colors);
            // Сохранить историю
            let hex_colors: Vec<String> = colors.iter().map(|c| rgb_to_hex(c.r, c.g, c.b)).collect();
            save_history_entry(HistoryEntry {
                date: Local::now().to_rfc3339(),
                scheme: scheme.to_string(),
                base: base.clone(),
                colors: hex_colors,
            });
            // Экспорт
            if let Some(export_fmt) = matches.value_of("export") {
                let filename = matches.value_of("output").unwrap_or(&format!("palette.{}", export_fmt));
                let result = match export_fmt {
                    "css" => export_css(&colors, filename),
                    "json" => export_json(&colors, filename),
                    "html" => export_html(&colors, filename),
                    _ => Ok(()),
                };
                if let Err(e) = result {
                    println!("Ошибка экспорта: {}", e);
                } else {
                    println!("Экспортировано в {}", filename);
                }
            }
        }
        Err(e) => println!("Ошибка: {}", e),
    }
}
