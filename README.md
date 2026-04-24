# Quiz Leaderboard Solver — Bajaj Finserv Health | JAVA Qualifier | SRM

A Java application that polls an external quiz API 10 times, deduplicates events, aggregates participant scores, and submits a sorted leaderboard — exactly once.

---

## Problem Statement

A quiz show validator API delivers participant scores across multiple rounds. Due to distributed system behaviour, **the same event data can appear in multiple poll responses**. The challenge is to:

- Poll the API **10 times** (poll index `0` through `9`) with a **mandatory 5-second delay** between each call
- **Deduplicate** events using a composite key of `(roundId + participant)`
- **Aggregate** scores per participant across all unique events
- Generate a **leaderboard sorted by total score** (descending; alphabetical on tie)
- **Submit once** to the validator

---

## Solution Approach

### Deduplication Strategy

Each event is identified by the composite key `roundId::participant` (e.g. `"R1::Diana"`). A `HashSet<String>` tracks every key seen so far. If an incoming event's key is already in the set, it is silently skipped — preventing double-counting.

```
Poll 1 → R1::Diana score=200  → NEW   → counted  ✅
Poll 7 → R1::Diana score=200  → SEEN  → skipped  ✅
```

### Score Aggregation

A `LinkedHashMap<String, Integer>` maps each participant name to their running total. New events are merged with `Map.merge(participant, score, Integer::sum)`.

### Leaderboard Sorting

Entries are sorted by total score descending. Ties are broken alphabetically to ensure a deterministic order.

---

## Project Structure

```
├── pom.xml
├── output.txt
├── README.md
└── src/
    └── main/
        └── java/
            └── com/bajaj/quiz/
                └── QuizLeaderboardSolver.java
```

---

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 11 or higher |
| Maven | 3.6+ |

---

## How to Run

**1. Clone the repository**
```bash
git clone https://github.com/Ishan1835/Bajaj-Assessment-2026.git
cd your-repo
```

**2. Build**
```bash
mvn clean package -q
```

**3. Run**
```bash
java -jar target/quiz-leaderboard-1.0.jar
```

> ⚠️ The program takes approximately **45–50 seconds** to complete due to the mandatory 5-second delay between each of the 10 polls.

---

## Sample Output

```
=== Quiz Leaderboard Solver ===
Registration Number : RA2311026010130
Total polls         : 10
Delay between polls : 5000ms

[Poll 1/10] Fetching (poll=0)...
  ✓ Processed 2 new event(s), skipped 0 duplicate(s)
[Poll 2/10] Fetching (poll=1)...
  ✓ Processed 1 new event(s), skipped 0 duplicate(s)
...
[Poll 10/10] Fetching (poll=9)...
  ✓ Processed 0 new event(s), skipped 1 duplicate(s)

── Final Leaderboard ──────────────────────
Rank  Participant          Total Score
-------------------------------------------
1     Diana                470
2     Ethan                455
3     Fiona                440
-------------------------------------------
Grand Total Score : 1365

── Submission Response ────────────────────
HTTP Status    : 200
Submitted Total: 1365
✅ Submission accepted!
```

---

## Score Verification

Events received across all 10 polls (duplicates highlighted):

| Round | Participant | Score | Poll(s) Received | Action |
|-------|-------------|-------|-----------------|--------|
| R1 | Diana | 200 | 1, 7 | Counted once |
| R1 | Ethan | 155 | 1 | Counted |
| R1 | Fiona | 180 | 2, 10 | Counted once |
| R2 | Diana | 95 | 3, 4 | Counted once |
| R2 | Ethan | 210 | 3, 9 | Counted once |
| R2 | Fiona | 120 | 6 | Counted |
| R3 | Diana | 175 | 5, 9 | Counted once |
| R3 | Ethan | 90 | 7 | Counted |
| R3 | Fiona | 140 | 5, 8 | Counted once |

| Participant | R1 | R2 | R3 | **Total** |
|-------------|----|----|----|---------:|
| Diana | 200 | 95 | 175 | **470** |
| Ethan | 155 | 210 | 90 | **455** |
| Fiona | 180 | 120 | 140 | **440** |
| **Grand Total** | | | | **1365** |

---

## Dependencies

```xml
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

Uses Java's built-in `java.net.http.HttpClient` (Java 11+) — no external HTTP library needed.

---

## Registration

**Name:** *Ishan Yadav*  
**Reg No:** RA2311026010130  
**Institution:** SRM Institute of Science and Technology