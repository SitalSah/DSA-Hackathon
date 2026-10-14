import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.swing.*;

/**
 * Maze with Keys and Doors.
 *
 * Input  : T (1..6) from stdin, then either a CSV file path (option 1) or N for a random maze (option 2).
 * Output : shortest path length (or -1), CSV with the path marked by 'x', and a Swing visualization.
 *
 * Compile: javac MazeKeys.java
 * Run    : java MazeKeys
 */
public class MazeKeys {

    static final int[] DR = {-1, 1, 0, 0};
    static final int[] DC = {0, 0, -1, 1};

    // One colour per key/door pair (a/A ... f/F). Red and green are reserved for exit/start.
    static final Color[] PAIR_COLORS = {
            new Color(30, 90, 255),   // a/A blue
            new Color(255, 140, 0),   // b/B orange
            new Color(200, 0, 200),   // c/C magenta
            new Color(0, 190, 210),   // d/D cyan
            new Color(130, 80, 30),   // e/E brown
            new Color(110, 50, 170)   // f/F purple
    };

    // ------------------------------------------------------------------ main
    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        System.out.print("Enter T (number of key/door types, 1-6): ");
        int T = sc.nextInt();
        if (T < 1 || T > 6) {
            System.out.println("T must be between 1 and 6.");
            return;
        }
        System.out.print("Option 1 = read grid from CSV, Option 2 = random grid. Choose: ");
        int opt = sc.nextInt();
        sc.nextLine();

        char[][] grid;
        if (opt == 1) {
            System.out.print("CSV file path: ");
            grid = readCsv(sc.nextLine().trim());
        } else {
            System.out.print("Grid size N (>= 2*T+4 recommended, min 7): ");
            int n = Math.max(7, sc.nextInt());
            grid = generate(n, T, new Random());
            writeCsv(grid, null, "maze_generated.csv");
            System.out.println("Generated maze saved to maze_generated.csv");
        }

        List<int[]> path = solve(grid);
        if (path == null) {
            System.out.println(-1);
        } else {
            System.out.println(path.size() - 1);
            writeCsv(grid, path, "maze_solution.csv");
            System.out.println("Solution saved to maze_solution.csv");
        }
        final List<int[]> fp = path;
        SwingUtilities.invokeLater(() -> show(grid, fp));
    }

    // ------------------------------------------------------------------ solver
    /** BFS over states (row, col, keyMask). Returns the path cells from S to E, or null. */
    static List<int[]> solve(char[][] g) {
        int n = g.length;
        int sr = -1, sc = -1, er = -1, ec = -1;
        for (int r = 0; r < n; r++)
            for (int c = 0; c < n; c++) {
                if (g[r][c] == 'S') { sr = r; sc = c; }
                if (g[r][c] == 'E') { er = r; ec = c; }
            }
        if (sr < 0 || er < 0) return null;

        int[] prev = new int[n * n * 64];
        Arrays.fill(prev, -1);
        ArrayDeque<Integer> q = new ArrayDeque<>();
        int start = ((sr * n + sc) << 6);
        prev[start] = -2;
        q.add(start);

        while (!q.isEmpty()) {
            int cur = q.poll();
            int mask = cur & 63, cell = cur >> 6, r = cell / n, c = cell % n;
            if (r == er && c == ec) {
                LinkedList<int[]> path = new LinkedList<>();
                for (int s = cur; s != -2; s = prev[s]) {
                    int cl = s >> 6;
                    path.addFirst(new int[]{cl / n, cl % n});
                }
                return path;
            }
            for (int d = 0; d < 4; d++) {
                int nr = r + DR[d], nc = c + DC[d];
                if (nr < 0 || nc < 0 || nr >= n || nc >= n) continue;
                char ch = g[nr][nc];
                if (ch == '#') continue;
                if (ch >= 'A' && ch <= 'F' && ((mask >> (ch - 'A')) & 1) == 0) continue;
                int nm = mask;
                if (ch >= 'a' && ch <= 'f') nm |= 1 << (ch - 'a');
                int ns = ((nr * n + nc) << 6) | nm;
                if (prev[ns] != -1) continue;
                prev[ns] = cur;
                q.add(ns);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ CSV I/O
    static char[][] readCsv(String file) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String l : Files.readAllLines(Paths.get(file)))
            if (!l.trim().isEmpty()) lines.add(l);
        int n = lines.size();
        char[][] g = new char[n][n];
        for (int r = 0; r < n; r++) {
            String[] parts = lines.get(r).split(",", -1);
            if (parts.length != n)
                throw new IOException("Grid is not square: row " + (r + 1) + " has " + parts.length + " cells, expected " + n);
            for (int c = 0; c < n; c++) {
                String p = parts[c].trim();
                g[r][c] = p.isEmpty() ? '.' : p.charAt(0);
            }
        }
        return g;
    }

    /**
     * Writes the grid. Cells on the path are marked: '.' -> 'x'; keys/doors -> 'x' + letter
     * (e.g. "xa", "xA") so they stay identifiable; S and E are left unchanged.
     */
    static void writeCsv(char[][] g, List<int[]> path, String file) throws IOException {
        int n = g.length;
        boolean[][] on = new boolean[n][n];
        if (path != null) for (int[] p : path) on[p[0]][p[1]] = true;
        try (PrintWriter pw = new PrintWriter(file)) {
            for (int r = 0; r < n; r++) {
                StringBuilder sb = new StringBuilder();
                for (int c = 0; c < n; c++) {
                    char ch = g[r][c];
                    String cell;
                    if (on[r][c] && ch == '.') cell = "x";
                    else if (on[r][c] && Character.isLetter(ch) && ch != 'S' && ch != 'E') cell = "x" + ch;
                    else cell = String.valueOf(ch);
                    if (c > 0) sb.append(',');
                    sb.append(cell);
                }
                pw.println(sb);
            }
        }
    }

    // ------------------------------------------------------------------ random generator
    /**
     * Builds a perfect maze (recursive backtracker) with a few extra openings, puts S top-left and E
     * at the far corner, places T doors along the shortest S->E route and then places each key in an
     * area reachable without the door it opens (and without any later door). Solvable by construction.
     */
    static char[][] generate(int n, int T, Random rnd) {
        while (true) {
            char[][] g = new char[n][n];
            for (char[] row : g) Arrays.fill(row, '#');

            // carve maze on even coordinates
            int m = (n + 1) / 2;
            boolean[][] vis = new boolean[m][m];
            ArrayDeque<int[]> st = new ArrayDeque<>();
            st.push(new int[]{0, 0});
            vis[0][0] = true;
            g[0][0] = '.';
            while (!st.isEmpty()) {
                int[] cur = st.peek();
                List<Integer> dirs = new ArrayList<>(List.of(0, 1, 2, 3));
                Collections.shuffle(dirs, rnd);
                boolean moved = false;
                for (int d : dirs) {
                    int nr = cur[0] + DR[d], nc = cur[1] + DC[d];
                    if (nr < 0 || nc < 0 || nr >= m || nc >= m || vis[nr][nc]) continue;
                    vis[nr][nc] = true;
                    g[cur[0] + nr][cur[1] + nc] = '.';   // wall between (2r,2c) and (2nr,2nc)
                    g[2 * nr][2 * nc] = '.';
                    st.push(new int[]{nr, nc});
                    moved = true;
                    break;
                }
                if (!moved) st.pop();
            }
            // a few extra openings to create loops (so doors are sometimes detours, not just gates)
            int extra = n * n / 40;
            for (int i = 0; i < extra; i++) {
                int r = 1 + rnd.nextInt(n - 2), c = 1 + rnd.nextInt(n - 2);
                if ((r + c) % 2 == 1) g[r][c] = '.';
            }

            int last = 2 * (m - 1);
            g[0][0] = 'S';
            g[last][last] = 'E';

            List<int[]> route = solve(g);
            if (route == null || route.size() < T + 4) continue;

            // doors along the route, evenly spread
            int L = route.size();
            for (int k = 0; k < T; k++) {
                int idx = (int) Math.round((k + 1) * (double) (L - 1) / (T + 1));
                idx = Math.max(1, Math.min(L - 2, idx));
                int[] p = route.get(idx);
                g[p[0]][p[1]] = (char) ('A' + k);
            }
            if (countDoors(g) != T) continue;

            // keys: key k lies in the region reachable with keys < k and doors >= k closed
            boolean ok = true;
            for (int k = 0; k < T && ok; k++) {
                List<int[]> cand = reachableOpen(g, k);
                if (cand.isEmpty()) { ok = false; break; }
                int[] p = cand.get(rnd.nextInt(cand.size()));
                g[p[0]][p[1]] = (char) ('a' + k);
            }
            if (ok && solve(g) != null) return g;
        }
    }

    static int countDoors(char[][] g) {
        int cnt = 0;
        for (char[] row : g) for (char ch : row) if (ch >= 'A' && ch <= 'F') cnt++;
        return cnt;
    }

    /** Plain '.' cells reachable from S when doors with index >= k are treated as walls. */
    static List<int[]> reachableOpen(char[][] g, int k) {
        int n = g.length;
        boolean[][] seen = new boolean[n][n];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[]{0, 0});
        seen[0][0] = true;
        List<int[]> res = new ArrayList<>();
        while (!q.isEmpty()) {
            int[] cur = q.poll();
            if (g[cur[0]][cur[1]] == '.') res.add(cur);
            for (int d = 0; d < 4; d++) {
                int nr = cur[0] + DR[d], nc = cur[1] + DC[d];
                if (nr < 0 || nc < 0 || nr >= n || nc >= n || seen[nr][nc]) continue;
                char ch = g[nr][nc];
                if (ch == '#') continue;
                if (ch >= 'A' && ch <= 'F' && ch - 'A' >= k) continue;
                seen[nr][nc] = true;
                q.add(new int[]{nr, nc});
            }
        }
        return res;
    }

    // ------------------------------------------------------------------ visualization
    static void show(char[][] g, List<int[]> path) {
        int n = g.length;
        int cell = Math.max(8, Math.min(48, 800 / n));
        CELL = cell;
        String title = path == null ? "Maze - no path (-1)" : "Maze - shortest path length = " + (path.size() - 1);

        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g0) {
                super.paintComponent(g0);
                Graphics2D g2 = (Graphics2D) g0;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Font font = new Font(Font.SANS_SERIF, Font.BOLD, Math.max(8, cell / 2));
                g2.setFont(font);
                FontMetrics fm = g2.getFontMetrics();

                for (int r = 0; r < n; r++) {
                    for (int c = 0; c < n; c++) {
                        int x = c * cell, y = r * cell;
                        char ch = g[r][c];
                        Color bg = Color.WHITE;
                        if (ch == '#') bg = Color.BLACK;
                        else if (ch == 'S') bg = new Color(0, 170, 0);
                        else if (ch == 'E') bg = new Color(220, 0, 0);
                        g2.setColor(bg);
                        g2.fillRect(x, y, cell, cell);
                        g2.setColor(new Color(200, 200, 200));
                        g2.drawRect(x, y, cell, cell);

                        if (ch >= 'a' && ch <= 'f') {            // key: filled circle
                            g2.setColor(PAIR_COLORS[ch - 'a']);
                            int pad = cell / 6;
                            g2.fillOval(x + pad, y + pad, cell - 2 * pad, cell - 2 * pad);
                            drawLetter(g2, fm, String.valueOf(ch), x, y, Color.WHITE);
                        } else if (ch >= 'A' && ch <= 'F') {     // door: filled square with border
                            g2.setColor(PAIR_COLORS[ch - 'A']);
                            int pad = cell / 8;
                            g2.fillRect(x + pad, y + pad, cell - 2 * pad, cell - 2 * pad);
                            g2.setColor(Color.BLACK);
                            g2.drawRect(x + pad, y + pad, cell - 2 * pad, cell - 2 * pad);
                            drawLetter(g2, fm, String.valueOf(ch), x, y, Color.WHITE);
                        } else if (ch == 'S' || ch == 'E') {
                            drawLetter(g2, fm, String.valueOf(ch), x, y, Color.WHITE);
                        }
                    }
                }

                if (path != null) {
                    // translucent highlight over path cells
                    g2.setColor(new Color(255, 235, 59, 110));
                    for (int[] p : path) g2.fillRect(p[1] * cell, p[0] * cell, cell, cell);
                    // thick line through cell centres
                    g2.setColor(new Color(40, 40, 40, 220));
                    g2.setStroke(new BasicStroke(Math.max(2f, cell / 6f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    for (int i = 0; i + 1 < path.size(); i++) {
                        int[] a = path.get(i), b = path.get(i + 1);
                        g2.drawLine(a[1] * cell + cell / 2, a[0] * cell + cell / 2,
                                b[1] * cell + cell / 2, b[0] * cell + cell / 2);
                    }
                }
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(n * cell + 1, n * cell + 1);
            }
        };

        JFrame f = new JFrame(title);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.add(new JScrollPane(panel));
        f.add(legend(), BorderLayout.SOUTH);
        f.pack();
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }

    static int CELL = 32;

    static void drawLetter(Graphics2D g2, FontMetrics fm, String s, int x, int y, Color col) {
        g2.setColor(col);
        int tx = x + (CELL - fm.stringWidth(s)) / 2;
        int ty = y + (CELL - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(s, tx, ty);
    }

    static JPanel legend() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 4));
        p.add(chip(Color.WHITE, "open"));
        p.add(chip(Color.BLACK, "wall"));
        p.add(chip(new Color(0, 170, 0), "start"));
        p.add(chip(new Color(220, 0, 0), "exit"));
        for (int i = 0; i < 6; i++)
            p.add(chip(PAIR_COLORS[i], (char) ('a' + i) + "/" + (char) ('A' + i)));
        p.add(chip(new Color(255, 235, 59), "path"));
        return p;
    }

    static JComponent chip(Color c, String text) {
        JLabel l = new JLabel(text, new Icon() {
            public void paintIcon(Component comp, Graphics g, int x, int y) {
                g.setColor(c);
                g.fillRect(x, y, 14, 14);
                g.setColor(Color.GRAY);
                g.drawRect(x, y, 14, 14);
            }
            public int getIconWidth() { return 15; }
            public int getIconHeight() { return 15; }
        }, SwingConstants.LEFT);
        return l;
    }
}
