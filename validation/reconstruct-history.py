"""Reconstruct source history from local backups without replacing working files.

One-time import: refuses an existing Git repository. No backdated metadata.
Archives stay local; only hashes and selected source entries enter Git.
"""
from pathlib import Path
import hashlib
import json
import os
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
GIT = 'git'

def run(*args, data=None, env=None):
    return subprocess.check_output([GIT, *args], cwd=ROOT, input=data, env=env).decode().strip()

def digest(data):
    return hashlib.sha256(data).hexdigest()

def archive(name, mapping):
    path = ROOT / 'validation' / name
    files = {}
    entries = []
    with zipfile.ZipFile(path) as z:
        for info in z.infolist():
            target = mapping(info.filename)
            if target:
                data = z.read(info)
                files[target] = data
                entries.append({'archivePath': info.filename, 'repositoryPath': target,
                                'sha256': digest(data), 'zipTimestamp': list(info.date_time)})
    evidence.append({'archive': name, 'sha256': digest(path.read_bytes()), 'entries': entries})
    return files

def current(paths):
    return {p: (ROOT / p).read_bytes() for p in paths}

def commit(files, title, description):
    global parent
    for path, data in sorted(files.items()):
        blob = run('hash-object', '-w', '--stdin', data=data)
        run('update-index', '--add', '--cacheinfo', '100644', blob, path, env=index_env)
        tracked[path] = data
    tree = run('write-tree', env=index_env)
    args = ['commit-tree', tree]
    if parent:
        args += ['-p', parent]
    message = title + '\n\nHistorial reconstruido; commit creado durante la importacion.\n' + description + '\n'
    parent = run(*args, data=message.encode('utf-8'))
    commits.append({'sha': parent, 'title': title, 'evidence': description})
    print(parent[:12], title, flush=True)

if (ROOT / '.git').exists():
    raise SystemExit('Refusing to replace an existing Git history.')

evidence, commits, tracked = [], [], {}
parent = None
run('init', '-b', 'codex/reconstructed-history')
run('config', 'core.autocrlf', 'false')
index_env = dict(os.environ, GIT_INDEX_FILE=str(ROOT / '.git' / 'reconstruction-index'))
run('read-tree', '--empty', env=index_env)

def server_path(name):
    return name if name.startswith('server/') and name.endswith('.java') else None

commit(archive('server-before-adaptation.zip', server_path),
       'reconstruct: importar servidor anterior a la adaptacion',
       'Fuentes exactas de server-before-adaptation.zip. Es la primera copia disponible,\n'
       'no prueba de que sea la primera version creada. Puede no compilar con el cliente congelado.')

def adapted_path(name):
    if name.endswith('.java') or name == 'server.properties':
        return 'server/' + name
    if name in ('launch_client.ps1', 'test_server.bat'):
        return name

commit(archive('before-low-latency.zip', adapted_path),
       'reconstruct: adaptar protocolo al cliente congelado',
       'Entradas exactas de before-low-latency.zip. Estado previo a la baja latencia.\n'
       'Este respaldo no contiene todos los archivos necesarios para ejecutar el cliente.')

def render_path(name):
    return {'ClientSession.java': 'server/ClientSession.java',
            'BuildLocalClient.java': 'portable/BuildLocalClient.java',
            'launch_client.ps1': 'launch_client.ps1'}.get(name)

commit(archive('before-render-optimization.zip', render_path),
       'reconstruct: recuperar primer ajuste de latencia local',
       'Tres archivos exactos de before-render-optimization.zip; el resto conserva el\n'
       'respaldo anterior. Es una reconstruccion parcial, no un snapshot integral probado.')

commit(archive('before-authoritative-combat.zip',
               lambda n: n if n.startswith('server/') and n.endswith(('.java', '.md', '.properties')) else None),
       'reconstruct: recuperar servidor previo al combate autoritativo',
       'Fuentes y configuracion exactas de before-authoritative-combat.zip. Agrupa\n'
       'ajustes posteriores de red y controles sin inventar snapshots intermedios.')

portable_paths = [p.relative_to(ROOT).as_posix() for p in (ROOT / 'portable').glob('*.java')]
portable_paths += [p.relative_to(ROOT).as_posix() for p in (ROOT / 'client-local' / 'src').rglob('*.java')]
portable_paths += ['client-local/README.md', 'launch_client.bat', 'launch_client.ps1',
                   'run_server.bat', 'run_server.ps1', 'start_server.bat',
                   'frozen-version.json', 'full.dat', 'getdown.txt', 'version.txt',
                   'server/local-config/deployment.properties']
commit(current(portable_paths),
       'reconstruct: consolidar copia portable y cliente local optimizado',
       'Archivos actuales: aislamiento de preferencias, lanzadores, congelacion,\n'
       'overlay local, renderizado, detencion del movimiento y backend FFM.\n'
       'Sin respaldos completos para separar con exactitud cada correccion del cliente.')

combat_paths = [p.relative_to(ROOT).as_posix() for p in (ROOT / 'server').glob('*.java')]
combat_paths += ['server/server.properties', 'test_server.bat']
commit(current(combat_paths),
       'reconstruct: implementar combate autoritativo y regresiones',
       'Diferencia entre el respaldo previo al combate y las fuentes actuales.\n'
       'Incluye controlador vanilla, cubos, dano, proyectiles, bombas y pruebas.\n'
       'Paridad incompleta y comprobacion visual pendiente; ver COMBATE_VANILLA.md.')

docs_paths = [p.relative_to(ROOT).as_posix() for p in (ROOT / 'docs').rglob('*')
              if p.is_file() and p.suffix in ('.md', '.html', '.ps1')]
docs_paths += ['README.md', 'LEEME_PRIMERO.md', 'AGENTS.md', 'allow_lan_firewall.ps1',
               'server/README_LAN.md', 'validation/build-hosting-docs.py']
docs_paths += [p.relative_to(ROOT).as_posix() for p in (ROOT / 'validation').glob('*notes.md')]
docs_paths += [p.relative_to(ROOT).as_posix() for p in (ROOT / 'validation').glob('*.java')]
docs_paths += [p.relative_to(ROOT).as_posix() for p in (ROOT / 'validation').glob('*.ps1')]
commit(current(docs_paths),
       'reconstruct: documentar hosting Windows y evidencia de validacion',
       'Documentacion y herramientas actuales. Las notas describen pruebas historicas;\n'
       'los logs, dumps, recursos, perfiles y binarios referenciados no se distribuyen.')

manifest = {'method': 'Exact archived entries followed by grouped current sources; no backdating.',
            'archives': evidence, 'milestoneCommits': commits,
            'finalSourceFiles': {p: digest(data) for p, data in sorted(tracked.items())}}
(ROOT / 'docs' / 'HISTORY_EVIDENCE.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

# A deny-by-default policy keeps the frozen game and local data out of future commits.
ignored = ['*', '!.gitignore', '!.gitattributes', '!AGENTS.md', '!README.md',
           '!LEEME_PRIMERO.md', '!README_GITHUB.md', '!HISTORY_RECONSTRUCTION.md',
           '!*.bat', '!*.ps1', '!frozen-version.json', '!full.dat', '!getdown.txt', '!version.txt',
           '!server/', '!server/*.java', '!server/*.md', '!server/server.properties',
           '!server/local-config/', '!server/local-config/deployment.properties',
           '!portable/', '!portable/*.java', '!client-local/', '!client-local/README.md',
           '!client-local/src/', '!client-local/src/**/', '!client-local/src/**/*.java',
           '!docs/', '!docs/*.md', '!docs/*.html', '!docs/*.ps1', '!docs/HISTORY_EVIDENCE.json',
           '!validation/', '!validation/*notes.md', '!validation/*.java', '!validation/*.ps1',
           '!validation/build-hosting-docs.py', '!validation/reconstruct-history.py']
(ROOT / '.gitignore').write_text('\n'.join(ignored) + '\n', encoding='utf-8')
(ROOT / '.gitattributes').write_text('* -text\n', encoding='utf-8')
history = '''# Historial reconstruido del servidor privado

No existia un repositorio Git en esta carpeta. Estos commits fueron creados al
importar respaldos el 11 de octubre de 2026, con la identidad Git configurada.
No son commits originales recuperados ni tienen fechas de autoria retroactivas.
La primera copia disponible ya contiene desarrollo; no permite recuperar el
trabajo anterior a ese respaldo. Los timestamps ZIP son indicios locales,
no fechas verificadas de autoria.

Los cuatro primeros commits recuperan entradas exactas de respaldos. El tercero
solo conserva tres archivos y arrastra los restantes del estado anterior.
Los siguientes agrupan fuentes actuales por funcion, sin pretender reproducir
cada cambio intermedio ni el orden exacto de todo el desarrollo.

Los commits historicos pueden carecer de auxiliares o ser incompatibles con
la biblioteca congelada. No se declara que cada commit historico compile ni
pase las pruebas. Para trabajar, usar el ultimo commit y los recursos indicados
en README_GITHUB.md. La reconstruccion no modifico fuentes ni archivos del juego.

## Etapas y evidencia

'''
for c in commits:
    history += f"- `{c['sha'][:12]}` — {c['title']}\n\n  {c['evidence'].replace(chr(10), ' ')}\n\n"
history += '''Los SHA-256 de respaldos y entradas estan en docs/HISTORY_EVIDENCE.json.
Los ZIP originales permanecen locales en validation/ y no entran al repositorio.
El manifiesto permite comparar las fuentes actuales con el estado importado.
El ultimo commit agrega esta trazabilidad y las reglas de inclusion de archivos.

Los futuros cambios se registraran como commits normales, sin reconstruccion.
'''
(ROOT / 'HISTORY_RECONSTRUCTION.md').write_text(history, encoding='utf-8')
commit(current(['.gitignore', '.gitattributes', 'README_GITHUB.md',
                'HISTORY_RECONSTRUCTION.md', 'docs/HISTORY_EVIDENCE.json',
                'validation/reconstruct-history.py']),
       'docs: registrar trazabilidad y excluir juego y datos locales',
       'Documenta limites de la reconstruccion y requisitos del checkout de fuentes.\n'
       'Politica de inclusion explicita: no se suben JAR, assets, Java, perfiles ni logs.')

run('update-ref', 'refs/heads/codex/reconstructed-history', parent)
run('read-tree', parent)  # Populate real index without touching the working tree.
for path, data in tracked.items():
    if (ROOT / path).read_bytes() != data:
        raise SystemExit('Working source mismatch: ' + path)
run('fsck', '--full')
status = run('status', '--porcelain')
if status:
    raise SystemExit('Unexpected worktree changes: ' + status)
run('bundle', 'create', 'exports/spiral-knights-private-server-history.bundle', '--all')
print(f'Validated {len(commits)} commits, {len(tracked)} source files. Worktree clean.')
