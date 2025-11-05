package utils

object DiffUtils {
    /**
     * Returns a simple unified diff between oldText and newText using a line-based LCS.
     * This is not a full patch format but follows common conventions:
     * lines starting with ' ' unchanged, '-' removed, '+' added, and an @@ header.
     */
    fun unifiedDiff(oldText: String, newText: String, includeHeader: Boolean = false): String {
        val a = splitLines(oldText)
        val b = splitLines(newText)
        val lcs = lcs(a, b)
        val sb = StringBuilder()
        if (includeHeader) {
            sb.appendLine("--- a")
            sb.appendLine("+++ b")
            sb.appendLine("@@")
        }
        var i = 0
        var j = 0
        for (pair in lcs) {
            val (ai, bj) = pair
            // lines removed from a until ai
            while (i < ai) {
                sb.append('-').appendLine(a[i])
                i++
            }
            // lines added to b until bj
            while (j < bj) {
                sb.append('+').appendLine(b[j])
                j++
            }
            // common line
            if (i < a.size && j < b.size) {
                sb.append(' ').appendLine(a[i])
                i++; j++
            }
        }
        // trailing changes
        while (i < a.size) { sb.append('-').appendLine(a[i]); i++ }
        while (j < b.size) { sb.append('+').appendLine(b[j]); j++ }
        return sb.toString()
    }

    private fun splitLines(s: String): List<String> {
        if (s.isEmpty()) return emptyList()
        return s.split("\n", "\r\n").flatMap { line ->
            if (line.endsWith("\r")) listOf(line.dropLast(1)) else listOf(line)
        }
    }

    private fun lcs(a: List<String>, b: List<String>): List<Pair<Int, Int>> {
        val n = a.size
        val m = b.size
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                dp[i][j] = if (a[i] == b[j]) 1 + dp[i + 1][j + 1] else maxOf(dp[i + 1][j], dp[i][j + 1])
            }
        }
        // Reconstruct path as list of matching indices (i, j)
        var i = 0
        var j = 0
        val matches = mutableListOf<Pair<Int, Int>>()
        while (i < n && j < m) {
            when {
                a[i] == b[j] -> { matches.add(i to j); i++; j++ }
                dp[i + 1][j] >= dp[i][j + 1] -> i++
                else -> j++
            }
        }
        // Convert to boundaries for emitting loops: prepend (0,0) if first match not at start
        val pairs = mutableListOf<Pair<Int, Int>>()
        var prevI = 0
        var prevJ = 0
        matches.forEach { (mi, mj) ->
            pairs.add(mi to mj)
            prevI = mi + 1
            prevJ = mj + 1
        }
        return pairs
    }
}
