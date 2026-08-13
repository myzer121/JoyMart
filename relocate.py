#!/usr/bin/env python3
"""
Relocate package paths in .class files by doing binary string replacement
on the constant pool UTF-8 entries. This emulates what maven-shade-plugin's
 relocation does, but without needing a full bytecode library.

Works because Java class files store all class/package references as
 "L<internal-name>;" and raw "<internal-name>" UTF-8 strings in the
 constant pool — replacing the bytes there is sufficient for the JVM to
 load the renamed class and for getPackage().getName() to return the new
 package.
"""
import os
import sys
import struct
import shutil

# (old_internal, new_internal) — internal names use '/' as separator
RELOCATIONS = [
    (b"org/bstats",                      b"me/nikl/gamebox/common/bstats"),
    (b"com/zaxxer/hikari",              b"me/nikl/gamebox/common/hikari"),
    (b"net/jodah/expiringmap",          b"me/nikl/gamebox/common/expiringmap"),
    (b"org/slf4j",                       b"me/nikl/gamebox/common/slf4j"),
    (b"javax/annotation",                b"me/nikl/gamebox/common/jsr305"),
]

# Only relocate these source dirs (dependency classes), NOT our own
# me/nikl/gamebox classes (which already reference original names and will
# be recompiled to reference relocated names via the source-level import
# rewrite... but we reverted that, so we must ALSO relocate references
# inside our own classes).
#
# Actually: since we compile against original jars, our classes reference
# org/bstats etc. So we must relocate ALL classes — ours and deps both.

def relocate_class(data: bytes) -> bytes:
    """Replace all relocated package paths in a class file's bytes."""
    for old, new in RELOCATIONS:
        data = data.replace(old, new)
    return data

def relocate_dir(src_dir: str, dst_dir: str):
    """Walk src_dir, relocate every .class file, write to dst_dir under
    the NEW package path. Non-class files are copied as-is."""
    for root, dirs, files in os.walk(src_dir):
        for fn in files:
            src_path = os.path.join(root, fn)
            rel = os.path.relpath(src_path, src_dir)
            # Compute the new relative path by applying relocations
            new_rel = rel
            for old, new in RELOCATIONS:
                old_path = old.replace(b"/", b"/")  # internal name uses /
                # On disk, paths use os.sep
                old_disk = old.decode().replace("/", os.sep)
                new_disk = new.decode().replace("/", os.sep)
                new_rel = new_rel.replace(old_disk, new_disk)
            dst_path = os.path.join(dst_dir, new_rel)
            os.makedirs(os.path.dirname(dst_path), exist_ok=True)
            if fn.endswith(".class"):
                with open(src_path, "rb") as f:
                    data = f.read()
                data = relocate_class(data)
                with open(dst_path, "wb") as f:
                    f.write(data)
            else:
                shutil.copy2(src_path, dst_path)

if __name__ == "__main__":
    # Usage: relocate.py <src_dir> <dst_dir>
    src = sys.argv[1]
    dst = sys.argv[2]
    if os.path.exists(dst):
        shutil.rmtree(dst)
    os.makedirs(dst, exist_ok=True)
    relocate_dir(src, dst)
    print(f"Relocated classes from {src} -> {dst}")
