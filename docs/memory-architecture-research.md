# Memory architecture research and OrionekoX plan

Date: 2026-10-28. Scope: improve the 0.52.0 local memory library without
turning it into a cloud-only feature. Sources were read from the projects'
public documentation/papers; formulas are paraphrased, not copied code.

## What the open-source systems actually do

### Mem0 (mem0ai/mem0)

- Two phases: extraction and update. On each new message pair the LLM sees a
  running summary, the previous ~10 messages and returns candidate facts.
- For each candidate it retrieves the top ~10 similar memories and asks the
  model to choose ADD, UPDATE, DELETE or NOOP. UPDATE happens only when the new
  fact has more information; contradictions delete the old fact.
- The graph variant extracts entities and relation triplets, searches existing
  nodes by embedding, and invalidates conflicting edges instead of deleting
  them so temporal questions can still be answered.
- Retrieval is hybrid: semantic, BM25 keyword and entity matching are scored in
  parallel and fused. The paper reports ~7k tokens per conversation and
  0.148 s p50 search; the graph variant roughly doubles memory size and search
  latency.
- There is no decay/forgetting curve; memory accumulates and contradictions are
  the only removal path.

### MemGPT / Letta

- A fixed context window is split into system instructions, writable working
  context and a FIFO message queue whose first entry is a recursive summary.
- External memory is split into recall storage (message DB) and archival
  storage (arbitrary text + vector index). The model explicitly pages items in
  and out through function calls.
- At ~70% of the window it injects a memory-pressure warning; at ~100% it evicts
  a large part of the FIFO queue and generates a new recursive summary from the
  previous summary plus evicted messages. Evicted messages remain retrievable.
- The lesson is not a particular score formula, but the hierarchy: working core,
  rolling summary, searchable archive, and explicit paging.

### Graphiti / Zep

- Temporal knowledge graph with bi-temporal facts: when a fact became true and
  when it was invalidated. Old facts are invalidated, not deleted; source
  episodes remain traceable.
- Entity/relationship extraction is LLM-driven with structured output; an
  ontology can be prescribed or learned.
- Hybrid retrieval combines embeddings, BM25 and graph traversal, then reranks
  by distance. New facts may be available asynchronously.
- No explicit forgetting curve; history is preserved for temporal reasoning.

### Generative Agents (Stanford)

- Memory stream entries have a timestamp, importance (LLM score 1–10) and an
  embedding.
- Retrieval score is a weighted sum of recency, importance and relevance, with
  all three normalized to [0,1]. Recency uses exponential decay 0.995 per game
  hour; relevance is cosine similarity; all weights are 1.0 in the paper.
- This is the clearest reference for mixing signal types rather than trusting
  vector similarity alone.

### FSRS (open-spaced-repetition)

- Later FSRS versions use a power forgetting curve, not exponential decay:
  `R(t,S) = (1 + factor * t / S)^(-decay)`, calibrated so `R=0.9` at `t=S`.
  FSRS v4 uses `R = (1 + t/(9S))^-1`.
- Recall increases stability (spacing effect); difficulty updates and mean
  reversion keep hard items hard. Exact coefficients are trained from review
  logs.
- The useful design idea for us: stability is a per-memory state, retrieval
  success increases it, and retention is a power curve rather than a fixed
  half-life.

### MemoryBank

- Uses the Ebbinghaus retention function `R = e^(-t/S)` with a simple integer
  strength `S` that starts at 1 and increments on recall, resetting elapsed
  time. The paper itself calls this exploratory and highly simplified.
- Lesson: the important part is reinforcement on recall and per-memory strength,
  not the exact exponential constant.

### LangMem

- Splits semantic (facts/collections or profile), episodic and procedural
  memory; formation can happen on the hot path or in the background.
- Recommends relevance combined with importance and memory strength
  (recency/frequency), hierarchical namespaces and metadata. Background
  reflection improves recall without slowing replies.
- This supports an asynchronous summarizer/extractor instead of blocking every
  chat turn.

## Diagnosed weaknesses of 0.52.0

1. The vector mode is a 128-dimension hashed n-gram projection, not a semantic
   embedding. It is stable and offline, but synonym/paraphrase recall is weak.
2. Normal mode uses token overlap/substring rather than BM25; common words and
   repeated small talk get too much weight.
3. Retrieval does not combine BM25, vector, recency, importance and diversity
   properly; top-k can contain near-duplicates.
4. The forgetting curve is a fixed exponential half-life and ignores difficulty
   or successful-recall stability updates. It is not FSRS/MemoryBank-like.
5. Facts are only appended. There is no ADD/UPDATE/DELETE/NOOP consolidation,
   contradiction handling or temporal validity.
6. Summaries are extractive and can be dominated by filler; there is no
   model-generated rolling summary or hierarchical merge.
7. Compression is message-count based only; there is no token-pressure warning
   or token budget.
8. No quantitative benchmark exists, so “better” is currently unmeasured.

## Plan (multiple rounds)

### Round A — retrieval and retention core

- Schema v2: difficulty, stability, valid-from/invalid-at, subject key,
  retrieval counters; keep the existing `vec`.
- Implement BM25 over the agent's memories in Java, normalized per query.
- Hybrid score: BM25, vector (when mode=vector), recency/retention and
  importance, then MMR diversity. Normal mode = BM25 + retention + importance;
  vector mode = BM25 + vector + retention + importance.
- Replace fixed half-life with a power retention curve and recall-driven
  stability: `R=(1+t/(9S))^-1`; successful recall increases `S`; high stability
  promotes to long-term; difficulty decreases on successful recall.
- Fact consolidation: derive a subject key, find near-duplicate facts, keep the
  richer text, and invalidate contradictory facts with numeric/date changes
  rather than deleting history.
- Merge old summaries into a rolling summary when there are too many; keep the
  newest detailed summary.
- Add a deterministic benchmark fixture and document recall@k/MRR/latency.

### Round B — model-level memory formation

- Add an asynchronous distiller that uses the agent's own model (no extra key)
  to extract facts and write a concise abstractive summary after a compression
  batch. It must be cancellable, bounded, and fall back to the current
  extractive path on error.
- Use a strict JSON schema and validate/dedupe facts; store `source=model`.
- Optionally add an embedding-provider abstraction (OpenAI-compatible
  `/embeddings`) with dimension/model metadata, reindex-on-change and local
  hash fallback. Current providers expose no embedding model, so this remains
  opt-in and must be tested with a real endpoint before being claimed.
- Add query expansion from entities and recent turns, plus a lightweight
  reranker.

### Round C — evaluation and UX

- Synthetic long-conversation benchmark: facts, paraphrases, temporal updates,
  contradictions, filler; measure recall@1/5, MRR, fact accuracy, latency and
  token usage for normal/vector/hybrid.
- Memory page: per-entry details (type, stability, retention, last access),
  delete/merge, reindex; agent settings for embedding provider.
- Decide whether Graphiti-style triples are worth the storage/complexity; do
  not add a graph store until the flat hybrid baseline is measured.

## Non-goals for now

- Shipping a large local embedding model in the APK.
- A cloud-only memory service or sending all conversations to a third party.
- Replacing the existing memory.txt contract or the Chatbox backup fields.
