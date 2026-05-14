package io.github.zeroone3010.pngfilteropt.diagnostics;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

final class LzGreedyDiagnostics {
    private static final int WINDOW = 32 * 1024;
    private static final int MIN_MATCH = 3;
    private static final int MAX_MATCH = 258;

    private final int maxCandidates;

    LzGreedyDiagnostics(int maxCandidates) { this.maxCandidates = Math.max(1, maxCandidates); }

    LzParseDiagnostics analyze(byte[] data) {
        Map<Integer, ArrayDeque<Integer>> positions = new HashMap<>();
        long literals = 0, matches = 0, matchedBytes = 0;
        long[] lenBuckets = new long[6], distBuckets = new long[5];
        int[] litHist = new int[256], lenCodeHist = new int[29], distCodeHist = new int[30];
        long costBits = 0;
        int i = 0, maxLen = 0;
        while (i < data.length) {
            Match best = findBest(data, i, positions);
            if (best.length >= MIN_MATCH) {
                matches++; matchedBytes += best.length; maxLen = Math.max(maxLen, best.length);
                lenBuckets[lenBucket(best.length)]++;
                distBuckets[distBucket(best.distance)]++;
                int lenCode = lengthCode(best.length); int distCode = distanceCode(best.distance);
                lenCodeHist[lenCode]++; distCodeHist[distCode]++;
                costBits += 10 + lengthExtraBits(best.length) + distanceExtraBits(best.distance);
                for (int k = 0; k < best.length; k++) insert(data, i + k, positions);
                i += best.length;
            } else {
                literals++; litHist[data[i] & 0xFF]++; costBits += 8; insert(data, i, positions); i++;
            }
        }
        long literalBytes = data.length - matchedBytes;
        return new LzParseDiagnostics(literals, matches, matchedBytes, literalBytes,
                data.length == 0 ? 0 : (100.0 * matchedBytes / data.length),
                matches == 0 ? 0 : ((double) matchedBytes / matches),
                maxLen, lenBuckets, distBuckets, costBits,
                entropy(litHist, literals), entropy(lenCodeHist, matches), entropy(distCodeHist, matches));
    }

    private Match findBest(byte[] data, int pos, Map<Integer, ArrayDeque<Integer>> positions) {
        if (pos + 3 >= data.length) return new Match(0, 0);
        int key = key3(data, pos); ArrayDeque<Integer> q = positions.get(key); if (q == null) return new Match(0, 0);
        int bestLen = 0, bestDist = 0, seen = 0;
        for (var it = q.descendingIterator(); it.hasNext() && seen < maxCandidates; ) {
            int prev = it.next(); seen++; int dist = pos - prev; if (dist <= 0 || dist > WINDOW) continue;
            int max = Math.min(MAX_MATCH, data.length - pos); int m = 0;
            while (m < max && data[prev + m] == data[pos + m]) m++;
            if (m > bestLen) { bestLen = m; bestDist = dist; if (m == MAX_MATCH) break; }
        }
        return new Match(bestLen, bestDist);
    }

    private void insert(byte[] data, int pos, Map<Integer, ArrayDeque<Integer>> positions) {
        if (pos + 3 >= data.length) return;
        int key = key3(data, pos);
        ArrayDeque<Integer> q = positions.computeIfAbsent(key, k -> new ArrayDeque<>());
        q.addLast(pos);
        while (!q.isEmpty() && pos - q.peekFirst() > WINDOW) q.removeFirst();
        while (q.size() > maxCandidates * 8) q.removeFirst();
    }

    private static int key3(byte[] d, int p) { return ((d[p] & 0xFF) << 16) | ((d[p + 1] & 0xFF) << 8) | (d[p + 2] & 0xFF); }
    private static int lenBucket(int l){ if(l<=7)return 0; if(l<=15)return 1; if(l<=31)return 2; if(l<=63)return 3; if(l<=127)return 4; return 5; }
    private static int distBucket(int d){ if(d<=255)return 0; if(d<=1023)return 1; if(d<=4095)return 2; if(d<=16383)return 3; return 4; }
    private static double entropy(int[] hist, long total){ if(total<=0)return 0; double e=0; for(int c:hist){ if(c==0)continue; double p=(double)c/total; e-=p*(Math.log(p)/Math.log(2)); } return e; }

    private static int lengthCode(int len){int[] base={3,4,5,6,7,8,9,10,11,13,15,17,19,23,27,31,35,43,51,59,67,83,99,115,131,163,195,227,258};for(int i=0;i<base.length;i++){int b=base[i];int n=(i<8||i==28)?1:(1<<(Math.max(0,(i-8)/4)));if(len>=b&&len< b+n)return i;}return 28;}
    private static int distanceCode(int dist){int code=0,b=1;while(code<29){int span= code<2?1:(1<<((code/2)-1));if(dist>=b&&dist<b+span)return code;b+=span;code++;}return 29;}
    private static int lengthExtraBits(int len){int c=lengthCode(len); if(c<=7||c==28)return 0; return (c-4)/4;}
    private static int distanceExtraBits(int dist){int c=distanceCode(dist); return c<=3?0:(c/2)-1;}

    private record Match(int length, int distance){}
}
