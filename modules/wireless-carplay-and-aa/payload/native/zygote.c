/* Modified by HMG-Seogaemo: standalone wireless zygote wrapper. */
/*
 * hmgapi-hook zygote wrapper: installed as /system/bin/app_process, runs the
 * stock binary (kept as app_process.seogaemo-stock) with the same arguments.
 *
 * For the zygote only (argv has --zygote), it adds LD_PRELOAD=libseogaemo_wireless_hook.so
 * when all of these hold, so the hook runtime loads into every app:
 *   /data/nemesis/backup/hmg-seogaemo/modules/wireless-carplay-and-aa/enable exists      (step 2 creates it)
 *   /data/nemesis/backup/hmg-seogaemo/modules/wireless-carplay-and-aa/disable is absent  (manual off switch)
 *   /system/lib/libseogaemo_wireless_hook.so exists
 *   /data/nemesis/backup/hmg-seogaemo/modules/wireless-carplay-and-aa/starts < 3         (boot guard)
 * Boot guard reset: with hooks on, a watcher (double-forked, so init adopts it
 * and the zygote never sees it exit) waits GUARD_DELAY seconds and writes 0 to
 * starts if this zygote pid is still alive. On 4.4 a system_server crash kills
 * the zygote too, so a zygote alive that long means the boot worked.
 * Stock ARM app_process re-execs /system/bin/app_process once with
 * NO_ADDR_COMPAT_LAYOUT_FIXUP=1 (ADDR_COMPAT_LAYOUT personality), so the zygote
 * passes through here twice per start. Only the first pass counts and sets the
 * preload; the second passes the environment through (LD_PRELOAD included).
 * Every start writes one line to the kernel log (tag "hmgapi-hook").
 * If the exec fails there is nothing left to fall back to, so this file stays
 * as small as possible.
 *
 * Builds freestanding for ARM (no libc, raw syscalls). AGPL-3.0-or-later.
 */
typedef unsigned long ul;

#ifndef STOCK
#define STOCK "/system/bin/app_process.seogaemo-stock"
#endif
#ifndef KMSG
#define KMSG "/dev/kmsg"
#endif
#ifndef HOOKDIR
#define HOOKDIR "/data/nemesis/backup/hmg-seogaemo/modules/wireless-carplay-and-aa"
#endif
#ifndef HOOKLIB
#define HOOKLIB "/system/lib/libseogaemo_wireless_hook.so"
#endif
#define MAXSTARTS 3
#ifndef GUARD_DELAY
#define GUARD_DELAY 120
#endif
#define MAXENV 256

static long sys3(long n, long a, long b, long c)
{
    register long r7 asm("r7") = n; register long r0 asm("r0") = a;
    register long r1 asm("r1") = b; register long r2 asm("r2") = c;
    asm volatile("svc 0" : "+r"(r0) : "r"(r7), "r"(r1), "r"(r2) : "memory");
    return r0;
}
static long xopen(const char *p, int fl, int m) { return sys3(5, (long)p, fl, m); }
static long xread(int fd, void *b, ul n) { return sys3(3, fd, (long)b, n); }
static long xwrite(int fd, const void *b, ul n) { return sys3(4, fd, (long)b, n); }
static void xclose(int fd) { sys3(6, fd, 0, 0); }
static long xexecve(const char *p, char **av, char **ev) { return sys3(11, (long)p, (long)av, (long)ev); }
static long xfork(void) { return sys3(2, 0, 0, 0); }
static long xgetpid(void) { return sys3(20, 0, 0, 0); }
static void xexit(int c) { sys3(1, c, 0, 0); for (;;) ; }
static void xsleep(long s) { long ts[2] = { s, 0 }; sys3(162, (long)ts, 0, 0); }
#define O_RDONLY 0
#define O_WRONLY 1
#define O_WR 0x241   /* O_WRONLY|O_CREAT|O_TRUNC */

static ul slen(const char *s) { ul n = 0; while (s[n]) n++; return n; }
static int seq(const char *a, const char *b) { while (*a && *a == *b) a++, b++; return *a == *b; }
static int spre(const char *s, const char *p) { while (*p && *s == *p) s++, p++; return !*p; }
static int exists(const char *p) { long fd = xopen(p, O_RDONLY, 0); if (fd < 0) return 0; xclose(fd); return 1; }

static char line[160];
static ul lpos;
static void put(const char *s) { while (*s && lpos < sizeof line - 2) line[lpos++] = *s++; }
static void putn(long v)
{
    char t[12]; int i = 0;
    if (v < 0) { put("-"); v = -v; }
    do t[i++] = '0' + v % 10; while ((v /= 10) && i < 11);
    while (i) { char c[2] = { t[--i], 0 }; put(c); }
}
static void flush(void)
{
    long fd = xopen(KMSG, O_WRONLY, 0);
    line[lpos++] = '\n';
    if (fd >= 0) { xwrite(fd, line, lpos); xclose(fd); }
    lpos = 0;
}

/* Reads and bumps the start counter; returns the value before this start, or -1. */
static long bump_starts(void)
{
    char b[16]; long fd, n, v = 0, i;
    fd = xopen(HOOKDIR "/starts", O_RDONLY, 0);
    if (fd >= 0) {
        n = xread(fd, b, sizeof b);
        xclose(fd);
        for (i = 0; i < n && b[i] >= '0' && b[i] <= '9'; i++) v = v * 10 + (b[i] - '0');
    }
    fd = xopen(HOOKDIR "/starts", O_WR, 0600);
    if (fd < 0) return -1;
    {
        char t[12]; int k = 0; long w = v + 1;
        do t[k++] = '0' + w % 10; while ((w /= 10) && k < 11);
        while (k) { char c = t[--k]; xwrite(fd, &c, 1); }
    }
    xclose(fd);
    return v;
}

static int alive(long pid)
{
    char p[24] = "/proc/", c;
    int i = 6, k = 0; char t[12]; long fd, n;
    do t[k++] = '0' + pid % 10; while ((pid /= 10) && k < 11);
    while (k) p[i++] = t[--k];
    p[i] = 0;
    { const char *s = "/cmdline"; while (*s) p[i++] = *s++; p[i] = 0; }
    fd = xopen(p, O_RDONLY, 0);
    if (fd < 0) return 0;
    n = xread(fd, &c, 1);     /* a zombie has an empty cmdline */
    xclose(fd);
    return n == 1;
}

/* Double fork: the middle child exits at once and is reaped here, before the
   exec, so the zygote never gets a SIGCHLD for it. */
static void start_watcher(void)
{
    long zpid = xgetpid(), pid = xfork();
    if (pid == 0) {
        if (xfork() == 0) {
            long fd;
            sys3(66, 0, 0, 0);                 /* setsid */
            xsleep(GUARD_DELAY);
            if (!alive(zpid)) xexit(0);
            fd = xopen(HOOKDIR "/starts", O_WR, 0600);
            if (fd >= 0) { xwrite(fd, "0", 1); xclose(fd); }
            put("<6>seogaemo-wireless: zygote "); putn(zpid); put(" up "); putn(GUARD_DELAY);
            put(" s, boot guard reset"); flush();
        }
        xexit(0);
    }
    if (pid > 0) sys3(114, pid, 0, 0);         /* wait4(pid, NULL, 0, NULL) */
}

static char *env[MAXENV];
static char preload[] = "LD_PRELOAD=" HOOKLIB;

int main(int argc, char **argv, char **envp)
{
    int i, n = 0, zygote = 0, use = 0, reexec = 0;
    long starts = -1, rc;

    for (i = 1; i < argc; i++)
        if (seq(argv[i], "--zygote")) zygote = 1;
    for (i = 0; envp[i]; i++)
        if (spre(envp[i], "NO_ADDR_COMPAT_LAYOUT_FIXUP=")) reexec = 1;

    put("<6>seogaemo-wireless: ");
    put(zygote ? "zygote" : "app_process");
    if (reexec) put(" (re-exec)");
    else if (zygote && exists(HOOKDIR "/enable")) {
        if (exists(HOOKDIR "/disable")) put(", hooks off (disable file)");
        else if (!exists(HOOKLIB)) put(", hooks off (no " HOOKLIB ")");
        else {
            starts = bump_starts();
            if (starts < 0) put(", hooks off (can't write starts)");
            else if (starts >= MAXSTARTS) { put(", hooks off (boot guard, starts="); putn(starts); put(")"); }
            else { use = 1; put(", hooks on, start "); putn(starts + 1); }
        }
    }

    for (i = 0; envp[i] && n < MAXENV - 2; i++)
        if (!(use && spre(envp[i], "LD_PRELOAD="))) env[n++] = envp[i];
    if (use) env[n++] = preload;
    env[n] = 0;

    put(", exec " STOCK);
    flush();
    if (use) start_watcher();
    rc = xexecve(STOCK, argv, env);
    put("<3>seogaemo-wireless: exec " STOCK " failed, errno ");
    putn(-rc);
    flush();
    (void)slen;
    return 127;
}

__attribute__((naked, noreturn)) void _start(void)
{
    asm volatile("ldr r0,[sp]\n add r1,sp,#4\n add r2,r1,r0,lsl #2\n add r2,r2,#4\n"
                 "bl main\n mov r7,#1\n svc 0\n");
}
