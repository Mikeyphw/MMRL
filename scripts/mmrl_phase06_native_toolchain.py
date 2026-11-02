#!/usr/bin/env python3
"""Fail-closed, read-only check of the exact Phase06 hosted Android native toolchain."""
from __future__ import annotations

import argparse
from pathlib import Path

NDK = '29.0.14206865'
CMAKE = '3.22.1'


def package_revision(folder: Path) -> str:
    file = folder / 'source.properties'
    if not folder.is_dir() or file.is_symlink() or not file.is_file():
        raise RuntimeError(f'Missing SDK package or source.properties: {folder}')
    if file.stat().st_size > 64_000:
        raise RuntimeError(f'Unexpected SDK source.properties size: {folder}')
    values = {}
    for line in file.read_text(encoding='utf-8').splitlines():
        if '=' not in line or line.lstrip().startswith('#'):
            continue
        key, value = (p.strip() for p in line.split('=', 1))
        if key in values:
            raise RuntimeError(f'Duplicate SDK source.properties key: {key}')
        values[key] = value
    return values.get('Pkg.Revision', '')


def verify(sdk_root: Path) -> None:
    sdk = sdk_root.resolve(strict=True)
    for kind, version in (('ndk', NDK), ('cmake', CMAKE)):
        folder = sdk / kind / version
        actual = package_revision(folder)
        if actual != version:
            raise RuntimeError(f'{kind} revision mismatch: expected {version}, got {actual!r}')
    ndk_binary = sdk / 'ndk' / NDK / 'ndk-build'
    cmake_binary = sdk / 'cmake' / CMAKE / 'bin' / 'cmake'
    for binary in (ndk_binary, cmake_binary):
        if not binary.is_file():
            raise RuntimeError(f'Incomplete native toolchain, missing binary: {binary}')
    print(f'PASS_PHASE06_NATIVE_TOOLCHAIN ndk={NDK} cmake={CMAKE}')


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk-root', type=Path, required=True)
    args = parser.parse_args()
    try:
        verify(args.sdk_root)
    except (OSError, RuntimeError, UnicodeError) as exc:
        parser.exit(2, f'PHASE06_NATIVE_TOOLCHAIN_REFUSED: {exc}\n')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
