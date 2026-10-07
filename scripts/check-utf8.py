"""检查项目文本文件的 UTF-8 编码，跳过依赖、构建产物和私有运行目录。"""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
EXCLUDED = {".git", ".runtime", "node_modules", "target", "dist", "__pycache__"}
EXTENSIONS = {".java", ".ts", ".vue", ".css", ".html", ".json", ".md", ".yaml", ".yml", ".xml", ".sql", ".py", ".ps1", ".sh", ".properties", ".toml"}

def main():
    checked = 0
    errors = []
    for path in ROOT.rglob("*"):
        if not path.is_file() or EXCLUDED.intersection(path.relative_to(ROOT).parts):
            continue
        if path.suffix not in EXTENSIONS:
            continue
        checked += 1
        try:
            path.read_bytes().decode("utf-8")
        except UnicodeDecodeError:
            errors.append(str(path.relative_to(ROOT)))
    if errors:
        print("非 UTF-8 文件：\n" + "\n".join(errors))
        return 1
    print(f"UTF-8 检查通过：{checked} 个文本文件")
    return 0

if __name__ == "__main__":
    sys.exit(main())
