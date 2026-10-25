"""Structural validation for recovered markdown docs."""
import glob
import os
import re
import sys

SPLIT = re.compile(r'(?<!\\)\|')
EMOJI = re.compile('[\U0001F300-\U0001FAFF\u2600-\u27BF\u2300-\u23FF\uFE0F\u2B00-\u2BFF]')
CJK = re.compile('[\u3000-\u30FF\u3400-\u4DBF\u4E00-\u9FFF\uF900-\uFAFF\uFF00-\uFFEF]')


def cells(line):
    s = line.strip()
    if s.startswith('|'):
        s = s[1:]
    if s.endswith('|'):
        s = s[:-1]
    return SPLIT.split(s)


def check(path):
    name = os.path.basename(path)
    text = open(path, encoding='utf-8').read()
    lines = text.split('\n')
    issues = []

    fence = False
    hdr = None
    start = 0
    for i, l in enumerate(lines, 1):
        if l.lstrip().startswith('```'):
            fence = not fence
            continue
        if fence:
            continue
        # merged heading detection
        if l.startswith('#'):
            if len(l) > 110:
                issues.append((i, 'LONG-HEADING', l[:90]))
            if re.search(r'\s#{1,6}\s', l):
                issues.append((i, 'HEADING-MERGED', l[:90]))
        # table column count
        if l.strip().startswith('|'):
            n = len(cells(l))
            if hdr is None:
                hdr = n
                start = i
            elif n != hdr:
                issues.append((i, 'TABLE-CELLS %d!=%d (@%d)' % (n, hdr, start), l[:70]))
        else:
            hdr = None
        # setext risk
        if i < len(lines) and lines[i].strip() in ('---', '==='):
            if l.strip() and not l.lstrip().startswith(('#', '|', '-', '*', '>')):
                issues.append((i, 'SETEXT-RISK', l[:70]))

    for m in EMOJI.finditer(text):
        issues.append((text[:m.start()].count('\n') + 1, 'EMOJI', repr(m.group(0))))
    for m in CJK.finditer(text):
        issues.append((text[:m.start()].count('\n') + 1, 'CJK', repr(m.group(0))))

    if 'Chú giải ký hiệu dùng trong các bảng' not in text:
        issues.append((0, 'NO-LEGEND', ''))
    if '`—` = không áp dụng.\n---\n' in text:
        issues.append((0, 'LEGEND-SETEXT', ''))

    print('%-34s lines=%-5d issues=%d' % (name, len(lines), len(issues)))
    for it in issues[:25]:
        print('    line %-5d %-28s %s' % it)
    return len(issues)


def main():
    args = sys.argv[1:]
    targets = args or sorted(glob.glob(r'D:/task/_recovery/*.md'))
    total = sum(check(t) for t in targets)
    print('\nTOTAL ISSUES:', total)


if __name__ == '__main__':
    main()
