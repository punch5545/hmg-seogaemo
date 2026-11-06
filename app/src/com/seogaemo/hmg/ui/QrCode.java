package com.seogaemo.hmg.ui;

import java.io.UnsupportedEncodingException;

/**
 * Minimal QR Code Model 2 encoder (ISO/IEC 18004): byte mode, error correction level M,
 * versions 1-40, automatic mask choice. Pure Java so the host test can compare its matrix
 * against a reference encoder; the head unit has no QR library.
 */
public final class QrCode {
    // Level M per version (index 0 unused).
    private static final int[] ECC_PER_BLOCK = {-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24,
            28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28};
    private static final int[] BLOCKS = {-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16,
            17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49};
    private static final int FORMAT_LEVEL_M = 0;

    public final int version;
    public final int size;
    public final int mask;
    private final boolean[][] modules;   // [row][column], true = dark
    private final boolean[][] function;

    /** Dark module at column x, row y. */
    public boolean get(int x, int y) { return modules[y][x]; }

    public static QrCode encode(String text) {
        try {
            return encode(text.getBytes("UTF-8"), -1);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Smallest version that fits; mask -1 picks the lowest penalty. */
    public static QrCode encode(byte[] data, int forcedMask) {
        for (int version = 1; version <= 40; version++) {
            int capacityBits = dataCodewords(version) * 8;
            int usedBits = 4 + (version < 10 ? 8 : 16) + data.length * 8;
            if (usedBits <= capacityBits) return new QrCode(version, codewords(version, data), forcedMask);
        }
        throw new IllegalArgumentException("data too long for a QR code");
    }

    private QrCode(int version, byte[] dataCodewords, int forcedMask) {
        this.version = version;
        size = version * 4 + 17;
        modules = new boolean[size][size];
        function = new boolean[size][size];
        drawFunctionPatterns();
        drawCodewords(withEcc(dataCodewords));
        int chosen = forcedMask;
        if (chosen < 0) {
            int best = Integer.MAX_VALUE;
            for (int m = 0; m < 8; m++) {
                applyMask(m);
                drawFormat(m);
                int penalty = penalty();
                if (penalty < best) { best = penalty; chosen = m; }
                applyMask(m);
            }
        }
        mask = chosen;
        applyMask(mask);
        drawFormat(mask);
    }

    private static byte[] codewords(int version, byte[] data) {
        int capacity = dataCodewords(version);
        byte[] out = new byte[capacity];
        int[] bit = {0};
        put(out, bit, 0x4, 4);
        put(out, bit, data.length, version < 10 ? 8 : 16);
        for (byte b : data) put(out, bit, b & 0xFF, 8);
        put(out, bit, 0, Math.min(4, capacity * 8 - bit[0]));
        bit[0] = (bit[0] + 7) / 8 * 8;
        for (int i = bit[0] / 8, pad = 0xEC; i < capacity; i++, pad ^= 0xEC ^ 0x11) out[i] = (byte) pad;
        return out;
    }

    private static void put(byte[] out, int[] bit, int value, int length) {
        for (int i = length - 1; i >= 0; i--, bit[0]++)
            if (((value >>> i) & 1) != 0) out[bit[0] >>> 3] |= (byte) (0x80 >>> (bit[0] & 7));
    }

    private static int rawModules(int version) {
        int result = (16 * version + 128) * version + 64;
        if (version >= 2) {
            int align = version / 7 + 2;
            result -= (25 * align - 10) * align - 55;
            if (version >= 7) result -= 36;
        }
        return result;
    }

    static int dataCodewords(int version) {
        return rawModules(version) / 8 - ECC_PER_BLOCK[version] * BLOCKS[version];
    }

    private byte[] withEcc(byte[] data) {
        int blocks = BLOCKS[version];
        int eccLength = ECC_PER_BLOCK[version];
        int raw = rawModules(version) / 8;
        int shortBlocks = blocks - raw % blocks;
        int shortLength = raw / blocks;
        byte[] divisor = rsDivisor(eccLength);
        byte[][] all = new byte[blocks][];
        for (int i = 0, k = 0; i < blocks; i++) {
            int dataLength = shortLength - eccLength + (i < shortBlocks ? 0 : 1);
            byte[] block = new byte[shortLength + 1];
            System.arraycopy(data, k, block, 0, dataLength);
            k += dataLength;
            byte[] ecc = rsRemainder(block, dataLength, divisor);
            System.arraycopy(ecc, 0, block, block.length - eccLength, eccLength);
            all[i] = block;
        }
        byte[] out = new byte[raw];
        int n = 0;
        for (int i = 0; i < shortLength + 1; i++)
            for (int j = 0; j < blocks; j++)
                if (i != shortLength - eccLength || j >= shortBlocks) out[n++] = all[j][i];
        return out;
    }

    private static byte[] rsDivisor(int degree) {
        byte[] result = new byte[degree];
        result[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < degree; j++) {
                result[j] = (byte) multiply(result[j] & 0xFF, root);
                if (j + 1 < degree) result[j] ^= result[j + 1];
            }
            root = multiply(root, 0x02);
        }
        return result;
    }

    private static byte[] rsRemainder(byte[] data, int length, byte[] divisor) {
        byte[] result = new byte[divisor.length];
        for (int d = 0; d < length; d++) {
            int factor = (data[d] ^ result[0]) & 0xFF;
            System.arraycopy(result, 1, result, 0, result.length - 1);
            result[result.length - 1] = 0;
            for (int i = 0; i < result.length; i++) result[i] ^= (byte) multiply(divisor[i] & 0xFF, factor);
        }
        return result;
    }

    private static int multiply(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = (z << 1) ^ ((z >>> 7) * 0x11D);
            z ^= ((y >>> i) & 1) * x;
        }
        return z;
    }

    private void set(int x, int y, boolean dark) {
        modules[y][x] = dark;
        function[y][x] = true;
    }

    private void drawFunctionPatterns() {
        for (int i = 0; i < size; i++) {
            set(6, i, i % 2 == 0);
            set(i, 6, i % 2 == 0);
        }
        finder(3, 3);
        finder(size - 4, 3);
        finder(3, size - 4);
        int[] align = alignmentPositions();
        int n = align.length;
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                if (!(i == 0 && j == 0 || i == 0 && j == n - 1 || i == n - 1 && j == 0))
                    for (int dy = -2; dy <= 2; dy++)
                        for (int dx = -2; dx <= 2; dx++)
                            set(align[i] + dx, align[j] + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
        drawFormat(0);  // reserves the format area; the real bits are drawn after masking
        if (version >= 7) {
            int rem = version;
            for (int i = 0; i < 12; i++) rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
            int bits = version << 12 | rem;
            for (int i = 0; i < 18; i++) {
                boolean dark = ((bits >>> i) & 1) != 0;
                int a = size - 11 + i % 3, b = i / 3;
                set(a, b, dark);
                set(b, a, dark);
            }
        }
    }

    private void finder(int x, int y) {
        for (int dy = -4; dy <= 4; dy++)
            for (int dx = -4; dx <= 4; dx++) {
                int distance = Math.max(Math.abs(dx), Math.abs(dy));
                int xx = x + dx, yy = y + dy;
                if (xx >= 0 && xx < size && yy >= 0 && yy < size) set(xx, yy, distance != 2 && distance != 4);
            }
    }

    private int[] alignmentPositions() {
        if (version == 1) return new int[0];
        int n = version / 7 + 2;
        int step = version == 32 ? 26 : (version * 4 + n * 2 + 1) / (n * 2 - 2) * 2;
        int[] result = new int[n];
        result[0] = 6;
        for (int i = n - 1, position = size - 7; i >= 1; i--, position -= step) result[i] = position;
        return result;
    }

    private void drawFormat(int maskId) {
        int data = FORMAT_LEVEL_M << 3 | maskId;
        int rem = data;
        for (int i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        int bits = (data << 10 | rem) ^ 0x5412;
        for (int i = 0; i <= 5; i++) set(8, i, bit(bits, i));
        set(8, 7, bit(bits, 6));
        set(8, 8, bit(bits, 7));
        set(7, 8, bit(bits, 8));
        for (int i = 9; i < 15; i++) set(14 - i, 8, bit(bits, i));
        for (int i = 0; i < 8; i++) set(size - 1 - i, 8, bit(bits, i));
        for (int i = 8; i < 15; i++) set(8, size - 15 + i, bit(bits, i));
        set(8, size - 8, true);
    }

    private static boolean bit(int value, int index) { return ((value >>> index) & 1) != 0; }

    private void drawCodewords(byte[] data) {
        int i = 0;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) right = 5;
            for (int vert = 0; vert < size; vert++)
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    boolean upward = ((right + 1) & 2) == 0;
                    int y = upward ? size - 1 - vert : vert;
                    if (!function[y][x] && i < data.length * 8) {
                        modules[y][x] = bit(data[i >>> 3], 7 - (i & 7));
                        i++;
                    }
                }
        }
    }

    private void applyMask(int maskId) {
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) {
                if (function[y][x]) continue;
                boolean invert;
                switch (maskId) {
                    case 0: invert = (x + y) % 2 == 0; break;
                    case 1: invert = y % 2 == 0; break;
                    case 2: invert = x % 3 == 0; break;
                    case 3: invert = (x + y) % 3 == 0; break;
                    case 4: invert = (x / 3 + y / 2) % 2 == 0; break;
                    case 5: invert = x * y % 2 + x * y % 3 == 0; break;
                    case 6: invert = (x * y % 2 + x * y % 3) % 2 == 0; break;
                    default: invert = ((x + y) % 2 + x * y % 3) % 2 == 0; break;
                }
                modules[y][x] ^= invert;
            }
    }

    /** ISO/IEC 18004 mask penalty (runs, 2x2 blocks, finder-like patterns, dark balance). */
    private int penalty() {
        int result = 0;
        for (int pass = 0; pass < 2; pass++)
            for (int a = 0; a < size; a++) {
                int run = 0;
                boolean color = false;
                int bits = 0;
                for (int b = 0; b < size; b++) {
                    boolean dark = pass == 0 ? modules[a][b] : modules[b][a];
                    if (b > 0 && dark == color) {
                        run++;
                        if (run == 5) result += 3;
                        else if (run > 5) result++;
                    } else {
                        color = dark;
                        run = 1;
                    }
                    bits = ((bits << 1) & 0x7FF) | (dark ? 1 : 0);
                    if (b >= 10 && (bits == 0x05D || bits == 0x5D0)) result += 40;
                }
            }
        int dark = 0;
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) {
                boolean c = modules[y][x];
                if (c) dark++;
                if (x < size - 1 && y < size - 1 && c == modules[y][x + 1] && c == modules[y + 1][x]
                        && c == modules[y + 1][x + 1]) result += 3;
            }
        int total = size * size;
        int k = (Math.abs(dark * 20 - total * 10) + total - 1) / total - 1;
        return result + Math.max(0, k) * 10;
    }
}
