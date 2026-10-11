"""Build the offline manual and documentation ZIP with Python's standard library."""
from pathlib import Path
from html import escape
from html.parser import HTMLParser
from urllib.parse import urlsplit, unquote
import hashlib
import json
import re
import zipfile

ROOT = Path(__file__).resolve().parent.parent
DOCS = ROOT / 'docs'
CHAPTERS = [
    ('inicio', ROOT / 'LEEME_PRIMERO.md', 'Empezar aquí'),
    ('hosting', DOCS / 'GUIA_HOSTING_WINDOWS.md', 'Alojar en Windows'),
    ('cliente', DOCS / 'GUIA_CLIENTE.md', 'Conectar un cliente'),
    ('operacion', DOCS / 'OPERACION_Y_DIAGNOSTICO.md', 'Operación y diagnóstico'),
    ('entrega', DOCS / 'PREPARAR_ENTREGA.md', 'Preparar la entrega'),
    ('referencia', DOCS / 'REFERENCIA_TECNICA.md', 'Referencia técnica'),
    ('combate', DOCS / 'COMBATE_VANILLA.md', 'Combate vanilla'),
    ('mensaje', DOCS / 'MENSAJE_PARA_TU_AMIGO.txt', 'Mensaje para tu amigo'),
    ('indice', DOCS / 'README.md', 'Documentos y comprobaciones'),
]
PATH_TO_ID = {path.resolve(): chapter for chapter, path, _ in CHAPTERS}
MANUAL = DOCS / 'MANUAL_COMPLETO.html'


def local_target(href, source):
    parsed = urlsplit(href)
    if parsed.scheme or parsed.netloc or href.startswith('#'):
        return href
    target = (source.parent / unquote(parsed.path)).resolve()
    if target in PATH_TO_ID:
        return '#' + PATH_TO_ID[target]
    if target == MANUAL.resolve():
        return '#inicio'
    return href


def inline(value, source):
    pattern = r'(`[^`]+`|\[[^\]]+\]\([^\s)]+\)|\*\*[^*]+\*\*)'
    pieces = re.split(pattern, value)
    result = []
    for piece in pieces:
        if piece.startswith('`') and piece.endswith('`'):
            result.append('<code>' + escape(piece[1:-1]) + '</code>')
        elif piece.startswith('**') and piece.endswith('**'):
            result.append('<strong>' + inline(piece[2:-2], source) + '</strong>')
        elif piece.startswith('[') and (match := re.fullmatch(r'\[([^\]]+)\]\(([^\s)]+)\)', piece)):
            label, href = match.groups()
            href = local_target(href, source)
            result.append('<a href="' + escape(href, quote=True) + '">' + escape(label) + '</a>')
        else:
            result.append(escape(piece))
    return ''.join(result)


def markdown(source, chapter):
    lines = source.read_text(encoding='utf-8-sig').splitlines()
    output = []
    index = 0
    heading_index = 0
    while index < len(lines):
        line = lines[index]
        if not line.strip():
            index += 1
            continue
        if line.startswith('```'):
            language = line[3:].strip()
            index += 1
            code = []
            while index < len(lines) and not lines[index].startswith('```'):
                code.append(lines[index])
                index += 1
            if index == len(lines):
                raise ValueError(f'Unclosed code fence: {source}')
            output.append('<div class="code-block"><span class="code-label">' +
                          escape(language or 'texto') + '</span><pre><code>' +
                          escape('\n'.join(code)) + '</code></pre></div>')
            index += 1
            continue
        if match := re.match(r'^(#{1,6})\s+(.+)$', line):
            level = min(len(match[1]) + 1, 6)
            heading_index += 1
            identifier = f'{chapter}-seccion-{heading_index}'
            output.append(f'<h{level} id="{identifier}">' + inline(match[2], source) + f'</h{level}>')
            index += 1
            continue
        if line.startswith('|') and index + 1 < len(lines) and re.match(r'^\|[\s:|\-]+\|\s*$', lines[index + 1]):
            rows = [line]
            index += 2
            while index < len(lines) and lines[index].startswith('|'):
                rows.append(lines[index])
                index += 1
            rendered_rows = []
            for row_index, row in enumerate(rows):
                tag = 'th' if row_index == 0 else 'td'
                cells = row.strip().strip('|').split('|')
                rendered_rows.append('<tr>' + ''.join(
                    f'<{tag}>' + inline(cell.strip(), source) + f'</{tag}>' for cell in cells) + '</tr>')
            output.append('<div class="table-wrap"><table><thead>' + rendered_rows[0] +
                          '</thead><tbody>' + ''.join(rendered_rows[1:]) + '</tbody></table></div>')
            continue
        if match := re.match(r'^(?:([-*]) |(\d+)\. )(.+)$', line):
            ordered = match[2] is not None
            tag = 'ol' if ordered else 'ul'
            start = f' start="{match[2]}"' if ordered else ''
            items = []
            while index < len(lines):
                item = re.match(r'^(?:([-*]) |(\d+)\. )(.+)$', lines[index])
                if not item or (item[2] is not None) != ordered:
                    break
                items.append('<li>' + inline(item[3], source) + '</li>')
                index += 1
            output.append(f'<{tag}{start}>' + ''.join(items) + f'</{tag}>')
            continue
        paragraph = [line]
        index += 1
        while index < len(lines) and lines[index].strip() and not re.match(r'^(#|```|\||[-*] |\d+\. )', lines[index]):
            paragraph.append(lines[index])
            index += 1
        output.append('<p>' + inline(' '.join(paragraph), source) + '</p>')
    return '\n'.join(output)


STYLE = '''
:root{color-scheme:light;--ink:#172b3b;--muted:#526575;--blue:#12567b;--line:#dce4e9}
*{box-sizing:border-box}html{scroll-behavior:smooth;scroll-padding-top:22px}
body{margin:0;background:#f2f5f7;color:var(--ink);font:16px/1.65 "Segoe UI",Arial,sans-serif}
a{color:var(--blue);text-underline-offset:3px}a:hover{color:#0b354d}
.cover{background:#173e53;color:#fff;padding:42px max(24px,calc((100vw - 1150px)/2)) 38px}
.cover .eyebrow{text-transform:uppercase;letter-spacing:2px;font-size:12px;color:#b7d8e7}
h1{font-size:38px;line-height:1.15;margin:12px 0 16px;font-weight:650}
.cover p{max-width:780px;margin:10px 0;color:#e0edf3}.tags{display:flex;gap:8px;flex-wrap:wrap;margin:20px 0 0}
.tags span{font-size:13px;border:1px solid #55778a;border-radius:5px;padding:4px 10px}
.layout{max-width:1200px;margin:0 auto;display:grid;grid-template-columns:230px minmax(0,1fr);gap:26px;padding:28px 24px 60px}
nav{position:sticky;top:22px;align-self:start;padding:18px 14px;background:#fff;border:1px solid var(--line);border-radius:8px}
nav strong{display:block;font-size:13px;color:var(--muted);margin:0 8px 10px;text-transform:uppercase;letter-spacing:1px}
nav a{display:block;text-decoration:none;padding:7px 8px;border-radius:4px;font-size:14px;line-height:1.4}nav a:hover{background:#eaf2f7}
main{min-width:0}article{background:#fff;border:1px solid var(--line);border-radius:8px;padding:30px;margin-bottom:24px;overflow-wrap:anywhere}
.chapter-label{font-size:12px;text-transform:uppercase;letter-spacing:1px;color:var(--muted);margin:0 0 12px}
h2{font-size:27px;line-height:1.25;margin:0 0 22px;color:#173e53}h3{font-size:21px;line-height:1.35;margin:32px 0 12px}h4{font-size:18px;margin:26px 0 10px}
p{margin:13px 0}li{margin:6px 0}ul,ol{padding-left:25px}
code{font:13px/1.55 Consolas,"Cascadia Mono",monospace;background:#edf2f5;border-radius:3px;padding:2px 4px}
.code-block{border:1px solid var(--line);border-radius:6px;overflow:hidden;margin:18px 0;background:#f7f9fa}
.code-label{display:block;color:var(--muted);font-size:11px;letter-spacing:.5px;text-transform:uppercase;padding:6px 14px;background:#eaf0f4}
pre{margin:0;padding:13px 14px;overflow-x:auto;white-space:pre;tab-size:4}pre code{background:none;padding:0}
.table-wrap{overflow-x:auto;margin:18px 0}table{width:100%;border-collapse:collapse;font-size:14px;line-height:1.5}
th,td{text-align:left;vertical-align:top;border:1px solid var(--line);padding:10px 12px}th{background:#eaf2f7;color:#173e53}tr:nth-child(even) td{background:#fafcfd}
.message{white-space:pre-wrap;overflow-wrap:anywhere;font:15px/1.65 "Segoe UI",Arial,sans-serif}
footer{font-size:13px;color:var(--muted);padding:0 8px}footer a{margin-right:15px}
@media(max-width:850px){.layout{display:block;padding:20px 14px}nav{position:static;margin-bottom:20px;display:flex;flex-wrap:wrap;gap:2px}nav strong{width:100%}nav a{padding:7px 8px}article{padding:23px}h1{font-size:30px}.cover{padding:28px 24px}}
@media print{@page{size:A4;margin:17mm}body{background:white;font-size:10.5pt;color:#111}.cover{background:white;color:#111;padding:0 0 20px;border-bottom:2px solid #173e53}.cover p,.cover .eyebrow{color:#333}h1{font-size:28pt}.tags span{border-color:#aaa}.layout{display:block;padding:20px 0 0}nav{position:static;border:0;padding:0 0 20px;columns:2;display:block}nav a{font-size:10pt;padding:4px 0}nav strong{margin:0 0 8px}article{border:0;border-radius:0;padding:0;margin:0;break-before:page}h2{font-size:19pt}h3{font-size:14pt}h4{font-size:12pt}h2,h3,h4{break-after:avoid}p,li{orphans:3;widows:3}table{font-size:9pt;table-layout:fixed}th,td{padding:7px;overflow-wrap:anywhere}thead{display:table-header-group}tr{break-inside:avoid}.table-wrap{overflow:visible}pre{white-space:pre-wrap;overflow:visible;overflow-wrap:anywhere}code{font-size:9pt}.code-block{break-inside:avoid}a{color:#173e53;text-decoration:underline}footer{margin-top:20px}}
'''


def build():
    sections = []
    for number, (chapter, source, label) in enumerate(CHAPTERS, 1):
        if source.suffix == '.txt':
            body = '<h2>' + escape(label) + '</h2><pre class="message">' + escape(source.read_text(encoding='utf-8-sig')) + '</pre>'
        else:
            body = markdown(source, chapter)
        sections.append(f'<article id="{chapter}"><div class="chapter-label">Documento {number:02d}</div>' + body + '</article>')
    navigation = ''.join(f'<a href="#{chapter}">{escape(label)}</a>' for chapter, _, label in CHAPTERS)
    document = '''<!doctype html>
<html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="description" content="Manual de instalación, alojamiento y diagnóstico de Spiral Knights Private para Windows x64.">
<title>Spiral Knights Private · Manual para Windows</title><style>''' + STYLE + '''</style></head><body>
<header class="cover"><div class="eyebrow">Spiral Knights Private</div><h1>Manual para alojar y jugar</h1>
<p>Instalación, invitados por LAN o VPN, operación y diagnóstico de la copia independiente.</p>
<div class="tags"><span>Windows x64</span><span>Datos 20261006114613</span><span>Revisión 11/10/2026</span><span>Lectura sin conexión</span></div>
<p>La documentación acompaña la copia completa del juego. Abrí este archivo en el navegador; Ctrl+P permite imprimirlo o guardarlo como PDF.</p></header>
<div class="layout"><nav aria-label="Índice del manual"><strong>Contenido</strong>''' + navigation + '''</nav><main>''' + '\n'.join(sections) + '''
<footer><a href="#inicio">Volver al comienzo</a><a href="verificar-copia.ps1">Verificador de archivos</a>Fuentes editables en esta carpeta docs.</footer>
</main></div></body></html>'''
    MANUAL.write_text(document, encoding='utf-8', newline='\n')


class CheckHTML(HTMLParser):
    def __init__(self):
        super().__init__()
        self.ids, self.links, self.tables, self.pre = set(), [], 0, 0
    def handle_starttag(self, tag, attrs):
        attributes = dict(attrs)
        if 'id' in attributes:
            assert attributes['id'] not in self.ids, 'Duplicate ID'
            self.ids.add(attributes['id'])
        if tag == 'a':
            self.links.append(attributes['href'])
        assert tag not in ('script', 'iframe'), 'Offline manual must not execute scripts or embed pages'
        if tag == 'table': self.tables += 1
        if tag == 'pre': self.pre += 1


def verify_and_package():
    parser = CheckHTML()
    parser.feed(MANUAL.read_text(encoding='utf-8'))
    for href in parser.links:
        if href.startswith('#'):
            assert href[1:] in parser.ids, f'Broken anchor: {href}'
        elif not urlsplit(href).scheme:
            assert (DOCS / href).is_file(), f'Broken file link: {href}'
    expected_tables = sum(len(re.findall(r'^\|[\s:|\-]+\|\s*$', path.read_text(encoding='utf-8-sig'), re.M))
                          for _, path, _ in CHAPTERS if path.suffix == '.md')
    expected_pre = sum(path.read_text(encoding='utf-8-sig').count('```') // 2
                       for _, path, _ in CHAPTERS if path.suffix == '.md') + 1
    assert parser.tables == expected_tables and parser.pre == expected_pre, 'Missing rendered content'
    for _, source, _ in CHAPTERS:
        if source.suffix != '.md': continue
        for href in re.findall(r'\[[^\]]+\]\(([^\s)]+)\)', source.read_text(encoding='utf-8-sig')):
            if not urlsplit(href).scheme and not href.startswith('#'):
                assert (source.parent / unquote(href)).is_file(), f'Broken Markdown link: {source}: {href}'
    exports = ROOT / 'exports'
    exports.mkdir(exist_ok=True)
    package = exports / 'Documentacion-SpiralKnightsPrivate-20261006114613.zip'
    files = [ROOT / 'LEEME_PRIMERO.md'] + sorted(path for path in DOCS.iterdir() if path.is_file())
    with zipfile.ZipFile(package, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in files:
            archive.write(path, path.relative_to(ROOT).as_posix())
    with zipfile.ZipFile(package) as archive:
        assert archive.testzip() is None, 'ZIP CRC error'
        names = archive.namelist()
        assert 'docs/MANUAL_COMPLETO.html' in names and 'docs/verificar-copia.ps1' in names
        assert not any(name.startswith(('code/', 'profile/', 'jdk/', 'java_vm/')) for name in names)
    report = {
        'manual': str(MANUAL), 'chapters': len(CHAPTERS), 'tables': parser.tables,
        'code_blocks': parser.pre, 'anchors': len(parser.ids), 'links_checked': len(parser.links),
        'manual_bytes': MANUAL.stat().st_size, 'zip': str(package),
        'zip_bytes': package.stat().st_size, 'zip_files': names,
        'zip_sha256': hashlib.sha256(package.read_bytes()).hexdigest(),
    }
    (ROOT / 'validation' / 'hosting-documentation-checks.json').write_text(
        json.dumps(report, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    print(json.dumps(report, ensure_ascii=False))


if __name__ == '__main__':
    build()
    verify_and_package()
