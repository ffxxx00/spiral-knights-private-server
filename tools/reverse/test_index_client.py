"""Verify the index reader against bundled javac/javap, not handwritten bytecode."""
import collections
from pathlib import Path
import re
import subprocess
import tempfile
from index_client import ClassFile, FIELDS, INVOKES, find_source

root = Path(__file__).resolve().parents[2]
workspace = root / "analysis" / "index-reader-test"
workspace.mkdir(parents=True, exist_ok=True)
with tempfile.TemporaryDirectory(dir=workspace) as folder:
    directory = Path(folder)
    variables = "\n".join(f"int v{i}=n+{i};" for i in range(270))
    source = r'''
interface Target { void hit(); }
public class Fixture {
    static String text="a\0b😺";
    static int counter;
    public void run(Target target) { target.hit(); counter++; helper(); Runnable r=Fixture::helper; r.run(); }
    static int helper() { return counter; }
    static int table(int n) { int value; switch(n) { case 1:value=2;break;case 2:value=3;break;case 3:value=4;break;default:value=0; } return value+helper(); }
    static int sparse(int n) { int value; switch(n) { case 1:value=2;break;case 100:value=3;break;case 999:value=4;break;default:value=0; } return value+helper(); }
    static int wide(int n) { VARIABLES v269++; return v269+helper(); }
}
'''.replace("VARIABLES", variables)
    (directory / "Fixture.java").write_text(source, encoding="utf-8")
    subprocess.run([str(root / "jdk/bin/javac.exe"), "--release", "17", "-encoding", "UTF-8", str(directory / "Fixture.java")], check=True, capture_output=True)
    cls = ClassFile((directory / "Fixture.class").read_bytes())
    result = subprocess.run([str(root / "jdk/bin/javap.exe"), "-c", "-p", "-classpath", str(directory), "Fixture"], check=True, capture_output=True, text=True).stdout
    kinds = dict(FIELDS, **{})
    kinds.update(INVOKES)
    kinds[186] = "invokedynamic"
    actual = collections.defaultdict(list)
    all_opcodes = set()
    for _, name, _, attrs in cls.methods:
        if "Code" not in attrs: continue
        for offset, opcode, _ in cls.instructions(attrs["Code"]):
            all_opcodes.add(opcode)
            if opcode in kinds: actual[name].append((offset, kinds[opcode]))
    expected = collections.defaultdict(list)
    name = None
    for line in result.splitlines():
        header = re.match(r"\s+(?:.*\s)?([\w.$<>]+)\([^)]*\);$", line)
        if header: name = "<init>" if header[1] == "Fixture" else header[1]
        if line.strip() == "static {};": name = "<clinit>"
        opcode = re.match(r"\s*(\d+): (invoke\w+|getstatic|putstatic|getfield|putfield)\b", line)
        if opcode: expected[name].append((int(opcode[1]), opcode[2]))
    assert dict(actual) == dict(expected), (actual, expected)
    assert {170, 171, 196, 186}.issubset(all_opcodes), "Fixture must exercise table/lookup switches, wide locals and invokedynamic"
    assert any(tag == 8 and cls.utf(value) == "a\0b😺" for tag, value in filter(None, cls.cp)), "Modified UTF-8 decode lost NUL/supplementary character"
    handles = [target for _, _, _, attrs in cls.methods if "Code" in attrs
               for _, opcode, index in cls.instructions(attrs["Code"]) if opcode == 186
               for target in cls.bootstrap_handles(index)]
    assert ("Fixture", "helper", "()I") in handles, "Lambda method handle missing"
    assert cls.name == "Fixture" and cls.interfaces == []
    generated = directory / 'sources' / 'example'
    generated.mkdir(parents=True)
    for filename in ('$Legal.java', 'Outer.java', 'Outer$1.java'):
        (generated / filename).write_text('// test navigation\n')
    assert find_source(directory, directory, 'example.$Legal') == 'sources/example/$Legal.java'
    assert find_source(directory, directory, 'example.Outer$1') == 'sources/example/Outer$1.java'
    assert find_source(directory, directory, 'example.Outer$Inner') == 'sources/example/Outer.java'
    print("INDEX READER JAVAC/JAVAP PARITY TEST PASSED")
