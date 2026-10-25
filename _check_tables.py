import glob, re, sys, os

SPLIT = re.compile(r'(?<!\\)\|')


def cells(line):
    s = line.strip()
    if s.startswith('|'):
        s = s[1:]
    if s.endswith('|'):
        s = s[:-1]
    return SPLIT.split(s)


def check(path):
    lines = open(path, encoding='utf-8').read().split('\n')
    fence = False
    hdr = None
    start = 0
    bad = 0
    for i, l in enumerate(lines, 1):
        if l.lstrip().startswith('```'):
            fence = not fence
            continue
        if fence:
            continue
        if l.strip().startswith('|'):
            n = len(cells(l))
            if hdr is None:
                hdr = n
                start = i
            elif n != hdr:
                bad += 1
                print('%s:%d: cells=%d expected=%d (table@%d) :: %s'
                      % (os.path.basename(path), i, n, hdr, start, l[:80]))
        else:
            hdr = None
    return bad


def main():
    base = r'D:/task/docs'
    targets = sys.argv[1:] or sorted(glob.glob(os.path.join(base, '*.md')))
    total = 0
    for f in targets:
        total += check(f)
    print('mismatched rows:', total)


if __name__ == '__main__':
    main()
