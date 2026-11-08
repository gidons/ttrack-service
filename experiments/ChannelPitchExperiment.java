import be.tarsos.dsp.pitch.FastYin;
import be.tarsos.dsp.pitch.McLeodPitchMethod;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import be.tarsos.dsp.pitch.PitchDetector;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Scratch experiment: can per-channel pitch-detection confidence identify which
 * channel of a stereo part-predominant track carries the target part?
 *
 * It reads the choir library's stereo tracks from data/&lt;song&gt;/ (not in the repository; see
 * SONGS below for the songs and which channel carries the part), decodes them with ffmpeg, and
 * reports how well the channel-comparison rule used by TargetChannelDetector separates them, plus
 * sweeps of its parameters. The results are summarized in the design document (section 7.5).
 *
 * Run from the repo root:
 *   java -cp ~/.m2/repository/be/tarsos/dsp/core/2.5/core-2.5.jar \
 *        experiments/ChannelPitchExperiment.java [yin|mpm]
 * Optional system properties: -Dwin=2048 (analysis window) and -Dgate=0.003 (energy gate).
 */
public class ChannelPitchExperiment {

    static final int SR = 44100;
    static final int WIN = Integer.getInteger("win", 2048);
    static final int HOP = WIN;
    /** Frames quieter than this RMS (full scale = 1.0) in a channel are not analyzed in it. */
    static final double ENERGY_GATE = Double.parseDouble(System.getProperty("gate", "0.003"));
    static final double MIN_HZ = 60, MAX_HZ = 1400;
    static final double THR = 0.80;
    static final double FRAME_SEC = (double) HOP / SR;

    record Frame(double rms, double pitch, double prob) {}

    /** truth: 0/1 = known target channel; -1 = must be rejected (no single target channel). */
    record Case(String name, String kind, Frame[] l, Frame[] r, int truth) {}

    record Song(String dir, int targetChannel) {}

    static final Song[] SONGS = {
        new Song("keep", 0), new Song("wonderful", 0), new Song("country", 1),
        new Song("ceimht", 0), new Song("down", 0),
    };
    static final String[] PARTS = {"bari", "bass", "lead", "tenor"};

    static float[][] decodeStereo(Path f) throws Exception {
        Process p = new ProcessBuilder("ffmpeg", "-v", "error", "-i", f.toString(),
                "-ar", "" + SR, "-ac", "2", "-f", "s16le", "pipe:1")
            .redirectError(ProcessBuilder.Redirect.INHERIT).start();
        byte[] all = p.getInputStream().readAllBytes();
        p.waitFor();
        int n = all.length / 4;
        float[][] ch = new float[2][n];
        ByteBuffer bb = ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < n; i++) {
            ch[0][i] = bb.getShort() / 32768f;
            ch[1][i] = bb.getShort() / 32768f;
        }
        return ch;
    }

    static Frame[] analyze(float[] x, String algo) {
        PitchDetector det = algo.equals("mpm") ? new McLeodPitchMethod(SR, WIN) : new FastYin(SR, WIN);
        int nf = (x.length - WIN) / HOP + 1;
        Frame[] out = new Frame[nf];
        float[] buf = new float[WIN];
        for (int f = 0; f < nf; f++) {
            System.arraycopy(x, f * HOP, buf, 0, WIN);
            double sum = 0;
            for (float v : buf) sum += v * v;
            double rms = Math.sqrt(sum / WIN);
            if (rms < ENERGY_GATE) {
                out[f] = new Frame(rms, -1, 0);
                continue;
            }
            PitchDetectionResult r = det.getPitch(buf);
            double hz = r.getPitch();
            boolean ok = hz >= MIN_HZ && hz <= MAX_HZ;
            out[f] = new Frame(rms, ok ? hz : -1, ok ? r.getProbability() : 0);
        }
        return out;
    }

    static boolean active(Frame f) { return f.rms >= ENERGY_GATE; }

    /** Stats over frames [from, to): {voicedFracL, voicedFracR, unionActiveFrames}. */
    static double[] stats(Frame[] l, Frame[] r, int from, int to, double thr) {
        int union = 0, vl = 0, vr = 0;
        to = Math.min(to, Math.min(l.length, r.length));
        for (int i = from; i < to; i++) {
            if (!active(l[i]) && !active(r[i])) continue;
            union++;
            if (l[i].pitch > 0 && l[i].prob >= thr) vl++;
            if (r[i].pitch > 0 && r[i].prob >= thr) vr++;
        }
        return new double[] {union == 0 ? 0 : (double) vl / union, union == 0 ? 0 : (double) vr / union, union};
    }

    static double margin(double a, double b) { return (a + b) == 0 ? 0 : (a - b) / (a + b); }

    // Candidate decision rule.
    static final double MIN_MARGIN = 0.20;
    static final double MIN_WINNER_VOICED = 0.50;
    static final int MIN_ACTIVE_FRAMES = 50;

    /** @return 0/1 = chosen channel, -1 = rejected. */
    static int decide(double[] s) {
        if (s[2] < MIN_ACTIVE_FRAMES) return -1;
        double m = margin(s[0], s[1]);
        int win = m >= 0 ? 0 : 1;
        if (Math.abs(m) < MIN_MARGIN) return -1;
        if (s[win] < MIN_WINNER_VOICED) return -1;
        return win;
    }

    static String partOf(String filename) {
        String n = filename.toLowerCase(Locale.ROOT);
        for (String p : PARTS) if (n.contains(p)) return p;
        return "?";
    }

    static List<Case> buildCases(String algo) throws Exception {
        List<Case> cases = new ArrayList<>();
        for (Song s : SONGS) {
            List<Path> files = new ArrayList<>();
            try (var st = Files.list(Path.of("data", s.dir))) {
                st.filter(p -> p.toString().endsWith(".mp3")).sorted().forEach(files::add);
            }
            List<Frame[]> target = new ArrayList<>();
            List<String> names = new ArrayList<>();
            for (Path f : files) {
                float[][] ch = decodeStereo(f);
                Frame[] l = analyze(ch[0], algo);
                Frame[] r = analyze(ch[1], algo);
                String part = partOf(f.getFileName().toString());
                cases.add(new Case(s.dir + "/" + part, "real", l, r, s.targetChannel));
                target.add(s.targetChannel == 0 ? l : r);
                names.add(part);
            }
            for (int a = 0; a < target.size(); a++) {
                for (int b = a + 1; b < target.size(); b++) {
                    cases.add(new Case(s.dir + "/" + names.get(a) + "+" + names.get(b), "synth-2target",
                        target.get(a), target.get(b), -1));
                }
            }
        }
        // A real full-mix stereo file: polyphonic in both channels; nothing to extract.
        Path full = Path.of("data", "gothedist", "gothedist-full.wav");
        if (Files.exists(full)) {
            float[][] ch = decodeStereo(full);
            cases.add(new Case("gothedist/full-mix", "full-mix", analyze(ch[0], algo), analyze(ch[1], algo), -1));
        }
        return cases;
    }

    static void printCases(List<Case> cases) {
        System.out.println("\nPer-case results (prob >= " + THR + ", full length):");
        for (Case c : cases) {
            double[] s = stats(c.l, c.r, 0, Integer.MAX_VALUE, THR);
            int d = decide(s);
            String expect = c.truth < 0 ? "REJECT" : (c.truth == 0 ? "LEFT" : "RIGHT");
            String got = d < 0 ? "REJECT" : (d == 0 ? "LEFT" : "RIGHT");
            System.out.printf("%-34s %-13s L=%5.1f%% R=%5.1f%% m=%+.2f  expect=%-6s got=%-6s %s%n",
                c.name, c.kind, s[0] * 100, s[1] * 100, margin(s[0], s[1]), expect, got,
                expect.equals(got) ? "" : "<<<<< MISMATCH");
        }
    }

    static void excerptStudy(List<Case> cases) {
        System.out.println("\nExcerpt-length study (prob >= " + THR + "; rule: |m|>=" + MIN_MARGIN
            + ", winner voiced>=" + MIN_WINNER_VOICED + ", active frames>=" + MIN_ACTIVE_FRAMES + ")");
        System.out.printf("%-18s %7s %7s %9s %9s %9s %9s %9s%n",
            "excerpt", "frames", "wrong", "falseRej", "falseAcc", "minRealM", "maxRejM", "minWinV");
        int[] startsSec = {0, 0, 0, 0, 0, 0, 30, 60};
        int[] lensSec = {5, 10, 20, 30, 60, 100000, 10, 10};
        for (int k = 0; k < startsSec.length; k++) {
            int from = (int) (startsSec[k] / FRAME_SEC);
            int to = lensSec[k] >= 100000 ? Integer.MAX_VALUE : from + (int) (lensSec[k] / FRAME_SEC);
            int wrong = 0, falseRej = 0, falseAcc = 0;
            double minRealM = 9, maxRejM = 0, minWinV = 9;
            for (Case c : cases) {
                double[] s = stats(c.l, c.r, from, to, THR);
                int d = decide(s);
                double m = margin(s[0], s[1]);
                if (c.truth >= 0) {
                    double signed = c.truth == 0 ? m : -m;
                    minRealM = Math.min(minRealM, signed);
                    minWinV = Math.min(minWinV, s[c.truth]);
                    if (d < 0) falseRej++;
                    else if (d != c.truth) wrong++;
                } else {
                    maxRejM = Math.max(maxRejM, Math.abs(m));
                    if (d >= 0) falseAcc++;
                }
            }
            String label = (lensSec[k] >= 100000 ? "all" : lensSec[k] + "s") + (startsSec[k] > 0 ? "@" + startsSec[k] + "s" : "");
            System.out.printf("%-18s %7d %7d %9d %9d %9.2f %9.2f %9.2f%n",
                label, to == Integer.MAX_VALUE ? -1 : to - from, wrong, falseRej, falseAcc, minRealM, maxRejM, minWinV);
        }
    }

    /** How wide is the safe operating region for the confidence threshold (full-length audio)? */
    static void thresholdSweep(List<Case> cases) {
        System.out.println("\nThreshold sweep (full length; rule: |m|>=" + MIN_MARGIN + ", winner voiced>="
            + MIN_WINNER_VOICED + ")");
        System.out.printf("%-6s %6s %9s %9s %9s %9s %9s%n",
            "thr", "wrong", "falseRej", "falseAcc", "minRealM", "maxRejM", "minWinV");
        for (double thr : new double[] {0.5, 0.6, 0.7, 0.75, 0.8, 0.85, 0.9, 0.95}) {
            int wrong = 0, falseRej = 0, falseAcc = 0;
            double minRealM = 9, maxRejM = 0, minWinV = 9;
            for (Case c : cases) {
                double[] s = stats(c.l, c.r, 0, Integer.MAX_VALUE, thr);
                int d = decide(s);
                double m = margin(s[0], s[1]);
                if (c.truth >= 0) {
                    minRealM = Math.min(minRealM, c.truth == 0 ? m : -m);
                    minWinV = Math.min(minWinV, s[c.truth]);
                    if (d < 0) falseRej++;
                    else if (d != c.truth) wrong++;
                } else {
                    maxRejM = Math.max(maxRejM, Math.abs(m));
                    if (d >= 0) falseAcc++;
                }
            }
            System.out.printf("%-6.2f %6d %9d %9d %9.2f %9.2f %9.2f%n", thr, wrong, falseRej, falseAcc, minRealM, maxRejM, minWinV);
        }
    }

    public static void main(String[] args) throws Exception {
        String algo = args.length > 0 ? args[0] : "yin";
        System.out.println("algorithm=" + algo + " win=" + WIN + " hop=" + HOP + " gate=" + ENERGY_GATE);
        long t0 = System.currentTimeMillis();
        List<Case> cases = buildCases(algo);
        printCases(cases);
        excerptStudy(cases);
        thresholdSweep(cases);
        System.out.println("\nelapsed " + (System.currentTimeMillis() - t0) / 1000 + "s");
    }
}
