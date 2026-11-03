#!/usr/bin/env python3
"""Conservative shell-source check against the pinned Android 4.4.2 profile.

This is a lexer and a command-list recognizer, not a shell interpreter. It never
executes input. Expansions remain opaque arguments; command substitutions are
checked recursively. Unknown command positions fail closed. See the audit note
for the explicit boundary between source checks and device verification.
"""
from dataclasses import dataclass
import json
from pathlib import Path
import re
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PROFILE = json.loads((ROOT / "tools/android44-shell-profile.json").read_text())
WAIVER = re.compile(r"# android44-shell: allow -- (\S.*)")
ASSIGN = re.compile(r"^[a-zA-Z_][a-zA-Z0-9_]*=")
FUNCTION = re.compile(r"(?:^|[;\n])\s*(?:function\s+)?([a-zA-Z_][a-zA-Z0-9_]*)\s*\(\s*\)")
OPERATORS = ("<<<", "<<-", "&&", "||", ";;", ";&", "|&", "<<", ">>", ">&", "<&", "<>", ">|", ";", "|", "&", "(", ")", "<", ">", "\n")
REDIRECTS = {"<<", "<<-", "<<<", ">>", ">&", "<&", "<>", ">|", "<", ">"}
SHELL_SHEBANG = re.compile(rb'^#![^\n]*(?:/|\s)(?:sh|bash|mksh|dash|ksh|zsh)(?:\s|$)')


def shell_source(name, data):
    return name.endswith('.sh') or bool(SHELL_SHEBANG.match(data))


@dataclass
class Token:
    text: str
    line: int
    literal: bool = True
    operator: bool = False
    quoted: bool = False


class Audit:
    def __init__(self, source, name="<input>", functions=()):
        self.source, self.name = source, str(name)
        self.functions = set(functions) | set(FUNCTION.findall(source))
        self.errors, self.waivers = [], []
        self.comments = {}
        self.substitutions = []

    def error(self, line, message):
        self.errors.append((line, message))

    def lex(self, source, first_line=1):
        tokens, word = [], []
        line = first_line
        word_line, literal, started, quoted = line, True, False, False
        i, quote, heredocs, pending = 0, None, [], None

        def flush():
            nonlocal word, literal, started, quoted
            if started:
                tokens.append(Token("".join(word), word_line, literal, quoted=quoted))
            word, literal, started, quoted = [], True, False, False

        def balanced(start, opening, closing):
            # Preserve parameter/arithmetic expressions as one argument.
            depth, j, q = 1, start, None
            while j < len(source):
                c = source[j]
                if c == "\\" and q != "'":
                    j += 2
                    continue
                if q:
                    if c == q:
                        q = None
                elif c in ("'", '"'):
                    q = c
                elif c == opening:
                    depth += 1
                elif c == closing:
                    depth -= 1
                    if not depth:
                        return j
                j += 1
            self.error(line, "unclosed expansion")
            return len(source) - 1

        while i < len(source):
            c = source[i]
            if not started:
                word_line = line
            if quote == "'":
                if c == "'":
                    quote = None
                else:
                    word.append(c)
                line += c == "\n"
                i += 1
                continue
            if c == "\\":
                if i + 1 < len(source):
                    following = source[i + 1]
                    if following == "\n":
                        line += 1
                    else:
                        word.append(following)
                        started = True
                    i += 2
                    continue
            if c == '`':
                self.error(line, "use $() instead of opaque backtick substitution")
            if c == '$' and i + 1 < len(source):
                if source[i + 1] == '{':
                    end = balanced(i + 2, '{', '}')
                    expression = source[i:end + 1]
                    if re.match(r"\$\{(?:![a-zA-Z_]|[^}:]+(?:\^|,|@[a-zA-Z]))", expression):
                        self.error(line, "unsupported parameter expansion")
                    # Command substitutions in default/pattern words also run.
                    if '$(' in expression:
                        self.substitutions.append((expression[2:-1], line, True))
                    word.append(expression)
                    line += expression.count('\n')
                    i = end + 1
                    literal, started = False, True
                    continue
                if source[i + 1] == '(':
                    end = balanced(i + 2, '(', ')')
                    expression = source[i:end + 1]
                    if expression.startswith('$(('):
                        # Arithmetic may itself contain command substitutions.
                        if '$(' in expression[3:]:
                            self.substitutions.append((expression[3:-2], line, True))
                    else:
                        self.substitutions.append((expression[2:-1], line, False))
                    word.append(expression)
                    line += expression.count('\n')
                    i = end + 1
                    literal, started = False, True
                    continue
                if source[i + 1] == "'" and quote is None:
                    # Data supports ANSI C quoting, but using its escapes to
                    # construct an executable/option needs explicit review.
                    literal = False
                    i += 1
                    c = source[i]
                else:
                    literal = False
            if c in ("'", '"'):
                quoted = True
                if quote == c:
                    quote = None
                elif quote is None:
                    quote = c
                else:
                    word.append(c)
                started = True
                i += 1
                continue
            if quote:
                word.append(c)
                started = True
                line += c == '\n'
                i += 1
                continue
            if c == '#' and not started:
                end = source.find('\n', i)
                if end < 0:
                    end = len(source)
                comment = source[i:end]
                if 'android44-shell:' in comment:
                    match = WAIVER.fullmatch(comment.rstrip())
                    if not match:
                        self.error(line, "waiver needs 'allow --' and a reason")
                    else:
                        self.comments[line] = match[1]
                i = end
                continue
            if c in ' \t\r':
                flush()
                i += 1
                continue
            op = next((op for op in OPERATORS if source.startswith(op, i)), None)
            # Braces are control operators only as standalone words.
            if not started and c in '{}' and (i + 1 == len(source) or source[i + 1] in ' \t\n;'):
                op = c
            if op:
                if source.startswith(('&>', ';;&'), i):
                    self.error(line, 'unsupported Bash operator')
                flush()
                if pending is not None and tokens and not tokens[-1].operator:
                    heredocs.append((tokens[-1].text, pending == '<<-', tokens[-1].quoted))
                    pending = None
                tokens.append(Token(op, line, operator=True))
                if op in ('<<', '<<-'):
                    pending = op
                if op in ('<', '>') and source[i + 1:i + 2] == '(':
                    self.error(line, "process substitution is unavailable in mksh R43")
                i += len(op)
                if op == '\n':
                    line += 1
                    for delimiter, tabs, quoted_delimiter in heredocs:
                        beginning = i
                        while i < len(source):
                            end = source.find('\n', i)
                            if end < 0:
                                end = len(source)
                            text = source[i:end]
                            if (text.lstrip('\t') if tabs else text) == delimiter:
                                # Lex expansion-bearing bodies, not their data as commands.
                                body = source[beginning:i]
                                if not quoted_delimiter and '`' in body:
                                    self.error(line, 'use $() instead of opaque backtick substitution in here-document')
                                if not quoted_delimiter and '$(' in body:
                                    self.substitutions.append((body, line, True))
                                line += source[beginning:end].count('\n') + 1
                                i = min(end + 1, len(source))
                                break
                            i = min(end + 1, len(source))
                        else:
                            self.error(line, "unterminated here-document")
                    heredocs = []
                continue
            word.append(c)
            started = True
            i += 1
        flush()
        if quote:
            self.error(line, "unclosed quote")
        return tokens

    def command(self, words):
        if not words:
            return
        while words and (ASSIGN.match(words[0].text) or words[0].text == '!'):
            words = words[1:]
        if not words:
            return
        token, args = words[0], words[1:]
        name = token.text
        if not token.literal:
            self.error(token.line, "dynamic command requires a reasoned inline waiver: " + name)
            return
        if name in self.functions:
            return
        if name in ('eval', 'source'):
            self.error(token.line, "opaque command execution; use a fixed script or literal command")
            return
        if name in ('exec', 'command', 'builtin'):
            if args and args[0].text in ('-v', '-V') and name == 'command':
                return
            if args and args[0].text == '--':
                args = args[1:]
            self.command(args)
            return
        if name == 'trap':
            if args and args[0].literal and args[0].text not in ('', '-'):
                self.parse(self.lex(args[0].text, args[0].line))
            elif args and not args[0].literal:
                self.error(token.line, "dynamic trap requires a waiver")
            return
        absolute = '/' in name
        if absolute:
            if name not in PROFILE['absolute_paths']:
                self.error(token.line, "unapproved binary path: " + name)
                return
            name = PROFILE['absolute_paths'][name]
        spec = PROFILE.get('absolute_specs', {}).get(token.text) if absolute else None
        spec = spec if spec is not None else PROFILE['commands'].get(name)
        if spec is None:
            self.error(token.line, "unknown command: " + name)
            return
        if name in ('[', 'test', '[['):
            allowed = PROFILE['test_operators']
            for arg in args:
                if arg.literal and arg.text.startswith('-') and re.fullmatch(r'-[a-zA-Z]+', arg.text) and arg.text not in allowed:
                    self.error(arg.line, 'unsupported test operator: ' + arg.text)
                if arg.text == '=~':
                    self.error(arg.line, 'Bash regex test is unavailable')
            return
        if spec.get('subcommands') and args:
            if args[0].literal and args[0].text not in spec['subcommands']:
                self.error(args[0].line, name + ': unreviewed subcommand ' + args[0].text)
        if name == 'sleep' and args and args[0].literal:
            if not re.fullmatch(spec['operand_pattern'], args[0].text):
                self.error(args[0].line, 'sleep requires integer seconds')
        if name == 'date':
            for arg in args:
                if arg.literal and arg.text.startswith('+') and arg.text not in spec['formats']:
                    self.error(arg.line, 'unreviewed date format: ' + arg.text)
        if name == 'sh':
            for i, arg in enumerate(args):
                if not arg.text.startswith('-') or arg.text == '--':
                    break
                if 'c' in arg.text[1:]:
                    if i + 1 < len(args) and args[i + 1].literal:
                        self.parse(self.lex(args[i + 1].text, args[i + 1].line))
                    else:
                        self.error(arg.line, 'dynamic sh -c requires a waiver')
                    break
        if name == 'md5' and not args:
            self.error(token.line, 'toolbox md5 requires filenames (no implicit stdin)')
        if name == 'dd':
            for arg in args:
                if '=' in arg.text and arg.text.split('=', 1)[0] not in spec['operands']:
                    self.error(arg.line, 'unsupported dd operand: ' + arg.text)
        i, ended = 0, False
        while i < len(args):
            arg = args[i]
            value = arg.text
            i += 1
            if ended:
                continue
            # A dynamic value is an operand or an option argument in our style.
            # Literal option prefixes still need checking, e.g. grep -Q"$x".
            if not arg.literal:
                if value.startswith('-'):
                    self.error(arg.line, name + ': dynamic option requires a waiver')
                continue
            if value == '--' and spec.get('double_dash', False):
                ended = True
                continue
            option_prefix = value.startswith('-') or (name == 'set' and value.startswith('+'))
            if not option_prefix or value in ('-', '+'):
                if not spec.get('interspersed', False):
                    ended = True
                continue
            if value.startswith('--'):
                flag, equal, _ = value.partition('=')
                arity = spec.get('long', {}).get(flag)
                if arity is None:
                    self.error(arg.line, f'{name}: unsupported option {value}')
                elif arity == 1 and not equal:
                    if i == len(args):
                        self.error(arg.line, f'{name}: missing argument for {flag}')
                    i += 1
                continue
            if name == 'kill' and re.fullmatch(r'-(?:[0-9]+|HUP|INT|QUIT|KILL|TERM|STOP|CONT|USR1|USR2)', value):
                continue
            if not spec.get('combined', True) and value not in ['-' + f for f in spec.get('short', {})]:
                self.error(arg.line, f'{name}: unsupported option {value}')
                continue
            for j, flag in enumerate(value[1:], 1):
                arity = spec.get('short', {}).get(flag)
                if arity is None:
                    self.error(arg.line, f'{name}: unsupported option -{flag} (in {value})')
                elif arity:
                    if name == 'set' and flag == 'o':
                        option = value[j + 1:] if j < len(value) - 1 else (args[i].text if i < len(args) else '')
                        if option and option not in PROFILE['mksh']['set_options']:
                            self.error(arg.line, 'unreviewed set option: ' + option)
                    if j == len(value) - 1 and arity == 1:
                        if i == len(args):
                            self.error(arg.line, f'{name}: missing argument for -{flag}')
                        i += 1
                    break

    def parse(self, tokens):
        words, cases, header = [], [], None
        i = 0
        while i < len(tokens):
            token = tokens[i]
            value = token.text
            if cases and cases[-1] == 'pattern':
                if value == 'esac' and not token.quoted:
                    cases.pop()
                elif value == ')' and token.operator:
                    cases[-1] = 'body'
                i += 1
                continue
            if header:
                if value == header and not token.quoted:
                    if header == 'in':
                        cases.append('pattern')
                    header = None
                i += 1
                continue
            if not words and not token.operator and not token.quoted:
                if value in ('case', 'for', 'select'):
                    header = 'in' if value == 'case' else 'do'
                    i += 1
                    continue
                if value in ('if', 'then', 'else', 'elif', 'fi', 'while', 'until', 'do', 'done'):
                    i += 1
                    continue
                if value == 'esac':
                    if cases:
                        cases.pop()
                    i += 1
                    continue
                if i + 2 < len(tokens) and tokens[i + 1].text == '(' and tokens[i + 2].text == ')':
                    i += 3
                    continue
            if value in REDIRECTS and token.operator:
                if words and words[-1].text.isdigit():
                    words.pop()
                i += 2
                continue
            if token.operator:
                self.command(words)
                words = []
                if value == ';;' and cases:
                    cases[-1] = 'pattern'
            else:
                words.append(token)
            i += 1
        self.command(words)

    def run(self):
        if self.source.startswith('#!') and self.source.splitlines()[0].strip() not in ('#!/system/bin/sh', '#!/bin/sh'):
            self.error(1, 'unreviewed shell interpreter')
        self.parse(self.lex(self.source))
        index = 0
        while index < len(self.substitutions):
            text, line, expansions_only = self.substitutions[index]
            tokens = self.lex(text, line)
            if not expansions_only:
                self.parse(tokens)
            index += 1
        result = []
        for line, error in self.errors:
            if line in self.comments:
                self.waivers.append((line, self.comments[line]))
            else:
                result.append(f'{self.name}:{line}: {error}')
        for line, reason in self.comments.items():
            self.waivers.append((line, reason))
        return sorted(set(result))


def paths():
    excluded = set(PROFILE['host_only_scripts'])
    found = set()
    for directory in ('nemesis', 'modules', 'tools', 'core'):
        for path in (ROOT / directory).rglob('*'):
            if not path.is_file() or any(part in ('node_modules', '.git') for part in path.parts):
                continue
            relative = path.relative_to(ROOT).as_posix()
            if relative in excluded or '/test/' in relative or ('/build/' in relative and '/payload/' not in relative) or relative.startswith('tools/test'):
                continue
            data = path.read_bytes()
            if shell_source(path.name, data):
                found.add(path)
    return sorted(found)


def main():
    selected = [Path(arg) for arg in sys.argv[1:]] or paths()
    functions = set()
    for path in selected:
        functions.update(FUNCTION.findall(path.read_text()))
    sources = [(str(path), path.read_text()) for path in selected]
    # Inspect actual package + OTA members too: this catches generated scripts
    # and accidentally shipped host helpers regardless of their source location.
    if len(sys.argv) == 1:
        for archive in sorted((ROOT / 'out/dist').glob('*.zip')):
            if archive.name.endswith('-nemesis-usb.zip'):
                continue  # contains the already inspected package ZIP
            with zipfile.ZipFile(archive) as zipped:
                for name in zipped.namelist():
                    if name.endswith('/'):
                        continue
                    data = zipped.read(name)
                    if shell_source(name, data):
                        sources.append((f'{archive}!{name}', data.decode()))
    errors = []
    for name, source in sources:
        audit = Audit(source, name, functions)
        errors.extend(audit.run())
        for line, reason in sorted(set(audit.waivers)):
            print(f'{name}:{line}: waiver: {reason}')
    if errors:
        print('\n'.join(errors), file=sys.stderr)
        return 1
    print(f'Android 4.4.2 shell profile passed ({len(selected)} sources, {len(sources) - len(selected)} archive scripts)')
    return 0


if __name__ == '__main__':
    sys.exit(main())
