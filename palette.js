// palette.js - Генератор цветовых палитр на JavaScript (Node.js CLI + веб)
// CLI: node palette.js --base #FF5733 --scheme analog --count 5
// Веб: откройте как HTML

// ========== ОСНОВНЫЕ ФУНКЦИИ ==========
function hexToRgb(hex) {
    const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
    return result ? {
        r: parseInt(result[1], 16),
        g: parseInt(result[2], 16),
        b: parseInt(result[3], 16)
    } : null;
}

function rgbToHex(r, g, b) {
    return '#' + [r, g, b].map(c => c.toString(16).padStart(2, '0')).join('');
}

function rgbToHsl(r, g, b) {
    r /= 255; g /= 255; b /= 255;
    const max = Math.max(r, g, b), min = Math.min(r, g, b);
    let h, s, l = (max + min) / 2;
    if (max === min) {
        h = s = 0;
    } else {
        const d = max - min;
        s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
        switch (max) {
            case r: h = ((g - b) / d + (g < b ? 6 : 0)) / 6; break;
            case g: h = ((b - r) / d + 2) / 6; break;
            case b: h = ((r - g) / d + 4) / 6; break;
        }
        h *= 360;
    }
    return { h, s: s * 100, l: l * 100 };
}

function hslToRgb(h, s, l) {
    h = h % 360; s /= 100; l /= 100;
    const c = (1 - Math.abs(2 * l - 1)) * s;
    const x = c * (1 - Math.abs((h / 60) % 2 - 1));
    const m = l - c / 2;
    let r, g, b;
    if (h < 60) { r = c; g = x; b = 0; }
    else if (h < 120) { r = x; g = c; b = 0; }
    else if (h < 180) { r = 0; g = c; b = x; }
    else if (h < 240) { r = 0; g = x; b = c; }
    else if (h < 300) { r = x; g = 0; b = c; }
    else { r = c; g = 0; b = x; }
    return { r: Math.round((r + m) * 255), g: Math.round((g + m) * 255), b: Math.round((b + m) * 255) };
}

// ========== ГЕНЕРАЦИЯ ПАЛИТР ==========
function generatePalette(baseHex, scheme, count = 5) {
    const base = hexToRgb(baseHex);
    if (!base) throw new Error('Неверный HEX');
    const hsl = rgbToHsl(base.r, base.g, base.b);
    const colors = [];
    switch (scheme) {
        case 'mono':
            for (let i = 0; i < count; i++) {
                const factor = count > 1 ? i / (count - 1) : 0.5;
                const newL = Math.max(10, Math.min(90, hsl.l * (0.5 + factor)));
                colors.push(hslToRgb(hsl.h, hsl.s, newL));
            }
            break;
        case 'comp':
            colors.push({ r: base.r, g: base.g, b: base.b });
            colors.push(hslToRgb((hsl.h + 180) % 360, hsl.s, hsl.l));
            break;
        case 'analog':
            for (let i = 0; i < count; i++) {
                const newH = (hsl.h - 30 + i * (60 / (count - 1))) % 360;
                colors.push(hslToRgb(newH, hsl.s, hsl.l));
            }
            break;
        case 'triad':
            for (let i = 0; i < 3; i++) {
                colors.push(hslToRgb((hsl.h + i * 120) % 360, hsl.s, hsl.l));
            }
            break;
        case 'tetrad':
            colors.push({ r: base.r, g: base.g, b: base.b });
            colors.push(hslToRgb((hsl.h + 180) % 360, hsl.s, hsl.l));
            colors.push(hslToRgb((hsl.h + 90) % 360, hsl.s, hsl.l));
            colors.push(hslToRgb((hsl.h + 270) % 360, hsl.s, hsl.l));
            break;
        default: throw new Error('Неизвестная схема');
    }
    return colors;
}

// ========== ВИЗУАЛИЗАЦИЯ ==========
function printPalette(colors) {
    colors.forEach((c, i) => {
        const hex = rgbToHex(c.r, c.g, c.b);
        const bg = `\x1b[48;2;${c.r};${c.g};${c.b}m`;
        const reset = '\x1b[0m';
        const lum = 0.299 * c.r + 0.587 * c.g + 0.114 * c.b;
        const fg = lum < 140 ? '\x1b[38;2;255;255;255m' : '\x1b[38;2;0;0;0m';
        console.log(`${bg}${fg}  ${i+1}. ${hex}  RGB(${c.r},${c.g},${c.b})  ${reset}`);
    });
}

// ========== ЭКСПОРТ ==========
function exportCSS(colors, filename) {
    const fs = require('fs');
    let css = ':root {\n';
    colors.forEach((c, i) => {
        css += `  --color-${i+1}: ${rgbToHex(c.r, c.g, c.b)};\n`;
    });
    css += '}\n';
    fs.writeFileSync(filename, css);
}

function exportJSON(colors, filename) {
    const fs = require('fs');
    const data = colors.map(c => ({ r: c.r, g: c.g, b: c.b, hex: rgbToHex(c.r, c.g, c.b) }));
    fs.writeFileSync(filename, JSON.stringify(data, null, 2));
}

function exportHTML(colors, filename) {
    const fs = require('fs');
    let html = '<html><head><style>body{font-family:sans-serif; display:flex; gap:20px;}</style></head><body>';
    colors.forEach(c => {
        const hex = rgbToHex(c.r, c.g, c.b);
        const lum = 0.299*c.r + 0.587*c.g + 0.114*c.b;
        const color = lum < 140 ? 'white' : 'black';
        html += `<div style="background:${hex}; width:100px; height:100px; border-radius:8px; text-align:center; line-height:100px; color:${color};">${hex}</div>`;
    });
    html += '</body></html>';
    fs.writeFileSync(filename, html);
}

function exportPNG(colors, filename) {
    // Для PNG нужна библиотека, например, 'canvas'. Для простоты пропускаем в этой версии.
    console.log('PNG экспорт требует дополнительных библиотек.');
}

// ========== ИСТОРИЯ (localStorage) ==========
function loadHistory() {
    if (typeof localStorage !== 'undefined') {
        try {
            return JSON.parse(localStorage.getItem('palette_history')) || [];
        } catch { return []; }
    }
    return [];
}

function saveHistoryEntry(entry) {
    if (typeof localStorage !== 'undefined') {
        let history = loadHistory();
        history.push(entry);
        if (history.length > 100) history = history.slice(-100);
        localStorage.setItem('palette_history', JSON.stringify(history));
    }
}

// ========== CLI (Node.js) ==========
if (typeof module !== 'undefined' && require.main === module) {
    const args = process.argv.slice(2);
    let base = null, scheme = 'analog', count = 5, exportFmt = null, output = null, history = false, random = false;
    for (let i = 0; i < args.length; i++) {
        switch (args[i]) {
            case '--base': base = args[++i]; break;
            case '--scheme': scheme = args[++i]; break;
            case '--count': count = parseInt(args[++i]); break;
            case '--export': exportFmt = args[++i]; break;
            case '--output': output = args[++i]; break;
            case '--history': history = true; break;
            case '--random': random = true; break;
        }
    }
    if (history) {
        const hist = loadHistory();
        if (hist.length === 0) console.log('История пуста.');
        else {
            console.log('\n📋 ИСТОРИЯ ПАЛИТР');
            hist.slice(-10).forEach(e => console.log(`${e.date.slice(0,19)}  ${e.scheme}  ${e.colors.length} цветов`));
        }
        process.exit(0);
    }
    if (random) {
        const r = Math.floor(Math.random() * 256);
        const g = Math.floor(Math.random() * 256);
        const b = Math.floor(Math.random() * 256);
        base = rgbToHex(r, g, b);
        console.log(`Случайный базовый цвет: ${base}`);
    } else if (!base) {
        const readline = require('readline');
        const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
        rl.question('Введите базовый цвет (HEX, например #FF5733): ', answer => {
            base = answer.trim();
            if (!base.startsWith('#')) base = '#' + base;
            run(base, scheme, count, exportFmt, output);
            rl.close();
        });
        return;
    }
    run(base, scheme, count, exportFmt, output);
}

function run(base, scheme, count, exportFmt, output) {
    try {
        const colors = generatePalette(base, scheme, count);
        console.log(`\n🎨 Палитра '${scheme}' (база ${base}):`);
        printPalette(colors);
        // Сохранить историю
        saveHistoryEntry({
            date: new Date().toISOString(),
            scheme: scheme,
            base: base,
            colors: colors.map(c => rgbToHex(c.r, c.g, c.b))
        });
        if (exportFmt) {
            const filename = output || `palette.${exportFmt}`;
            if (exportFmt === 'css') exportCSS(colors, filename);
            else if (exportFmt === 'json') exportJSON(colors, filename);
            else if (exportFmt === 'html') exportHTML(colors, filename);
            else console.log('Формат не поддерживается для экспорта');
            console.log(`Экспортировано в ${filename}`);
        }
    } catch (e) {
        console.error('Ошибка:', e.message);
    }
}

// ========== Браузерная версия ==========
if (typeof window !== 'undefined') {
    window.hexToRgb = hexToRgb;
    window.rgbToHex = rgbToHex;
    window.rgbToHsl = rgbToHsl;
    window.hslToRgb = hslToRgb;
    window.generatePalette = generatePalette;
    window.printPalette = printPalette;
    window.exportCSS = exportCSS;
    window.exportJSON = exportJSON;
    window.exportHTML = exportHTML;
    window.loadHistory = loadHistory;
    window.saveHistoryEntry = saveHistoryEntry;
    // Веб-интерфейс при загрузке
    document.addEventListener('DOMContentLoaded', function() {
        const container = document.getElementById('palette-container');
        const baseInput = document.getElementById('base-input');
        const schemeSelect = document.getElementById('scheme-select');
        const countInput = document.getElementById('count-input');
        const generateBtn = document.getElementById('generate-btn');
        const exportBtn = document.getElementById('export-btn');

        function displayPalette() {
            const base = baseInput.value.trim();
            const scheme = schemeSelect.value;
            const count = parseInt(countInput.value) || 5;
            try {
                const colors = generatePalette(base, scheme, count);
                container.innerHTML = '';
                colors.forEach(c => {
                    const hex = rgbToHex(c.r, c.g, c.b);
                    const div = document.createElement('div');
                    div.style.cssText = `background:${hex}; width:80px; height:80px; display:inline-block; margin:4px; border-radius:8px; text-align:center; line-height:80px; color:${0.299*c.r+0.587*c.g+0.114*c.b < 140 ? 'white' : 'black'}; font-size:12px;`;
                    div.textContent = hex;
                    container.appendChild(div);
                });
                // Сохранить историю
                saveHistoryEntry({
                    date: new Date().toISOString(),
                    scheme: scheme,
                    base: base,
                    colors: colors.map(c => rgbToHex(c.r, c.g, c.b))
                });
            } catch (e) {
                container.innerHTML = '<span style="color:red;">Ошибка: ' + e.message + '</span>';
            }
        }

        generateBtn.addEventListener('click', displayPalette);
        exportBtn.addEventListener('click', function() {
            const base = baseInput.value.trim();
            const scheme = schemeSelect.value;
            const count = parseInt(countInput.value) || 5;
            const colors = generatePalette(base, scheme, count);
            // Создаём HTML и скачиваем
            const html = exportHTMLToString(colors);
            const blob = new Blob([html], {type: 'text/html'});
            const a = document.createElement('a');
            a.href = URL.createObjectURL(blob);
            a.download = 'palette.html';
            a.click();
            URL.revokeObjectURL(a.href);
        });

        // Инициализация случайным цветом
        const r = Math.floor(Math.random() * 256);
        const g = Math.floor(Math.random() * 256);
        const b = Math.floor(Math.random() * 256);
        baseInput.value = rgbToHex(r, g, b);
        displayPalette();
    });
}
